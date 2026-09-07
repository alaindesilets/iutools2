package org.iutools.text

import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * splitIntoWords() / highlightRange() / highlightRanges() -- pure string
 * search. Moved here from :composeApp's WordLookupScreenTest when the
 * helpers moved into :core.
 */
class TextSearchTest {

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
