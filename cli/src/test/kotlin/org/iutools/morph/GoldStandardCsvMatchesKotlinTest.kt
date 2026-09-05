package org.iutools.morph

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Verifies data/grammar/gold-standard/gold-standard.csv is a byte-for-byte
 * faithful export of the hand-written gold standard: builds one instance the
 * current way (addCase() calls in MorphAnalGoldStandard_Hansard /
 * _WordsThatFailedBefore) and one from the CSV (GoldStandardCsvReader), and
 * compares every word's AnalyzerCase field by field. The accuracy tests
 * themselves keep using the addCase()-built instances -- this test only
 * guards the CSV export, which nothing else consumes yet.
 */
class GoldStandardCsvMatchesKotlinTest {

    @Test
    fun `Hansard CSV export matches the addCase() gold standard`() {
        assertGoldStandardsMatch(MorphAnalGoldStandard_Hansard(), MorphAnalGoldStandard_Hansard_FromCsv())
    }

    @Test
    fun `WordsThatFailedBefore CSV export matches the addCase() gold standard`() {
        assertGoldStandardsMatch(
            MorphAnalGoldStandard_WordsThatFailedBefore(),
            MorphAnalGoldStandard_WordsThatFailedBefore_FromCsv(),
        )
    }

    private fun assertGoldStandardsMatch(
        fromKotlin: MorphAnalGoldStandardAbstract,
        fromCsv: MorphAnalGoldStandardAbstract,
    ) {
        assertEquals(fromKotlin.allWords(), fromCsv.allWords(), "Set of words differs")
        for (word in fromKotlin.allWords()) {
            val a = fromKotlin.caseData(word)!!
            val b = fromCsv.caseData(word)!!
            assertEquals(a.correctDecomps?.toList(), b.correctDecomps?.toList(), "correctDecomps differs for word '$word'")
            assertEquals(a.misspelled, b.misspelled, "misspelled differs for word '$word'")
            assertEquals(a.possiblyMisspelled, b.possiblyMisspelled, "possiblyMisspelled differs for word '$word'")
            assertEquals(a.borrowed, b.borrowed, "borrowed differs for word '$word'")
            assertEquals(a.decompUnknown, b.decompUnknown, "decompUnknown differs for word '$word'")
            assertEquals(a.properName, b.properName, "properName differs for word '$word'")
            assertEquals(a.caseComment, b.caseComment, "caseComment differs for word '$word'")
        }
    }
}
