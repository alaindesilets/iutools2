package org.iutools.morph.cli

import org.iutools.llm.MorphemeRow
import org.iutools.dictionary.DictionaryHit
import org.iutools.dictionary.DictionarySource
import org.iutools.dictionary.ShorterWordDictionaryHit
import org.iutools.lookup.DecompositionOutcome
import org.iutools.lookup.FailureReason
import org.iutools.lookup.HansardLookupState
import org.iutools.lookup.WordLookupResult
import org.iutools.script.Script
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 * renderDefinition() is what `segment_iu --define <word>` prints. These
 * tests drive it with fabricated WordLookupResults rather than a real
 * lookup, so they pin the text layout for each shape of result -- a
 * definition found, none found, only a shorter-form hit, and the analyzer
 * failing -- without needing the dictionary data or the analyzer.
 */
class DefineRenderingTest {

    private fun result(
        word: String = "igalaaq",
        decomposition: DecompositionOutcome = DecompositionOutcome.Success(
            word = word,
            lenient = false,
            decompositions = listOf(listOf(MorphemeRow("igalaaq", "igalaaq/1n", null))),
            hasMore = false,
            enteredScript = Script.ROMAN,
        ),
        dictionaryHits: List<DictionaryHit> = emptyList(),
        shorterWordHits: List<ShorterWordDictionaryHit> = emptyList(),
    ) = WordLookupResult(
        word = word,
        enteredScript = Script.ROMAN,
        decomposition = decomposition,
        dictionaryHits = dictionaryHits,
        shorterWordHits = shorterWordHits,
        dictionariesSettled = true,
        tusaalangaFetchError = null,
        hansard = HansardLookupState.NotSearched,
    )

    @Test
    fun definitionFound_isPrintedWithSourceAndMeaning() {
        val rendered = renderDefinition(
            "igalaaq",
            result(dictionaryHits = listOf(DictionaryHit(DictionarySource.SPALDING, "igalaaq", "window", Script.ROMAN))),
        )

        assertContains(rendered, "=== igalaaq ===")
        assertContains(rendered, "[SPALDING] igalaaq -- window")
        assertContains(rendered, "{igalaaq:igalaaq/1n}")
        assertFalse(rendered.contains("No dictionary definition"))
    }

    @Test
    fun noDefinition_saysSo() {
        val rendered = renderDefinition("qrst", result(word = "qrst"))

        assertContains(rendered, "No dictionary definition found.")
    }

    @Test
    fun onlyAShorterFormHit_isLabelledAsSuch() {
        val rendered = renderDefinition(
            "igalaaqmut",
            result(
                word = "igalaaqmut",
                shorterWordHits = listOf(
                    ShorterWordDictionaryHit("igalaaqmut", DictionaryHit(DictionarySource.SPALDING, "igalaaq", "window", Script.ROMAN)),
                ),
            ),
        )

        assertContains(rendered, "For a shorter form")
        assertContains(rendered, "[SPALDING] igalaaq -- window")
    }

    @Test
    fun emptyDecomposition_saysNoneFound() {
        val rendered = renderDefinition(
            "qrst",
            result(
                word = "qrst",
                decomposition = DecompositionOutcome.Success("qrst", false, emptyList(), false, Script.ROMAN),
            ),
        )

        assertContains(rendered, "No decompositions found")
    }

    @Test
    fun analyzerFailure_isDescribed() {
        val timedOut = renderDefinition("x", result(decomposition = DecompositionOutcome.Failure(FailureReason.Timeout)))
        assertContains(timedOut, "timed out")

        val analysisError = renderDefinition(
            "x",
            result(decomposition = DecompositionOutcome.Failure(FailureReason.AnalysisError("bad table"))),
        )
        assertContains(analysisError, "analysis error: bad table")
    }

    @Test
    fun multipleDecompositions_areEachPrinted() {
        val rendered = renderDefinition(
            "igalaaq",
            result(
                decomposition = DecompositionOutcome.Success(
                    "igalaaq", false,
                    listOf(
                        listOf(MorphemeRow("igalaaq", "igalaaq/1n", null)),
                        listOf(MorphemeRow("iga", "igaq/1v", null), MorphemeRow("laaq", "laaq/3vn", null)),
                    ),
                    false, Script.ROMAN,
                ),
            ),
        )

        assertContains(rendered, "{igalaaq:igalaaq/1n}")
        assertContains(rendered, "{iga:igaq/1v}{laaq:laaq/3vn}")
    }
}
