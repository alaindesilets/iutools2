package org.iutools.app

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/*
 * Regression coverage for the Guess Meaning feature's string resources --
 * these get hand-edited often (format placeholders, XML escaping of
 * quotes/apostrophes), so a typo silently breaking the English or French
 * resource, or the two drifting out of sync, is a real risk. Same
 * Robolectric/locale-switching pattern as UiStringLocalizationTest.kt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuessMeaningStringsTest {

    private fun contextFor(locale: Locale): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    @Test
    fun chatMetrics_formatsSecondsAndTokenCounts_inEnglish() {
        val formatted = contextFor(Locale.ENGLISH).getString(R.string.chat_metrics, 2.5, 120, 45)

        assertTrue(formatted, formatted.contains("2.5"))
        assertTrue(formatted, formatted.contains("120"))
        assertTrue(formatted, formatted.contains("45"))
        assertTrue(formatted, formatted.contains("read"))
        assertTrue(formatted, formatted.contains("written"))
    }

    @Test
    fun chatMetrics_formatsSecondsAndTokenCounts_inFrench() {
        val formatted = contextFor(Locale.FRENCH).getString(R.string.chat_metrics, 2.5, 120, 45)

        assertTrue(formatted, formatted.contains("120"))
        assertTrue(formatted, formatted.contains("45"))
        assertTrue(formatted, formatted.contains("lus"))
        assertTrue(formatted, formatted.contains("écrits"))
    }

    @Test
    fun chatSystemPrompt_differsBetweenLanguages_butKeepsTheCandidateMeaningsMarkerInEnglish() {
        val englishPrompt = contextFor(Locale.ENGLISH).getString(R.string.chat_system_prompt)
        val frenchPrompt = contextFor(Locale.FRENCH).getString(R.string.chat_system_prompt)

        assertNotEquals(englishPrompt, frenchPrompt)
        // The app's chat parsing/UI convention relies on this exact marker
        // appearing verbatim, in English, regardless of the prompt's language
        // (Alain's request -- see GuessMeaningScreen.kt's header comment).
        assertTrue(englishPrompt.contains("\"Candidate meanings:\""))
        assertTrue(frenchPrompt.contains("\"Candidate meanings:\""))
    }

    @Test
    fun guessMeaningSeedUnknownMorpheme_differsBetweenLanguages() {
        val english = contextFor(Locale.ENGLISH).getString(R.string.guess_meaning_seed_unknown_morpheme)
        val french = contextFor(Locale.FRENCH).getString(R.string.guess_meaning_seed_unknown_morpheme)

        assertEquals("(unknown)", english)
        assertEquals("(inconnu)", french)
    }

    @Test
    fun backButton_switchesLanguage_withLocale() {
        val english = contextFor(Locale.ENGLISH).getString(R.string.back_button)
        val french = contextFor(Locale.FRENCH).getString(R.string.back_button)

        assertEquals("Back", english)
        assertEquals("Retour", french)
    }
}
