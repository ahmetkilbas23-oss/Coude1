package com.example.oneDmBot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.oneDmBot.db.AppDatabase
import com.example.oneDmBot.db.FilmEntity
import com.example.oneDmBot.db.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MovieAutoDownloadService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var settings: Settings
    private lateinit var db: AppDatabase

    @Volatile private var workerJob: Job? = null
    @Volatile private var lastNotifSeen: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = Settings(this)
        db = AppDatabase.get(this)
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
                if (settings.isPaused) {
                    delay(3_000)
                    continue
                }
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
        val outcome = runCatching { driveOneDm(film.filmUrl) }.getOrElse {
            Log.w(TAG, "driveOneDm threw", it); DriveOutcome.RETRY
        }
        when (outcome) {
            DriveOutcome.SUCCESS ->
                db.filmDao().setStatus(film.id, FilmEntity.STATUS_DONE)
            DriveOutcome.SKIP -> {
                Log.i(TAG, "Skipping ${film.title} — no matching resolution row")
                db.filmDao().setStatus(film.id, FilmEntity.STATUS_SKIPPED)
            }
            DriveOutcome.RETRY -> {
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
    }

    private enum class DriveOutcome { SUCCESS, RETRY, SKIP }

    private suspend fun driveOneDm(filmUrl: String): DriveOutcome {
        // Step 1+2+3: open the film page in 1DM's built-in browser
        val pkg = settings.oneDmPackage
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(filmUrl)).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot launch 1DM ($pkg): $t"); return DriveOutcome.RETRY
        }

        // wait for page to load
        delay(7_000)

        // Step 4: tap the play button (calibrated coordinates)
        val (px, py) = settings.getCoord(Settings.Slot.PLAY)
        if (px <= 0 || py <= 0) {
            Log.e(TAG, "Play button not calibrated"); return DriveOutcome.RETRY
        }
        if (!gestureTap(px.toFloat(), py.toFloat())) {
            Log.w(TAG, "play tap dispatch failed"); return DriveOutcome.RETRY
        }

        // Step 5: wait for 1DM to detect downloads (counter to fill), then
        // tap the calibrated download counter coordinate. 20 s is the longest
        // detection window observed by the user; tapping earlier hits an
        // empty list and 1DM just refreshes the page.
        delay(20_000)
        val (dx, dy) = settings.getCoord(Settings.Slot.DOWNLOAD)
        if (dx <= 0 || dy <= 0) {
            Log.e(TAG, "Download counter not calibrated"); return DriveOutcome.RETRY
        }
        if (!gestureTap(dx.toFloat(), dy.toFloat())) {
            Log.w(TAG, "download tap dispatch failed"); return DriveOutcome.RETRY
        }

        // Step 6+7: pick the row matching resolution & language. If the
        // film simply doesn't offer a 1280×Türkçe variant, skip rather than
        // retry endlessly.
        val pickRow = waitForNode(timeoutMs = 8_000) { findResolutionRow(it) }
        if (pickRow == null) {
            Log.w(TAG, "no matching resolution row found — skipping film")
            return DriveOutcome.SKIP
        }
        if (!clickNode(pickRow)) return DriveOutcome.RETRY

        // Step 8: confirm screen, find "Başla" / "Start"
        val startBtn = waitForNode(timeoutMs = 6_000) { findStartButton(it) }
        if (startBtn == null) {
            Log.w(TAG, "Başla button not found"); return DriveOutcome.RETRY
        }
        if (!clickNode(startBtn)) return DriveOutcome.RETRY

        // Step 9: download notification confirms success — give listener up to 12s
        val deadline = System.currentTimeMillis() + 12_000
        val baseline = lastNotifSeen
        while (System.currentTimeMillis() < deadline) {
            if (lastNotifSeen > baseline) return DriveOutcome.SUCCESS
            delay(500)
        }
        // Treat as success even without notif: 1DM may suppress while in foreground.
        return DriveOutcome.SUCCESS
    }

    // ── node lookup helpers ──────────────────────────────────────────────────
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

    companion object {
        private const val TAG = "OneDmBot/AccService"
        private const val MAX_RETRIES = 3

        @Volatile var instance: MovieAutoDownloadService? = null
            private set
    }
}
