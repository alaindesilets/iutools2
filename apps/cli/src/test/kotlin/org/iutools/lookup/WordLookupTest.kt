package org.iutools.lookup

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.iutools.corpus.HansardExampleSource
import org.iutools.corpus.HansardExamplesOutcome
import org.iutools.dictionary.DictionarySource
import org.iutools.dictionary.TusaalangaEntry
import org.iutools.dictionary.TusaalangaResult
import org.iutools.morph.AnalyzerChoice
import org.iutools.morph.Decomposition
import org.iutools.morph.MorphologicalAnalyzer
import org.iutools.morph.r2l.MorphologicalAnalyzer_R2L
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * WordLookup is the ported word-lookup orchestration. These tests pin down
 * the gating rules that used to be reachable only through the Compose
 * screen: what turns "guess the meaning" on, when the Hansard search runs
 * by itself, that a shorter-word hit doesn't count as a definition, and
 * that each analyzer-failure kind is reported rather than thrown.
 *
 * Spalding is called for real (it is embedded, deterministic data -- same
 * as SpaldingDictionaryTest uses it). The Hansard source and the Tusaalanga
 * lookup are fakes, so no test here needs a database or the network. The
 * analyzer is real R2L for the success cases and a throwing stub for the
 * two failure cases.
 */
class WordLookupTest {

    // "igalaaq" (window) is a real Spalding headword; see SpaldingDictionaryTest.
    private val wordInSpalding = "igalaaq"

    // Not a Spalding headword, and short/nonsensical enough that no prefix
    // of it is one either.
    private val wordInNoDictionary = "zzzznotarealword"

    private class RecordingHansardSource(
        private val outcome: HansardExamplesOutcome = HansardExamplesOutcome.NotFound,
    ) : HansardExampleSource {
        var searchedFor: String? = null
        override suspend fun examplesFor(word: String): HansardExamplesOutcome {
            searchedFor = word
            return outcome
        }
    }

    private class ThrowingAnalyzer(private val fail: () -> Nothing) : MorphologicalAnalyzer() {
        override fun doDecompose(word: String, lenient: Boolean?): Array<Decomposition> = fail()
    }

    private fun runLookup(
        word: String,
        analyzerChoice: AnalyzerChoice = AnalyzerChoice.UQAILAUT,
        analyzerFor: (AnalyzerChoice) -> MorphologicalAnalyzer? = { MorphologicalAnalyzer_R2L() },
        tusaalanga: suspend (String) -> TusaalangaResult = { TusaalangaResult.NotFound },
        hansard: RecordingHansardSource = RecordingHansardSource(),
    ): Pair<List<WordLookupResult>, RecordingHansardSource> {
        val lookup = WordLookup(hansard, analyzerFor, tusaalanga)
        val emissions = runBlocking { lookup.lookup(word, WordLookupPrefs(analyzerChoice, lenient = true)).toList() }
        return emissions to hansard
    }

    private fun finalResult(
        word: String,
        analyzerChoice: AnalyzerChoice = AnalyzerChoice.UQAILAUT,
        analyzerFor: (AnalyzerChoice) -> MorphologicalAnalyzer? = { MorphologicalAnalyzer_R2L() },
        tusaalanga: suspend (String) -> TusaalangaResult = { TusaalangaResult.NotFound },
        hansard: RecordingHansardSource = RecordingHansardSource(),
    ): WordLookupResult = runLookup(word, analyzerChoice, analyzerFor, tusaalanga, hansard).first.last()

    @Test
    fun submittedWords_splitsMultiWordInputForDisambiguation() {
        val lookup = WordLookup(RecordingHansardSource(), { MorphologicalAnalyzer_R2L() })

        assertEquals(listOf("iglu", "nuna"), lookup.submittedWords("  iglu nuna  "))
        assertEquals(listOf("iglu"), lookup.submittedWords("iglu"))
    }

    @Test
    fun exactDictionaryHit_hasDefinition_andGuessMeaningNotOffered() {
        val result = finalResult(wordInSpalding)

        assertTrue(result.hasDefinition)
        assertTrue(result.dictionaryHits.any { it.source == DictionarySource.SPALDING })
        assertTrue(!result.shouldOfferGuessMeaning)
    }

    @Test
    fun exactDictionaryHit_doesNotAutoSearchHansard() {
        val (emissions, hansard) = runLookup(wordInSpalding)

        assertNull(hansard.searchedFor, "Hansard should not be searched when a definition was found")
        assertEquals(HansardLookupState.NotSearched, emissions.last().hansard)
        assertTrue(emissions.none { it.hansard is HansardLookupState.Searching })
    }

    @Test
    fun noDictionaryHit_offersGuessMeaning_andAutoSearchesHansard() {
        val (emissions, hansard) = runLookup(wordInNoDictionary)
        val result = emissions.last()

        assertTrue(!result.hasDefinition)
        assertTrue(result.shouldOfferGuessMeaning)
        assertEquals(wordInNoDictionary, hansard.searchedFor)
        assertTrue(result.hansard is HansardLookupState.Done)
    }

    @Test
    fun shorterWordHit_doesNotCountAsDefinition_soHansardStillAutoSearches() {
        val shorterHit: suspend (String) -> TusaalangaResult = {
            TusaalangaResult.FoundForShorterWord(TusaalangaEntry("iglu", "ᐃᒡᓗ", "house"))
        }

        val (emissions, hansard) = runLookup(wordInNoDictionary, tusaalanga = shorterHit)
        val result = emissions.last()

        assertTrue(result.shorterWordHits.any { it.hit.source == DictionarySource.TUSAALANGA })
        assertTrue(!result.hasDefinition)
        assertTrue(result.shouldOfferGuessMeaning)
        assertEquals(wordInNoDictionary, hansard.searchedFor)
    }

    @Test
    fun tusaalangaExactHit_countsAsDefinition() {
        val exactHit: suspend (String) -> TusaalangaResult = {
            TusaalangaResult.Found(TusaalangaEntry("iglu", "ᐃᒡᓗ", "house"))
        }

        val (emissions, hansard) = runLookup(wordInNoDictionary, tusaalanga = exactHit)
        val result = emissions.last()

        assertTrue(result.dictionaryHits.any { it.source == DictionarySource.TUSAALANGA })
        assertTrue(result.hasDefinition)
        assertTrue(!result.shouldOfferGuessMeaning)
        assertNull(hansard.searchedFor)
    }

    @Test
    fun tusaalangaFetchFailure_isSurfacedButLookupStillCompletes() {
        val failing: suspend (String) -> TusaalangaResult = { TusaalangaResult.FetchFailed("network down") }

        val result = finalResult(wordInNoDictionary, tusaalanga = failing)

        assertEquals("network down", result.tusaalangaFetchError)
        assertTrue(result.dictionariesSettled)
        assertTrue(result.decomposition is DecompositionOutcome.Success)
    }

    @Test
    fun tusaalangaLookupThrowing_isTreatedAsAFetchFailure() {
        val throwing: suspend (String) -> TusaalangaResult = { error("boom") }

        val result = finalResult(wordInNoDictionary, tusaalanga = throwing)

        assertTrue(result.tusaalangaFetchError != null)
        assertTrue(result.dictionariesSettled)
    }

    @Test
    fun fstSelectedButUnavailable_reportsFstNotAvailable_withDictionariesStillRun() {
        val result = finalResult(
            wordInSpalding,
            analyzerChoice = AnalyzerChoice.FST,
            analyzerFor = { null },
        )

        assertEquals(DecompositionOutcome.Failure(FailureReason.FstNotAvailable), result.decomposition)
        assertTrue(result.hasDefinition, "dictionary lookup must still run even when the analyzer is unavailable")
        assertTrue(result.dictionariesSettled)
    }

    @Test
    fun successfulDecomposition_isReportedWithMorphemeRows() {
        val result = finalResult("iglumut")

        val success = result.decomposition as DecompositionOutcome.Success
        assertTrue(success.decompositions.isNotEmpty())
        assertTrue(success.decompositions.first().isNotEmpty())
        assertEquals("iglumut", success.word)
    }

    @Test
    fun decompose_expandAll_returnsEveryDecompositionWithoutAShowMoreFlag() {
        val lookup = WordLookup(RecordingHansardSource(), { MorphologicalAnalyzer_R2L() })
        val prefs = WordLookupPrefs(AnalyzerChoice.UQAILAUT, lenient = true)

        val previewed = runBlocking { lookup.decompose("iglumut", prefs) } as DecompositionOutcome.Success
        val expanded = runBlocking { lookup.decompose("iglumut", prefs, expandAll = true) } as DecompositionOutcome.Success

        assertTrue(!expanded.hasMore)
        assertTrue(expanded.decompositions.size >= previewed.decompositions.size)
    }

    @Test
    fun decompose_fstUnavailable_returnsFstNotAvailable() {
        val lookup = WordLookup(RecordingHansardSource(), analyzerFor = { null })

        val outcome = runBlocking {
            lookup.decompose("iglu", WordLookupPrefs(AnalyzerChoice.FST, lenient = true))
        }

        assertEquals(DecompositionOutcome.Failure(FailureReason.FstNotAvailable), outcome)
    }

    @Test
    fun analyzerTimeout_isReportedAsATimeoutFailure() {
        val result = finalResult(
            wordInNoDictionary,
            analyzerFor = { ThrowingAnalyzer { throw TimeoutException("slow") } },
        )

        assertEquals(DecompositionOutcome.Failure(FailureReason.Timeout), result.decomposition)
    }

    @Test
    fun analyzerError_isReportedAsAnAnalysisErrorFailure() {
        val result = finalResult(
            wordInNoDictionary,
            analyzerFor = { ThrowingAnalyzer { throw IllegalStateException("bad grammar table") } },
        )

        val failure = result.decomposition as DecompositionOutcome.Failure
        assertTrue(failure.reason is FailureReason.AnalysisError)
    }
}
