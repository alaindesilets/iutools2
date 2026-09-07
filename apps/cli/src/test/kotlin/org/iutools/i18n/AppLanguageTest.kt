package org.iutools.i18n

import org.iutools.llm.MeaningLanguage
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * AppLanguage is the app-wide English/French choice. These tests pin down
 * the two behaviours that aren't just "an enum with two values": the
 * device-locale-based default, and the bridge to the Guess Meaning
 * feature's own MeaningLanguage.
 *
 * defaultAppLanguage() reads Locale.getDefault(), so each test that
 * exercises it sets the default locale explicitly and restoreDefaultLocale()
 * puts it back afterwards.
 */
class AppLanguageTest {

    private val realDefaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreDefaultLocale() {
        Locale.setDefault(realDefaultLocale)
    }

    @Test
    fun eachLanguageCarriesItsOwnLocaleAndEndonym() {
        assertEquals(Locale.ENGLISH, AppLanguage.ENGLISH.locale)
        assertEquals("English", AppLanguage.ENGLISH.endonym)
        assertEquals(Locale.FRENCH, AppLanguage.FRENCH.locale)
        assertEquals("Français", AppLanguage.FRENCH.endonym)
    }

    @Test
    fun defaultIsFrenchWhenTheDeviceIsSetToFrench() {
        Locale.setDefault(Locale.CANADA_FRENCH)

        assertEquals(AppLanguage.FRENCH, defaultAppLanguage())
    }

    @Test
    fun defaultIsEnglishWhenTheDeviceIsSetToEnglish() {
        Locale.setDefault(Locale.US)

        assertEquals(AppLanguage.ENGLISH, defaultAppLanguage())
    }

    @Test
    fun defaultFallsBackToEnglishForAnyOtherDeviceLanguage() {
        Locale.setDefault(Locale.JAPANESE)

        assertEquals(AppLanguage.ENGLISH, defaultAppLanguage())
    }

    @Test
    fun toMeaningLanguageMapsBothValues() {
        assertEquals(MeaningLanguage.ENGLISH, AppLanguage.ENGLISH.toMeaningLanguage())
        assertEquals(MeaningLanguage.FRENCH, AppLanguage.FRENCH.toMeaningLanguage())
    }
}
