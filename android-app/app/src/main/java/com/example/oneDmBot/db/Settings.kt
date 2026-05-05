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

    var playButtonX: Int
        get() = prefs.getInt(KEY_PLAY_X, -1)
        set(value) = prefs.edit { putInt(KEY_PLAY_X, value) }

    var playButtonY: Int
        get() = prefs.getInt(KEY_PLAY_Y, -1)
        set(value) = prefs.edit { putInt(KEY_PLAY_Y, value) }

    val isCalibrated: Boolean
        get() = playButtonX > 0 && playButtonY > 0

    var dailyCheckHour: Int
        get() = prefs.getInt(KEY_DAILY_HOUR, 4)
        set(value) = prefs.edit { putInt(KEY_DAILY_HOUR, value) }

    companion object {
        private const val KEY_RESOLUTION = "preferred_resolution"
        private const val KEY_LANGUAGE = "preferred_language"
        private const val KEY_PACKAGE = "one_dm_package"
        private const val KEY_PLAY_X = "play_x"
        private const val KEY_PLAY_Y = "play_y"
        private const val KEY_DAILY_HOUR = "daily_hour"
    }
}
