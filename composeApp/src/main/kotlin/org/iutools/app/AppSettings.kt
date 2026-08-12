package org.iutools.app

import android.content.Context

/*
 * Persists the settings-panel choices (interface language, display script)
 * across app restarts. Plain SharedPreferences rather than Jetpack
 * DataStore -- two flat string values don't warrant DataStore's coroutine-
 * based API and extra dependency.
 */
object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_LANGUAGE = "ui_language"
    private const val KEY_DISPLAY_SCRIPT = "display_script"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadLanguage(context: Context): AppLanguage {
        val stored = prefs(context).getString(KEY_LANGUAGE, null) ?: return defaultAppLanguage()
        return AppLanguage.entries.firstOrNull { it.name == stored } ?: defaultAppLanguage()
    }

    fun saveLanguage(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    fun loadDisplayScript(context: Context): DisplayScript {
        val stored = prefs(context).getString(KEY_DISPLAY_SCRIPT, null) ?: return DisplayScript.ROMAN
        return DisplayScript.entries.firstOrNull { it.name == stored } ?: DisplayScript.ROMAN
    }

    fun saveDisplayScript(context: Context, script: DisplayScript) {
        prefs(context).edit().putString(KEY_DISPLAY_SCRIPT, script.name).apply()
    }
}
