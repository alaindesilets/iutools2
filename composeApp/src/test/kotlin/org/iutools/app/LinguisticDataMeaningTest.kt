package org.iutools.app

import org.iutools.linguisticdata.LinguisticData
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Same real risk LanguageSwitchUiTest.kt's dropped second test was after --
 * does the bilingual meaning text sourced from the linguistic-data CSVs
 * actually differ per language -- covered directly against the real
 * LinguisticData singleton instead. No Compose UI, no coroutines, so none of
 * that test's Robolectric/dispatcher trouble applies here: this is a plain
 * synchronous JVM call, same as the real app's own MorphemeTable ends up
 * doing via Morpheme.preferredMeaning().
 */
class LinguisticDataMeaningTest {

    @Test
    fun morphemePreferredMeaning_differsBetweenEnglishAndFrench_forARealMorpheme() {
        val morpheme = LinguisticData.getInstance().getMorpheme("atuaq/1v")
        checkNotNull(morpheme) { "expected atuaq/1v to be a real morpheme in the linguistic data" }

        val english = morpheme.preferredMeaning(preferFrench = false)
        val french = morpheme.preferredMeaning(preferFrench = true)

        assertTrue("english meaning should not be blank", !english.isNullOrBlank())
        assertTrue("french meaning should not be blank", !french.isNullOrBlank())
        assertNotEquals(english, french)
    }
}
