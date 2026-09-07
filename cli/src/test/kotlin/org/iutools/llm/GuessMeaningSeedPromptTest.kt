package org.iutools.llm

import org.iutools.corpus.BilingualExample
import org.iutools.dictionary.DictionaryLookupResult
import org.iutools.dictionary.ShorterWordDictionaryResult
import org.iutools.linguisticdata.Morpheme
import org.iutools.script.Script
import org.iutools.script.TransCoder
import kotlin.test.Test
import kotlin.test.assertTrue

/*
 * guessMeaningSeedPrompt() is a pure function once its localized labels are
 * already resolved, so these tests just feed it labels and data and check
 * the assembled text.
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
    questionTemplate = "Can you suggest possible meanings for the word %1\$s?",
    decompositionSingleIntro = "Here is a possible decomposition:",
    decompositionMultipleIntro = "Here are several possible decompositions:",
    decompositionNumberTemplate = "Decomposition %1\$d:",
    unknownMorpheme = "(unknown)",
    shorterWordDictionaryIntroTemplate = "No definition found, but here's one for a word related to %1\$s:",
    hansardExamplesExactIntroTemplate = "Bilingual examples for the exact word %1\$s:",
    hansardExamplesShorterWordIntroTemplate = "Bilingual examples for a shorter, related word than %1\$s:",
)

class GuessMeaningSeedPromptTest {

    @Test
    fun guessMeaningSeedPrompt_singleDecomposition_hasNoDecompositionNumberHeader() {
        val rows = listOf(
            MorphemeRow("atua", "atuaq/1v", FakeMorpheme("read", "lire")),
            MorphemeRow("gaq", "gaq/1vn", FakeMorpheme("thing that", "chose qui")),
        )

        val prompt = guessMeaningSeedPrompt(
            "atuagaq", listOf(rows), emptyList(), hansardExamplesAreExactMatch = true, emptyList(), preferFrench = false, labels,
        )

        // The question opens the seed now (not the "Word:" line, removed --
        // the word is embedded in the question itself instead), per Alain's
        // request. Morpheme surface forms are always converted to syllabic
        // in the seed sent to the AI, regardless of the Roman fixture text
        // above -- see guessMeaningSeedPrompt's header comment. Computed via
        // TransCoder itself (not hand-transliterated), per AGENTS.md's data
        // integrity rule.
        assertTrue(prompt.contains("Can you suggest possible meanings for the word ${TransCoder.ensureScript(Script.SYLLABIC, "atuagaq")}?"))
        assertTrue(prompt.startsWith("Can you suggest possible meanings"))
        assertTrue(prompt.contains("- ${TransCoder.ensureScript(Script.SYLLABIC, "atua")} (atuaq/1v): read"))
        assertTrue(prompt.contains("- ${TransCoder.ensureScript(Script.SYLLABIC, "gaq")} (gaq/1vn): thing that"))
        assertTrue(!prompt.contains("Decomposition 1:"), "single decomposition shouldn't be numbered")
    }

    @Test
    fun guessMeaningSeedPrompt_romanSurfaceForm_isSentAsSyllabic() {
        // Per Alain: Inuktitut sent to the AI in Roman looked enough like
        // gibberish to a small local model that it fell back to its default
        // language (Chinese) instead of English -- this asserts the raw
        // Roman fixture text never appears verbatim in the seed, not just
        // that the syllabic form happens to also be present.
        val rows = listOf(MorphemeRow("atuagaq", "atuagaq/1n", null))

        val prompt = guessMeaningSeedPrompt(
            "atuagaq", listOf(rows), emptyList(), hansardExamplesAreExactMatch = true, emptyList(), preferFrench = false, labels,
        )

        assertTrue(prompt.contains("- ${TransCoder.ensureScript(Script.SYLLABIC, "atuagaq")} (atuagaq/1n)"))
        assertTrue(!prompt.contains("- atuagaq ("), "Roman surface form shouldn't appear verbatim in the seed")
    }

    @Test
    fun guessMeaningSeedPrompt_multipleDecompositions_areNumbered() {
        val decompositions = listOf(
            listOf(MorphemeRow("a", "a/1n", null)),
            listOf(MorphemeRow("b", "b/1n", null)),
        )

        val prompt = guessMeaningSeedPrompt(
            "ab", decompositions, emptyList(), hansardExamplesAreExactMatch = true, emptyList(), preferFrench = false, labels,
        )

        assertTrue(prompt.contains("Decomposition 1:"))
        assertTrue(prompt.contains("Decomposition 2:"))
    }

    @Test
    fun guessMeaningSeedPrompt_missingMeaning_fallsBackToUnknownLabel() {
        val rows = listOf(MorphemeRow("xyz", "xyz/1n", fullRecord = null))

        val prompt = guessMeaningSeedPrompt(
            "xyz", listOf(rows), emptyList(), hansardExamplesAreExactMatch = true, emptyList(), preferFrench = false, labels,
        )

        assertTrue(prompt.contains("- ${TransCoder.ensureScript(Script.SYLLABIC, "xyz")} (xyz/1n): (unknown)"))
    }

    @Test
    fun guessMeaningSeedPrompt_preferFrench_usesFrenchMeaningWhenAvailable() {
        val rows = listOf(MorphemeRow("atua", "atuaq/1v", FakeMorpheme("read", "lire")))

        val prompt = guessMeaningSeedPrompt(
            "atua", listOf(rows), emptyList(), hansardExamplesAreExactMatch = true, emptyList(), preferFrench = true, labels,
        )

        assertTrue(prompt.contains("- ${TransCoder.ensureScript(Script.SYLLABIC, "atua")} (atuaq/1v): lire"))
    }

    @Test
    fun guessMeaningSeedPrompt_hansardExamplesForExactWord_usesExactIntroWithWord() {
        val rows = listOf(MorphemeRow("iglu", "iglu/1n", FakeMorpheme("house", "maison")))
        val examples = listOf(
            BilingualExample("ᐃᒡᓗ ᓄᑖᖅ", "a new house", "19990401"),
            BilingualExample("ᐊᒻᒪᓗ ᐃᒡᓗ", "and the house", "20050318"),
        )

        val prompt = guessMeaningSeedPrompt(
            "iglu", listOf(rows), examples, hansardExamplesAreExactMatch = true, emptyList(), preferFrench = false, labels,
        )

        val syllabicWord = TransCoder.ensureScript(Script.SYLLABIC, "iglu")
        assertTrue(prompt.contains("Bilingual examples for the exact word $syllabicWord:"))
        assertTrue(!prompt.contains("shorter, related word than"), "shouldn't use the shorter-word intro when the match is exact")
        assertTrue(prompt.contains("- ᐃᒡᓗ ᓄᑖᖅ — a new house"))
        assertTrue(prompt.contains("- ᐊᒻᒪᓗ ᐃᒡᓗ — and the house"))
    }

    @Test
    fun guessMeaningSeedPrompt_hansardExamplesForShorterWord_usesShorterWordIntroInstead() {
        // Per Alain: found only for a related but shorter word, not the exact
        // one being analyzed -- the prompt must say the AI does NOT need to
        // verify its guesses against these sentences, not the opposite.
        val rows = listOf(MorphemeRow("iglu", "iglu/1n", FakeMorpheme("house", "maison")))
        val examples = listOf(BilingualExample("ᐃᒡᓗ ᓄᑖᖅ", "a new house", "19990401"))

        val prompt = guessMeaningSeedPrompt(
            "iglumut", listOf(rows), examples, hansardExamplesAreExactMatch = false, emptyList(), preferFrench = false, labels,
        )

        val syllabicWord = TransCoder.ensureScript(Script.SYLLABIC, "iglumut")
        assertTrue(prompt.contains("Bilingual examples for a shorter, related word than $syllabicWord:"))
        assertTrue(!prompt.contains("exact word"), "shouldn't use the exact-word intro when the match is only for a shorter word")
        assertTrue(prompt.contains("- ᐃᒡᓗ ᓄᑖᖅ — a new house"))
    }

    @Test
    fun guessMeaningSeedPrompt_noHansardExamples_omitsBothHansardIntros() {
        val rows = listOf(MorphemeRow("iglu", "iglu/1n", FakeMorpheme("house", "maison")))

        val prompt = guessMeaningSeedPrompt(
            "iglu", listOf(rows), emptyList(), hansardExamplesAreExactMatch = true, emptyList(), preferFrench = false, labels,
        )

        assertTrue(!prompt.contains("Bilingual examples"))
    }

    @Test
    fun guessMeaningSeedPrompt_withShorterWordDictionaryResults_includesThemAsSyllabic() {
        // Per Alain: a shorter/related-word dictionary hit does NOT suppress
        // Guess Meaning (only an exact-word hit does -- see
        // ShorterWordDictionaryResult's own comment), so it's still sent to
        // the AI here -- converted to syllabic, same reasoning as
        // decomposition morphemes, since the headword ("iglu" below) can be
        // stored in Roman (e.g. Spalding).
        val rows = listOf(MorphemeRow("iglumut", "iglu/1n", FakeMorpheme("house", "maison")))
        val shorterWordResults = listOf(
            ShorterWordDictionaryResult(
                originalWord = "iglumut",
                result = DictionaryLookupResult("Found in the Spalding dictionary", "iglu", "house", Script.ROMAN),
            ),
        )

        val prompt = guessMeaningSeedPrompt(
            "iglumut", listOf(rows), emptyList(), hansardExamplesAreExactMatch = true, shorterWordResults, preferFrench = false, labels,
        )

        val syllabicSearchWord = TransCoder.ensureScript(Script.SYLLABIC, "iglumut")
        assertTrue(prompt.contains("No definition found, but here's one for a word related to $syllabicSearchWord:"))
        assertTrue(prompt.contains("- Found in the Spalding dictionary, \"${TransCoder.ensureScript(Script.SYLLABIC, "iglu")}\": house"))
        assertTrue(!prompt.contains("\"iglu\""), "Roman headword shouldn't appear verbatim in the seed")
    }

    @Test
    fun guessMeaningSeedPrompt_noShorterWordDictionaryResults_omitsThatIntro() {
        val rows = listOf(MorphemeRow("iglu", "iglu/1n", FakeMorpheme("house", "maison")))

        val prompt = guessMeaningSeedPrompt(
            "iglu", listOf(rows), emptyList(), hansardExamplesAreExactMatch = true, emptyList(), preferFrench = false, labels,
        )

        assertTrue(!prompt.contains("No definition found"))
    }
}
