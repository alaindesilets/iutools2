package org.iutools.llm

import kotlin.test.Test
import kotlin.test.assertEquals

class CandidateMeaningsTest {

    @Test
    fun extractCandidateMeanings_simpleList_returnsEachBulletTrimmed() {
        val text = """
            Some reasoning about the word's morphemes.

            Candidate meanings:
            - snowmobile
            - vehicle
            - machine
        """.trimIndent()

        assertEquals(listOf("snowmobile", "vehicle", "machine"), extractCandidateMeanings(text))
    }

    @Test
    fun extractCandidateMeanings_bulletPointCharacter_alsoRecognized() {
        val text = "Candidate meanings:\n• igloo\n• snow house"

        assertEquals(listOf("igloo", "snow house"), extractCandidateMeanings(text))
    }

    @Test
    fun extractCandidateMeanings_blankLineBeforeBullets_stillParses() {
        val text = "Candidate meanings:\n\n- one\n- two"

        assertEquals(listOf("one", "two"), extractCandidateMeanings(text))
    }

    @Test
    fun extractCandidateMeanings_noHeader_returnsEmptyList() {
        assertEquals(emptyList<String>(), extractCandidateMeanings("Just some reasoning, no list yet."))
    }

    @Test
    fun extractCandidateMeanings_headerWithNothingAfter_returnsEmptyList() {
        // Mid-stream: the header just arrived, bullets haven't been generated yet.
        assertEquals(emptyList<String>(), extractCandidateMeanings("Reasoning...\n\nCandidate meanings:"))
    }

    @Test
    fun extractCandidateMeanings_partialLastBullet_includesItAsIs() {
        // Mid-stream: the last bullet is still being generated.
        val text = "Candidate meanings:\n- snowmobile\n- veh"

        assertEquals(listOf("snowmobile", "veh"), extractCandidateMeanings(text))
    }

    @Test
    fun extractCandidateMeanings_headerMentionedEarlierInReasoning_usesTheFinalOccurrence() {
        val text = """
            I'll structure this with a Candidate meanings: section at the end.

            Candidate meanings:
            - real answer
        """.trimIndent()

        assertEquals(listOf("real answer"), extractCandidateMeanings(text))
    }

    @Test
    fun extractCandidateMeanings_textAfterList_stopsAtFirstNonBulletLine() {
        val text = "Candidate meanings:\n- one\n- two\n\nSome trailing note."

        assertEquals(listOf("one", "two"), extractCandidateMeanings(text))
    }

    @Test
    fun extractExplanation_textBeforeHeader_returnedTrimmed() {
        val text = "Some reasoning about the word's morphemes.\n\nCandidate meanings:\n- snowmobile"

        assertEquals("Some reasoning about the word's morphemes.", extractExplanation(text))
    }

    @Test
    fun extractExplanation_noHeader_returnsWholeTextTrimmed() {
        assertEquals("Just some reasoning, no list yet.", extractExplanation("  Just some reasoning, no list yet.  "))
    }

    @Test
    fun extractExplanation_headerMentionedEarlierInReasoning_stopsAtFinalOccurrence() {
        val text = "I'll structure this with a Candidate meanings: section at the end.\n\nCandidate meanings:\n- real answer"

        assertEquals("I'll structure this with a Candidate meanings: section at the end.", extractExplanation(text))
    }

    @Test
    fun extractExplanation_headerWithNothingAfter_returnsTextBeforeIt() {
        assertEquals("Reasoning...", extractExplanation("Reasoning...\n\nCandidate meanings:"))
    }
}
