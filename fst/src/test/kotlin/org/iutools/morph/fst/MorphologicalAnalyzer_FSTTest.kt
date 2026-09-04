package org.iutools.morph.fst

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Small focused checks on MorphologicalAnalyzer_FST's own behavior. The
 * full gold-standard coverage / parity-with-hfst-lookup sweep lives in
 * :cli's MorphologicalAnalyzer_FST__AccuracyTest.
 *
 * Skips when data/grammar/fst/lexicon-analyser.hfstol hasn't been built.
 */
class MorphologicalAnalyzer_FSTTest {

    private fun analyzer(): MorphologicalAnalyzer_FST {
        assumeTrue(MorphologicalAnalyzer_FST.isAvailable(), "data/grammar/fst/lexicon-analyser.hfstol not built")
        return MorphologicalAnalyzer_FST()
    }

    @Test
    fun decomposesAKnownRomanWord() {
        analyzer().use { fst ->
            val decomps = fst.decomposeWord("iglu")
            assertTrue(decomps.isNotEmpty(), "expected at least one decomposition for 'iglu'")
        }
    }

    // Regression: a word typed in syllabics, or capitalized, used to get no
    // analysis at all (the .lexc lexicon is lowercase Roman) while the R2L
    // analyzer handled both. MorphologicalAnalyzer_FST now applies the same
    // input normalization.
    @Test
    fun syllabicsAndCapitalizationAreNormalizedLikeR2L() {
        analyzer().use { fst ->
            val roman = fst.decomposeWord("iglu").map { it.toString() }
            assertTrue(roman.isNotEmpty())

            val syllabic = fst.decomposeWord("ᐃᒡᓗ").map { it.toString() }
            assertEquals(roman, syllabic, "syllabic 'ᐃᒡᓗ' should analyse the same as Roman 'iglu'")

            val capitalized = fst.decomposeWord("Iglu").map { it.toString() }
            assertEquals(roman, capitalized, "'Iglu' should analyse the same as 'iglu'")
        }
    }
}
