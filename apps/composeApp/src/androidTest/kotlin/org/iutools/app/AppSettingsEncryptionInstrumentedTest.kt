package org.iutools.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.iutools.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Confirms the API key is actually encrypted at rest, not just that
 * AppSettings.loadApiKey()/saveApiKey() round-trip correctly (already
 * covered by the plain-JUnit AppSettingsTest.kt) -- reading the raw
 * shared_prefs file on disk and asserting neither the key's value nor its
 * preference name appear anywhere in it is a plain string check, not
 * something needing human visual judgment, so this doesn't need the
 * pause-for-a-human-to-confirm pattern AGENTS.md describes for genuinely
 * hard-to-automate cases (see its "Semi-automated tests" section) -- it
 * just needs a *real* Android Keystore, which is the one thing Robolectric
 * can't simulate (see AppSettings.securePrefsFactory's own comment, and
 * why AppSettingsTest.kt substitutes a fake there instead of testing this).
 *
 * Runs on-device/emulator only (see AGENTS.md's "Division of labor" --
 * `./gradlew :composeApp:connectedDebugAndroidTest`, or via Android
 * Studio's test runner); this AI sandbox has no emulator to run it itself.
 */
@RunWith(AndroidJUnit4::class)
class AppSettingsEncryptionInstrumentedTest {

    /*
     * Every AppSettings setter persists with SharedPreferences.edit().apply()
     * (EncryptedSharedPreferences too, under the hood) -- an async disk write,
     * normally drained from QueuedWork at Activity lifecycle points, of which
     * an instrumented test has none. These tests read the raw prefs file
     * straight after a save, so they must force that write to disk first.
     *
     * An empty commit() on the *same* SharedPreferences instance does it:
     * commit() is synchronous, and SharedPreferencesImpl serializes disk
     * writes, so the pending apply() has landed by the time commit() returns.
     * The file name mirrors AppSettings.PREFS_NAME / SECURE_PREFS_NAME (both
     * private there), same as the hard-coded shared_prefs paths below.
     */
    private fun flushPrefsToDisk(context: Context, prefsFileName: String) {
        assertTrue(
            "commit() on $prefsFileName should report success",
            context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE).edit().commit(),
        )
    }

    @Test
    fun apiKey_isNotStoredAsPlaintextOnDisk() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Unique per run so a leftover value from a previous run (or from
        // manually testing the app on this same device) can't produce a
        // false pass.
        val testKey = "sk-ant-test-instrumented-${System.currentTimeMillis()}"

        AppSettings.saveApiKey(context, testKey)
        flushPrefsToDisk(context, "app_settings_secure")

        // context.filesDir is <data-dir>/files -- its parent is the app's
        // data directory, same place getSharedPreferences() always writes
        // to. Read directly as a file (not via SharedPreferences) so this
        // sees exactly what's actually on disk, not a decrypted in-memory
        // view of it.
        val secureFile = File(context.filesDir.parentFile, "shared_prefs/app_settings_secure.xml")
        assertTrue("expected ${secureFile.path} to exist after saveApiKey()", secureFile.exists())
        val rawContent = secureFile.readText()

        assertFalse(
            "the raw encrypted prefs file must never contain the plaintext key",
            rawContent.contains(testKey),
        )
        assertFalse(
            "EncryptedSharedPreferences encrypts preference names too (AES256_SIV) -- " +
                "the literal name should never appear either",
            rawContent.contains("anthropic_api_key"),
        )

        // Sanity check the assertions above aren't just passing because
        // nothing got written at all -- confirm the real (encrypted) API
        // still reads the value back out correctly.
        assertEquals(testKey, AppSettings.loadApiKey(context))
    }

    @Test
    fun nonSecretSettings_remainPlaintext_forContrast() {
        // Not a security assertion -- just confirms this test's own
        // "shouldn't be readable" checks above are meaningful by showing
        // the *other* prefs file (language/display script, never meant to
        // be secret) really is readable in the same raw-file way.
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppSettings.saveLanguage(context, AppLanguage.FRENCH)
        flushPrefsToDisk(context, "app_settings")

        val plainFile = File(context.filesDir.parentFile, "shared_prefs/app_settings.xml")
        assertTrue("expected ${plainFile.path} to exist after saveLanguage()", plainFile.exists())

        assertTrue(
            "the plain-prefs file is expected to be human-readable XML, unlike app_settings_secure.xml",
            plainFile.readText().contains("ui_language"),
        )
    }
}
