package com.example.oneDmBot.template

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Stores user-supplied "what does the download button look like" reference
 * images on internal storage. Two slots are used so the user can teach a
 * second visual (e.g. the resolution row or the start button) later.
 */
class TemplateStore(private val ctx: Context) {

    private fun fileFor(slot: String): File = File(ctx.filesDir, "template_$slot.png")

    fun has(slot: String): Boolean = fileFor(slot).exists()

    fun load(slot: String): Bitmap? {
        val f = fileFor(slot)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)
    }

    fun saveFromUri(slot: String, uri: Uri): Boolean {
        val bm = ctx.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return false
        return saveBitmap(slot, bm)
    }

    fun saveBitmap(slot: String, bitmap: Bitmap): Boolean {
        val out = fileFor(slot)
        return runCatching {
            out.outputStream().use { os ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            }
        }.isSuccess
    }

    fun delete(slot: String) {
        fileFor(slot).delete()
    }

    companion object {
        const val SLOT_DOWNLOAD = "download"
    }
}
