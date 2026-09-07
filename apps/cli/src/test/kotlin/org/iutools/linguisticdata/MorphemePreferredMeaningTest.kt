package org.iutools.linguisticdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/*
 * Regression coverage for the app UI's i18n: the morpheme meaning shown to
 * the user must follow the requested language, falling back to the other
 * language only when the preferred one is missing/blank.
 */
class MorphemePreferredMeaningTest {

    @Test
    fun preferredMeaning_picksTheRequestedLanguage() {
        // "atuaq/1v" (morpheme of "atuagaq") has known, differing French
        // and English dictionary meanings -- a fixture that catches the
        // language selection being wrong or silently ignored.
        val morpheme = LinguisticData.getInstance().getMorpheme("atuaq/1v")
        val frenchMeaning = requireNotNull(morpheme?.frenchMeaning)
        val englishMeaning = requireNotNull(morpheme?.englishMeaning)
        assertNotEquals(frenchMeaning, englishMeaning, "Test fixture assumes these differ")

        assertEquals(frenchMeaning, morpheme!!.preferredMeaning(preferFrench = true))
        assertEquals(englishMeaning, morpheme.preferredMeaning(preferFrench = false))
    }

    @Test
    fun preferredMeaning_fallsBackToTheOtherLanguageWhenPreferredIsBlank() {
        val morpheme = LinguisticData.getInstance().getMorpheme("atuaq/1v")!!.clone() as Morpheme
        morpheme.frenchMeaning = ""

        assertEquals(morpheme.englishMeaning, morpheme.preferredMeaning(preferFrench = true))
    }
}
