package org.iutools.app

import org.iutools.dictionary.DictionaryLookupResult
import org.iutools.script.Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Plain JUnit (no Robolectric/Android framework needed): splitIntoWords(),
 * shouldOfferGuessMeaning() and the highlight helpers are all pure
 * functions. (guessMeaningSeedPrompt() moved to :core -- its tests are
 * GuessMeaningSeedPromptTest in :cli.)
 */

class WordLookupScreenTest {

    @Test
    fun splitIntoWords_singleWord_returnsOneElement() {
        assertEquals(listOf("atuagaq"), splitIntoWords("atuagaq"))
    }

    @Test
    fun splitIntoWords_multipleWords_splitsOnWhitespace() {
        assertEquals(listOf("qanuq", "ippit"), splitIntoWords("qanuq ippit"))
    }

    @Test
    fun splitIntoWords_extraWhitespace_isIgnored() {
        assertEquals(listOf("qanuq", "ippit"), splitIntoWords("  qanuq   ippit  "))
    }

    @Test
    fun splitIntoWords_blank_returnsEmptyList() {
        assertEquals(emptyList<String>(), splitIntoWords("   "))
    }

    // Regression test for Alain's report: a real word had a shorter-word
    // dictionary hit (which doesn't count as "a definition was found", see
    // ShorterWordDictionaryResult) but the analyzer failed to decompose it --
    // an earlier version of this condition also required a successful
    // decomposition, which silently hid the Guess Meaning button exactly
    // when it was needed most.
    private val sampleDictionaryHit = DictionaryLookupResult(
        title = "Spalding",
        word = "atuagaq",
        meaning = "book",
        enteredScript = Script.ROMAN,
    )

    @Test
    fun shouldOfferGuessMeaning_noDictionaryHitAndNotLoading_true_regardlessOfDecompositionOutcome() {
        assertTrue(shouldOfferGuessMeaning(emptyList(), dictionaryLoading = false, lastSearchedWord = "atuagaq"))
    }

    @Test
    fun shouldOfferGuessMeaning_exactDictionaryHit_false() {
        assertTrue(!shouldOfferGuessMeaning(listOf(sampleDictionaryHit), dictionaryLoading = false, lastSearchedWord = "atuagaq"))
    }

    @Test
    fun shouldOfferGuessMeaning_stillLoading_false() {
        assertTrue(!shouldOfferGuessMeaning(emptyList(), dictionaryLoading = true, lastSearchedWord = "atuagaq"))
    }

    @Test
    fun shouldOfferGuessMeaning_noWordSearchedYet_false() {
        assertTrue(!shouldOfferGuessMeaning(emptyList(), dictionaryLoading = false, lastSearchedWord = ""))
    }

    @Test
    fun highlightRange_wordInMiddleOfSentence_returnsItsRange() {
        assertEquals(6..10, highlightRange("qanuq ippit uvanga", "ippit"))
    }

    @Test
    fun highlightRange_wordAtStartOfSentence_returnsItsRange() {
        assertEquals(0..4, highlightRange("ippit qanuq", "ippit"))
    }

    @Test
    fun highlightRange_wordNotPresent_returnsNull() {
        assertEquals(null, highlightRange("qanuq ippit", "notthere"))
    }

    @Test
    fun highlightRange_caseInsensitive_stillMatches() {
        assertEquals(0..4, highlightRange("IPPIT qanuq", "ippit"))
    }

    @Test
    fun highlightRange_blankWord_returnsNull() {
        assertEquals(null, highlightRange("qanuq ippit", ""))
    }

    @Test
    fun highlightRanges_multipleCandidates_returnsRangeForEachThatOccurs() {
        // "house" at 6..10, "family" at 20..25; "notthere" doesn't occur.
        val ranges = highlightRanges("a new house for the family", listOf("house", "family", "notthere"))

        assertEquals(listOf(6..10, 20..25), ranges)
    }

    @Test
    fun highlightRanges_noneOccur_returnsEmptyList() {
        assertEquals(emptyList<IntRange>(), highlightRanges("a new house", listOf("car", "boat")))
    }

    @Test
    fun highlightRanges_emptyCandidates_returnsEmptyList() {
        assertEquals(emptyList<IntRange>(), highlightRanges("a new house", emptyList()))
    }
}
