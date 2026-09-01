package org.iutools.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/*
 * Persists the settings-panel choices (interface language, display script,
 * lenient morphological analysis, the user's own Claude.ai API key) across
 * app restarts.
 *
 * Language/display script/lenient analysis use plain SharedPreferences --
 * Jetpack DataStore would be overkill for a few flat, non-secret values, and
 * there's nothing in them worth protecting.
 *
 * The API key is different: it's the user's own credential, so it's stored
 * in a *separate* EncryptedSharedPreferences file instead (AES256-GCM,
 * wrapped by a key held in the Android Keystore -- not just app-private
 * like the plain file above, but unreadable even to something with root
 * access to the file itself, since the key never leaves the Keystore). Per
 * Alain's request, replacing the plain-SharedPreferences version this
 * started as. AndroidManifest.xml's backup rules also exclude this specific
 * file from Auto Backup/device transfer, belt-and-suspenders alongside the
 * encryption -- a Keystore-backed key doesn't normally survive a backup/
 * restore cycle anyway (it's tied to this install), so a backed-up copy of
 * this file would just be undecryptable ciphertext, but there's no reason
 * to leave it sitting in a cloud backup regardless.
 */
object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_LANGUAGE = "ui_language"
    private const val KEY_DISPLAY_SCRIPT = "display_script"
    private const val KEY_LENIENT_ANALYSIS = "lenient_analysis"

    // Lenient morphological analysis is on unless the user turns it off --
    // it surfaces more decompositions for words the strict analyzer rejects,
    // which is the more useful default for a lookup tool.
    private const val DEFAULT_LENIENT_ANALYSIS = true

    // Matches the file name excluded in res/xml/backup_rules.xml and
    // res/xml/data_extraction_rules.xml -- keep those in sync if this ever
    // changes.
    private const val SECURE_PREFS_NAME = "app_settings_secure"
    private const val KEY_API_KEY = "anthropic_api_key"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Indirection (not a direct call to createEncryptedPrefs below), purely
    // so tests can swap it out -- Robolectric fakes plain SharedPreferences
    // in memory, but there's no software equivalent of the hardware/OS-
    // backed AndroidKeyStore provider EncryptedSharedPreferences relies on,
    // so any Robolectric test that composes Settings (see
    // DisplayScriptSwitchUiTest.kt/LanguageSwitchUiTest.kt) needs a fake
    // here instead, or it hits a real KeyStoreException. Always the real
    // encrypted implementation in production; only test code should ever
    // reassign this (see AppSettingsTest.kt's own header comment for what
    // that does and doesn't verify as a result).
    internal var securePrefsFactory: (Context) -> SharedPreferences = ::createEncryptedPrefs

    // For tests to restore the real behavior after substituting a fake (see
    // securePrefsFactory's own comment) -- can't just reassign
    // `::createEncryptedPrefs` from outside this object, since it's private.
    internal fun resetSecurePrefsFactoryToDefault() {
        securePrefsFactory = ::createEncryptedPrefs
    }

    // Not cached across calls -- Settings is the only caller, so this isn't
    // a hot path, and caching would mean holding onto a Context longer than
    // needed. EncryptedSharedPreferences.create() is idempotent (repeated
    // calls with the same file name reopen the same underlying file), so
    // this is safe to call every time.
    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun securePrefs(context: Context): SharedPreferences = securePrefsFactory(context)

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

    fun loadLenientAnalysis(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LENIENT_ANALYSIS, DEFAULT_LENIENT_ANALYSIS)

    fun saveLenientAnalysis(context: Context, lenient: Boolean) {
        prefs(context).edit().putBoolean(KEY_LENIENT_ANALYSIS, lenient).apply()
    }

    fun loadApiKey(context: Context): String =
        securePrefs(context).getString(KEY_API_KEY, "") ?: ""

    fun saveApiKey(context: Context, apiKey: String) {
        securePrefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
    }
}
