package org.iutools.app

import org.iutools.linguisticdata.Morpheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Plain JUnit (no Robolectric/Android framework needed): guessMeaningSeedPrompt()
 * and splitIntoWords() are pure functions once their localized labels are
 * already resolved -- see the visibility note on both in DecomposerScreen.kt.
 */

// Minimal concrete Morpheme: the abstract methods below are irrelevant to
// preferredMeaning() (the only Morpheme behavior guessMeaningSeedPrompt() uses).
private class FakeMorpheme(englishMeaning: String?, frenchMeaning: String?) : Morpheme() {
    init {
        this.englishMeaning = englishMeaning
        this.frenchMeaning = frenchMeaning
    }

    override fun agreeWithTransitivity(trans: String?) = true
    override fun showData() = ""
    override fun setAttrs() {}
    override fun getSignature() = ""
    override fun getOriginalMorpheme() = ""
}

private val labels = GuessMeaningSeedLabels(
    word = "Word:",
    decompositionHeader = "Decomposition(s):",
    decompositionNumberTemplate = "Decomposition %1\$d:",
    unknownMorpheme = "(unknown)",
    question = "What might this word mean?",
)

class DecomposerScreenTest {

    @Test
    fun guessMeaningSeedPrompt_singleDecomposition_hasNoDecompositionNumberHeader() {
        val rows = listOf(
            MorphemeRow("atua", "atuaq/1v", FakeMorpheme("read", "lire")),
            MorphemeRow("gaq", "gaq/1vn", FakeMorpheme("thing that", "chose qui")),
        )

        val prompt = guessMeaningSeedPrompt("atuagaq", listOf(rows), preferFrench = false, labels)

        assertTrue(prompt.contains("Word: atuagaq"))
        assertTrue(prompt.contains("- atua (atuaq/1v): read"))
        assertTrue(prompt.contains("- gaq (gaq/1vn): thing that"))
        assertTrue(prompt.contains("What might this word mean?"))
        assertTrue("single decomposition shouldn't be numbered", !prompt.contains("Decomposition 1:"))
    }

    @Test
    fun guessMeaningSeedPrompt_multipleDecompositions_areNumbered() {
        val decompositions = listOf(
            listOf(MorphemeRow("a", "a/1n", null)),
            listOf(MorphemeRow("b", "b/1n", null)),
        )

        val prompt = guessMeaningSeedPrompt("ab", decompositions, preferFrench = false, labels)

        assertTrue(prompt.contains("Decomposition 1:"))
        assertTrue(prompt.contains("Decomposition 2:"))
    }

    @Test
    fun guessMeaningSeedPrompt_missingMeaning_fallsBackToUnknownLabel() {
        val rows = listOf(MorphemeRow("xyz", "xyz/1n", fullRecord = null))

        val prompt = guessMeaningSeedPrompt("xyz", listOf(rows), preferFrench = false, labels)

        assertTrue(prompt.contains("- xyz (xyz/1n): (unknown)"))
    }

    @Test
    fun guessMeaningSeedPrompt_preferFrench_usesFrenchMeaningWhenAvailable() {
        val rows = listOf(MorphemeRow("atua", "atuaq/1v", FakeMorpheme("read", "lire")))

        val prompt = guessMeaningSeedPrompt("atua", listOf(rows), preferFrench = true, labels)

        assertTrue(prompt.contains("- atua (atuaq/1v): lire"))
    }

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
}
