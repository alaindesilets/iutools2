package org.iutools.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * Regression coverage for AppSettings' API key persistence (see
 * GuessMeaningInline.kt's onNeedApiKey and SettingsDialog's API key field,
 * in WordLookupScreen.kt) -- covers the load/save *contract* only (default
 * empty, round-trips, overwrites). It deliberately substitutes a plain
 * SharedPreferences for AppSettings.securePrefsFactory (see that field's own
 * comment): Robolectric has no software equivalent of the hardware/OS-backed
 * AndroidKeyStore provider the real EncryptedSharedPreferences path needs,
 * so it can't verify the encryption itself -- that needs a real device
 * (androidTest, not set up yet in this project, see AGENTS.md's "Division of
 * labor" section).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppSettingsTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Before
    fun useFakeSecurePrefs() {
        AppSettings.securePrefsFactory = { context -> context.getSharedPreferences("test_secure_prefs", Context.MODE_PRIVATE) }
    }

    @After
    fun restoreRealSecurePrefsFactory() {
        AppSettings.resetSecurePrefsFactoryToDefault()
    }

    @Test
    fun loadApiKey_neverSaved_returnsEmptyString() {
        assertEquals("", AppSettings.loadApiKey(context()))
    }

    @Test
    fun saveApiKey_thenLoad_returnsTheSavedValue() {
        val ctx = context()

        AppSettings.saveApiKey(ctx, "sk-ant-test-key")

        assertEquals("sk-ant-test-key", AppSettings.loadApiKey(ctx))
    }

    @Test
    fun saveApiKey_overwritesPreviousValue() {
        val ctx = context()
        AppSettings.saveApiKey(ctx, "first-key")

        AppSettings.saveApiKey(ctx, "second-key")

        assertEquals("second-key", AppSettings.loadApiKey(ctx))
    }
}
