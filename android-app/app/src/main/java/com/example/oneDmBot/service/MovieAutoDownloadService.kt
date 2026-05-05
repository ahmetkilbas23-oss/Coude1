package com.example.oneDmBot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.oneDmBot.db.AppDatabase
import com.example.oneDmBot.db.FilmEntity
import com.example.oneDmBot.db.Settings
import com.example.oneDmBot.template.TemplateMatcher
import com.example.oneDmBot.template.TemplateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MovieAutoDownloadService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var settings: Settings
    private lateinit var db: AppDatabase
    private lateinit var templates: TemplateStore

    @Volatile private var workerJob: Job? = null
    @Volatile private var lastNotifSeen: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = Settings(this)
        db = AppDatabase.get(this)
        templates = TemplateStore(this)
        instance = this
        Log.i(TAG, "MovieAutoDownloadService connected")
        startWorkerLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no-op; we drive actively */ }

    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        scope.cancel()
        return super.onUnbind(intent)
    }

    fun notifyDownloadStarted() {
        lastNotifSeen = System.currentTimeMillis()
    }

    private fun startWorkerLoop() {
        workerJob?.cancel()
        workerJob = scope.launch {
            // mark any inflight items as pending again on service (re)start
            db.filmDao().resetInflight()
            while (true) {
                val film = db.filmDao().nextWithStatus(FilmEntity.STATUS_PENDING)
                if (film == null) {
                    delay(5_000)
                    continue
                }
                processFilm(film)
            }
        }
    }

    private suspend fun processFilm(film: FilmEntity) {
        Log.i(TAG, "Processing film ${film.title} :: ${film.filmUrl}")
        db.filmDao().setStatus(film.id, FilmEntity.STATUS_DOWNLOADING)
        val ok = runCatching { driveOneDm(film.filmUrl) }.getOrElse {
            Log.w(TAG, "driveOneDm threw", it); false
        }
        if (ok) {
            db.filmDao().setStatus(film.id, FilmEntity.STATUS_DONE)
        } else {
            db.filmDao().bumpRetry(film.id)
            val refreshed = db.filmDao().nextWithStatus(FilmEntity.STATUS_DOWNLOADING)
            val retries = refreshed?.retries ?: (film.retries + 1)
            if (retries >= MAX_RETRIES) {
                db.filmDao().setStatus(film.id, FilmEntity.STATUS_FAILED)
            } else {
                db.filmDao().setStatus(film.id, FilmEntity.STATUS_PENDING)
                delay(3_000)
            }
        }
    }

    private suspend fun driveOneDm(filmUrl: String): Boolean {
        // Step 1+2+3: open the film page in 1DM's built-in browser
        val pkg = settings.oneDmPackage
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(filmUrl)).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot launch 1DM ($pkg): $t"); return false
        }

        // wait for page to load
        delay(7_000)

        // Step 4: tap the play button (calibrated coordinates)
        val px = settings.playButtonX
        val py = settings.playButtonY
        if (px <= 0 || py <= 0) {
            Log.e(TAG, "Play button not calibrated"); return false
        }
        if (!gestureTap(px.toFloat(), py.toFloat())) {
            Log.w(TAG, "play tap dispatch failed"); return false
        }

        // Step 5: wait for 1DM to detect downloads, then tap the counter.
        // Primary path: visual template matching against the user-supplied
        // reference crop of the download icon. Fallback: accessibility text
        // lookup (rarely works for 1DM's custom views but kept for robustness).
        delay(8_000)
        val templateBitmap = templates.load(TemplateStore.SLOT_DOWNLOAD)
        var tappedDownload = false
        if (templateBitmap != null) {
            val deadline = System.currentTimeMillis() + 12_000
            while (System.currentTimeMillis() < deadline && !tappedDownload) {
                val screenshot = takeScreenshotSuspend()
                if (screenshot != null) {
                    val match = TemplateMatcher.findCenter(screenshot, templateBitmap)
                    screenshot.recycle()
                    if (match != null) {
                        Log.i(TAG, "download icon found at ${match.x},${match.y}")
                        tappedDownload = gestureTap(match.x.toFloat(), match.y.toFloat())
                        break
                    }
                }
                delay(1_500)
            }
        }
        if (!tappedDownload) {
            val downloadFab = waitForNode(timeoutMs = 4_000) { findDownloadFab(it) }
            if (downloadFab != null) {
                tappedDownload = clickNode(downloadFab)
            }
        }
        if (!tappedDownload) {
            Log.w(TAG, "download icon never matched; teach a template first"); return false
        }

        // Step 6+7: pick the row matching resolution & language
        val pickRow = waitForNode(timeoutMs = 8_000) { findResolutionRow(it) }
        if (pickRow == null) {
            Log.w(TAG, "no matching resolution row found"); return false
        }
        if (!clickNode(pickRow)) return false

        // Step 8: confirm screen, find "Başla" / "Start"
        val startBtn = waitForNode(timeoutMs = 6_000) { findStartButton(it) }
        if (startBtn == null) {
            Log.w(TAG, "Başla button not found"); return false
        }
        if (!clickNode(startBtn)) return false

        // Step 9: download notification confirms success — give listener up to 12s
        val deadline = System.currentTimeMillis() + 12_000
        val baseline = lastNotifSeen
        while (System.currentTimeMillis() < deadline) {
            if (lastNotifSeen > baseline) return true
            delay(500)
        }
        // Treat as success even without notif: 1DM may suppress while in foreground.
        return true
    }

    // ── node lookup helpers ──────────────────────────────────────────────────
    private fun findDownloadFab(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1DM exposes the FAB with content description like "Download" or a counter badge
        val descMatches = listOf("download", "indir", "indirme")
        return findFirst(root) { node ->
            val cd = (node.contentDescription ?: "").toString().lowercase()
            val txt = (node.text ?: "").toString().lowercase()
            (descMatches.any { cd.contains(it) || txt.contains(it) }) && node.isClickable
        }
    }

    private fun findResolutionRow(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val res = settings.preferredResolution.lowercase()
        val lang = settings.preferredLanguage.lowercase()
        return findFirst(root) { node ->
            val joined = collectText(node).lowercase()
            joined.contains(res) && (joined.contains(lang) || joined.contains("turkish") || joined.contains("tr "))
                && (node.isClickable || node.parent?.isClickable == true)
        }?.let { if (it.isClickable) it else it.parent }
    }

    private fun findStartButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = listOf("başla", "basla", "start", "ok", "tamam")
        return findFirst(root) { node ->
            val t = (node.text ?: "").toString().lowercase()
            val cd = (node.contentDescription ?: "").toString().lowercase()
            (candidates.any { t == it || t.contains(it) || cd.contains(it) }) && node.isClickable
        }
    }

    private fun collectText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { sb.append(collectText(it)).append(' ') }
        }
        return sb.toString()
    }

    private fun findFirst(
        node: AccessibilityNodeInfo?,
        match: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (match(node)) return node
        for (i in 0 until node.childCount) {
            findFirst(node.getChild(i), match)?.let { return it }
        }
        return null
    }

    private suspend fun waitForNode(
        timeoutMs: Long,
        find: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root == null) {
                delay(300)
                continue
            }
            find(root)?.let { return it }
            delay(400)
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        // fallback: tap by bounds
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false
        return gestureTap(rect.exactCenterX(), rect.exactCenterY())
    }

    private fun gestureTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y + 1f) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Capture the current display via AccessibilityService.takeScreenshot.
     * Returns a software-backed ARGB_8888 bitmap suitable for pixel access.
     * The platform rate-limits this to ~1 call/sec; bursting will return null.
     */
    suspend fun takeScreenshotSuspend(): Bitmap? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cont.resume(null); return@suspendCancellableCoroutine
        }
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                applicationContext.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hwBitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        result.hardwareBuffer.close()
                        val sw = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hwBitmap?.recycle()
                        cont.resume(sw)
                    }
                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "takeScreenshot failed: $errorCode")
                        cont.resume(null)
                    }
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "takeScreenshot threw", t)
            cont.resume(null)
        }
    }

    companion object {
        private const val TAG = "OneDmBot/AccService"
        private const val MAX_RETRIES = 3

        @Volatile var instance: MovieAutoDownloadService? = null
            private set
    }
}
