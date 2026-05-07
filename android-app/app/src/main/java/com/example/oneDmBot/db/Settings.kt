package com.example.oneDmBot.db

import android.content.Context
import androidx.core.content.edit

class Settings(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("onedmbot", Context.MODE_PRIVATE)

    var preferredResolution: String
        get() = prefs.getString(KEY_RESOLUTION, "1280") ?: "1280"
        set(value) = prefs.edit { putString(KEY_RESOLUTION, value) }

    var preferredLanguage: String
        get() = prefs.getString(KEY_LANGUAGE, "Türkçe") ?: "Türkçe"
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value) }

    var oneDmPackage: String
        get() = prefs.getString(KEY_PACKAGE, "idm.internet.download.manager") ?: "idm.internet.download.manager"
        set(value) = prefs.edit { putString(KEY_PACKAGE, value) }

    var dailyCheckHour: Int
        get() = prefs.getInt(KEY_DAILY_HOUR, 4)
        set(value) = prefs.edit { putInt(KEY_DAILY_HOUR, value) }

    var isPaused: Boolean
        get() = prefs.getBoolean(KEY_PAUSED, false)
        set(value) = prefs.edit { putBoolean(KEY_PAUSED, value) }

    fun getCoord(slot: Slot): Pair<Int, Int> =
        prefs.getInt(slot.keyX, -1) to prefs.getInt(slot.keyY, -1)

    fun setCoord(slot: Slot, x: Int, y: Int) {
        prefs.edit {
            putInt(slot.keyX, x)
            putInt(slot.keyY, y)
        }
    }

    fun clearCoord(slot: Slot) {
        prefs.edit {
            remove(slot.keyX)
            remove(slot.keyY)
        }
    }

    fun isCalibrated(slot: Slot): Boolean {
        val (x, y) = getCoord(slot)
        return x > 0 && y > 0
    }

    enum class Slot(val keyX: String, val keyY: String, val title: String, val hint: String) {
        PLAY(
            "play_x", "play_y",
            "Play (▶) butonu",
            "1DM tarayıcısında film sayfası açıkken ortadaki ▶ play butonunun olduğu yere dokunun."
        ),
        DOWNLOAD(
            "dl_x", "dl_y",
            "İndirme sayacı",
            "1DM'in sağ üstünde rakamlı (örn. 19) indirme sayacının olduğu yere dokunun."
        )
    }

    companion object {
        private const val KEY_RESOLUTION = "preferred_resolution"
        private const val KEY_LANGUAGE = "preferred_language"
        private const val KEY_PACKAGE = "one_dm_package"
        private const val KEY_DAILY_HOUR = "daily_hour"
        private const val KEY_PAUSED = "is_paused"
    }
}
