package org.iutools.app

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/*
 * Regression coverage for the app UI's i18n: this exercises Android's own
 * resource-qualifier resolution (values/ vs values-fr/), which the plain
 * JVM test for Morpheme.preferredMeaning() (in :cli) can't reach -- a typo
 * in the values-fr/ directory name would silently fall back to English
 * everywhere, and only resolving a real string under each Locale catches
 * that.
 *
 * sdk pinned to 35: Robolectric 4.14.1 doesn't yet support simulating the
 * app's targetSdk (37, itself a not-yet-stable preview API level per
 * composeApp/build.gradle.kts) -- this only affects which Android version
 * Robolectric simulates for the test, unrelated to the app's real minSdk/
 * targetSdk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UiStringLocalizationTest {

    private fun stringFor(locale: Locale): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config).getString(R.string.find_word_button)
    }

    @Test
    fun findWordButtonLabel_switchesLanguage_withLocale() {
        val englishLabel = stringFor(Locale.ENGLISH)
        val frenchLabel = stringFor(Locale.FRENCH)

        assertEquals("Find Word", englishLabel)
        assertEquals("Trouver le mot", frenchLabel)
        assertNotEquals(englishLabel, frenchLabel)
    }
}
