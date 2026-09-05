package org.iutools.morph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the all_correct_decomps column of
 * data/grammar/gold-standard/gold-standard.csv (every decomposition R2L
 * produces for a fair word, `;`-separated -- see add_all_correct_decomps.py)
 * parses correctly:
 * - non-empty for every fair word (both the "hansard" and
 *   "words_that_failed_before" sources), empty for flagged ones
 * - each decomp in the list, once its surface forms are concatenated,
 *   reconstructs the word it belongs to
 * - the Hansard-attested decomp is itself in the list, except for the two
 *   known gaps where R2L can't currently find it at all (same two words
 *   MorphAnalCurrentExpectations_Hansard.kt already tracks as
 *   CORRECT_NOT_PRESENT).
 */
class GoldStandardCsvAllCorrectDecompsTest {

    private val knownGoldNotInR2lOutput = setOf("imaimmat", "taaksumunga")

    @Test
    fun `fair Hansard words all have a non-empty all_correct_decomps list`() {
        val cases = GoldStandardCsvReader.casesFor("hansard").associateBy { it.word }
        val allCorrectDecomps = GoldStandardCsvReader.allCorrectDecomps("hansard")

        val fairWords = cases.values.filter { case -> !isFlagged(case) }.map { it.word }
        assertTrue(fairWords.isNotEmpty())

        for (word in fairWords) {
            val decomps = allCorrectDecomps.getValue(word)
            assertTrue(decomps.isNotEmpty(), "Expected a non-empty all_correct_decomps list for fair word '$word'")
        }
    }

    @Test
    fun `flagged words have an empty all_correct_decomps list`() {
        val cases = GoldStandardCsvReader.casesFor("hansard").associateBy { it.word }
        val allCorrectDecomps = GoldStandardCsvReader.allCorrectDecomps("hansard")

        val flaggedWords = cases.values.filter { case -> isFlagged(case) }.map { it.word }
        assertTrue(flaggedWords.isNotEmpty())
        for (word in flaggedWords) {
            assertEquals(emptyList(), allCorrectDecomps.getValue(word), "Expected no all_correct_decomps for flagged word '$word'")
        }
    }

    @Test
    fun `fair words_that_failed_before words also have a non-empty all_correct_decomps list`() {
        val wordsThatFailedBefore = GoldStandardCsvReader.allCorrectDecomps("words_that_failed_before")
        assertTrue(wordsThatFailedBefore.isNotEmpty())
        for ((word, decomps) in wordsThatFailedBefore) {
            assertTrue(decomps.isNotEmpty(), "Expected a non-empty all_correct_decomps list for '$word' (words_that_failed_before source)")
        }
    }

    @Test
    fun `every decomp's concatenated surface forms reconstruct the word`() {
        val allCorrectDecomps = GoldStandardCsvReader.allCorrectDecomps("hansard")
        var checked = 0
        for ((word, decomps) in allCorrectDecomps) {
            for (decomp in decomps) {
                assertEquals(
                    word,
                    GoldStandardCsvReader.concatenatedSurfaceForms(decomp),
                    "Concatenated surface forms of '$decomp' should reconstruct '$word'",
                )
                checked++
            }
        }
        assertTrue(checked > 0, "Expected to have actually checked at least one decomp")
    }

    @Test
    fun `the Hansard-attested decomp is among R2L's own decomps, except two known gaps`() {
        val cases = GoldStandardCsvReader.casesFor("hansard").associateBy { it.word }
        val allCorrectDecomps = GoldStandardCsvReader.allCorrectDecomps("hansard")

        val fairWords = cases.values.filter { case -> !isFlagged(case) }
        var checked = 0
        for (case in fairWords) {
            if (case.word in knownGoldNotInR2lOutput) continue
            val goldDecomps = case.correctDecomps ?: continue
            val decomps = allCorrectDecomps.getValue(case.word)
            assertTrue(
                goldDecomps.any { it in decomps },
                "Expected at least one of the Hansard-attested decomps for '${case.word}' to be in R2L's own output",
            )
            checked++
        }
        assertTrue(checked > 0)

        // The two known gaps really are gaps -- not present -- so a future
        // fix that makes R2L find them should update this test, not silently
        // pass either way.
        for (word in knownGoldNotInR2lOutput) {
            val case = cases.getValue(word)
            val goldDecomps = case.correctDecomps ?: continue
            val decomps = allCorrectDecomps.getValue(word)
            assertTrue(
                goldDecomps.none { it in decomps },
                "'$word' was expected to still be a known gap (gold decomp not found by R2L) -- " +
                    "if this now fails, R2L started finding it: remove it from knownGoldNotInR2lOutput",
            )
        }
    }

    private fun isFlagged(case: AnalyzerCase): Boolean =
        case.misspelled || case.possiblyMisspelled || case.borrowed || case.properName || case.decompUnknown
}
