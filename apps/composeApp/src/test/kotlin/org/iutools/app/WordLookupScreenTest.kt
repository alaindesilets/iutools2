package org.iutools.app

import org.iutools.dictionary.DictionaryHit
import org.iutools.dictionary.DictionarySource
import org.iutools.script.Script
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * shouldOfferGuessMeaning() -- pure gating logic, no Robolectric needed.
 * (splitIntoWords / highlightRange(s) moved to :core -- their tests are
 * TextSearchTest in :cli; guessMeaningSeedPrompt likewise ->
 * GuessMeaningSeedPromptTest.)
 */
class WordLookupScreenTest {

    // Regression test for Alain's report: a real word had a shorter-word
    // dictionary hit (which doesn't count as "a definition was found", see
    // ShorterWordDictionaryHit) but the analyzer failed to decompose it --
    // an earlier version of this condition also required a successful
    // decomposition, which silently hid the Guess Meaning button exactly
    // when it was needed most.
    private val sampleDictionaryHit = DictionaryHit(
        source = DictionarySource.SPALDING,
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
}
