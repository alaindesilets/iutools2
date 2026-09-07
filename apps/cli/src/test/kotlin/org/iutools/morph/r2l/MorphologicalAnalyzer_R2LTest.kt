package org.iutools.morph.r2l

import org.iutools.morph.MorphologicalAnalyzer
import org.iutools.morph.MorphologicalAnalyzerTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MorphologicalAnalyzer_R2LTest : MorphologicalAnalyzerTest() {
    override fun makeAnalyzer(): MorphologicalAnalyzer {
        return MorphologicalAnalyzer_R2L()
    }

    /*
     * Decomposition.lenient is set for readings that R2L only finds by
     * assuming the (vowel-final) word had a dropped final consonant
     * (assumedMissingFinalConsonant / the extendedAnalysis path).
     *
     * "ammalu" is a stable case: strict analysis gives {ammalu:ammalu/1c}
     * and {amma:amma/1c}{lu:lu/1q}; the extended analysis additionally finds
     * {amma:angmaq/1v}{lu:luk/tv-imp-1d} by treating the word as "ammaluk".
     */
    @Test
    fun decomposeWord_lenientFlag_marksGuessedFinalConsonantReadings() {
        analyzer.deactivateTimeout()

        val extended = analyzer.decomposeWord("ammalu", true)
        val guessed = extended.single { it.toString() == "{amma:angmaq/1v}{lu:luk/tv-imp-1d}" }
        assertTrue(guessed.lenient, "the reconstructed 'ammaluk' reading should be lenient")
        for (dec in extended.filter { it.toString() != "{amma:angmaq/1v}{lu:luk/tv-imp-1d}" }) {
            assertFalse(dec.lenient, "strict reading wrongly flagged lenient: $dec")
        }
    }

    @Test
    fun decomposeWord_lenientFlag_allFalseWhenExtendedAnalysisOff() {
        analyzer.deactivateTimeout()

        val strict = analyzer.decomposeWord("ammalu", false)
        assertTrue(strict.isNotEmpty())
        for (dec in strict) {
            assertFalse(dec.lenient, "strict-only run produced a lenient-flagged decomp: $dec")
        }
        assertFalse(
            strict.any { it.toString() == "{amma:angmaq/1v}{lu:luk/tv-imp-1d}" },
            "the reconstructed reading should not appear with extendedAnalysis off",
        )
    }
}
