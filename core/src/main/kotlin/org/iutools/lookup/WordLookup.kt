package org.iutools.lookup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.iutools.corpus.HansardExampleSource
import org.iutools.corpus.HansardExamplesOutcome
import org.iutools.dictionary.DictionaryHit
import org.iutools.dictionary.DictionarySource
import org.iutools.dictionary.ShorterWordDictionaryHit
import org.iutools.dictionary.SpaldingDictionary
import org.iutools.dictionary.TusaalangaFetcher
import org.iutools.dictionary.TusaalangaResult
import org.iutools.llm.toMorphemeRows
import org.iutools.morph.AnalyzerChoice
import org.iutools.morph.MorphologicalAnalyzer
import org.iutools.morph.MorphologicalAnalyzerException
import org.iutools.script.Script
import org.iutools.script.TransCoder
import org.iutools.text.splitIntoWords
import java.util.concurrent.TimeoutException

/*
 * Looks up everything iutools knows about one Inuktitut word and returns it
 * as a single structure, ready to be shown.
 *
 * This is the port of the old iutools "core" layer's word-lookup entry
 * point: hand it a word plus the relevant preferences, get back one
 * WordLookupResult holding the morphological decomposition, the dictionary
 * hits (Spalding and Tusaalanga), the Hansard examples, and the few
 * gating flags a UI needs (whether a definition was found, whether to offer
 * the "guess the meaning" feature). The Android app and a future CLI both
 * drive this same class; neither adds lookup logic of its own.
 *
 * The one data source :core cannot reach on its own -- the Nunavut Hansard
 * example index, which on Android is a local SQLite file -- is injected as a
 * HansardExampleSource. The analyzer is injected too, as a factory the
 * caller owns the lifecycle of (WordLookup never closes it).
 *
 * lookup() returns a Flow because the answer arrives in pieces: the Spalding
 * hit is synchronous, the Tusaalanga hit is a network call, the Hansard
 * search only runs when no definition was found, and the decomposition runs
 * in parallel with all of that. A GUI collects the successive
 * WordLookupResults to fill its screen in as each part lands; a CLI that
 * only wants the finished answer takes the last one.
 */
class WordLookup(
    private val hansardExamples: HansardExampleSource,
    private val analyzerFor: (AnalyzerChoice) -> MorphologicalAnalyzer?,
    private val tusaalangaLookup: suspend (String) -> TusaalangaResult = TusaalangaFetcher::fetch,
) {

    /*
     * The analyzer only ever decomposes a single word. A caller that took
     * free-form input should split it here first and, if this returns more
     * than one word, ask which was meant before calling lookup() with it.
     */
    fun submittedWords(rawInput: String): List<String> = splitIntoWords(rawInput.trim())

    /*
     * The whole lookup as a single value, for a caller that has no use for
     * the intermediate snapshots (a CLI, a batch tool). Same work as
     * lookup(); just the final WordLookupResult.
     */
    suspend fun lookupOnce(word: String, prefs: WordLookupPrefs): WordLookupResult =
        lookup(word, prefs).last()

    fun lookup(word: String, prefs: WordLookupPrefs): Flow<WordLookupResult> = channelFlow {
        val enteredScript = TransCoder.textScript(word)

        // Spalding is embedded and instant, so it is resolved before the
        // first emission. A prefix hit is only tried when the exact word
        // missed, and -- deliberately -- does not count as "a definition was
        // found" (see WordLookupResult.hasDefinition): it still gates
        // nothing, but is shown and handed to the meaning-guesser.
        val spaldingHit = SpaldingDictionary.lookup(word)?.let {
            DictionaryHit(DictionarySource.SPALDING, it.word, it.meaning, enteredScript)
        }
        val spaldingShorterHit = if (spaldingHit == null) {
            SpaldingDictionary.lookupLongestPrefix(word)?.let { (_, entry) ->
                ShorterWordDictionaryHit(word, DictionaryHit(DictionarySource.SPALDING, entry.word, entry.meaning, enteredScript))
            }
        } else {
            null
        }

        val analyzer = analyzerFor(prefs.analyzerChoice)

        val mutex = Mutex()
        var result = WordLookupResult(
            word = word,
            enteredScript = enteredScript,
            decomposition = if (analyzer == null) {
                DecompositionOutcome.Failure(FailureReason.FstNotAvailable)
            } else {
                DecompositionOutcome.Loading
            },
            dictionaryHits = listOfNotNull(spaldingHit),
            shorterWordHits = listOfNotNull(spaldingShorterHit),
            dictionariesSettled = false,
            tusaalangaFetchError = null,
            hansard = HansardLookupState.NotSearched,
        )
        send(result)

        suspend fun update(block: (WordLookupResult) -> WordLookupResult) {
            mutex.withLock {
                result = block(result)
                send(result)
            }
        }

        coroutineScope {
            if (analyzer != null) {
                launch {
                    val outcome = decomposeWith(analyzer, word, prefs.lenient, expandAll = false, enteredScript)
                    update { it.copy(decomposition = outcome) }
                }
            }

            launch {
                val tusaalanga = runCatching { tusaalangaLookup(word) }
                    .getOrElse { TusaalangaResult.FetchFailed(it.message ?: it.toString()) }
                update { current ->
                    var hits = current.dictionaryHits
                    var shorter = current.shorterWordHits
                    when (tusaalanga) {
                        is TusaalangaResult.Found ->
                            hits = hits + DictionaryHit(
                                DictionarySource.TUSAALANGA, tusaalanga.entry.word, tusaalanga.entry.meaning, enteredScript,
                            )
                        is TusaalangaResult.FoundForShorterWord ->
                            shorter = shorter + ShorterWordDictionaryHit(
                                word,
                                DictionaryHit(DictionarySource.TUSAALANGA, tusaalanga.entry.word, tusaalanga.entry.meaning, enteredScript),
                            )
                        TusaalangaResult.NotFound, is TusaalangaResult.FetchFailed -> {}
                    }
                    current.copy(
                        dictionaryHits = hits,
                        shorterWordHits = shorter,
                        tusaalangaFetchError = (tusaalanga as? TusaalangaResult.FetchFailed)?.message,
                        dictionariesSettled = true,
                    )
                }

                // Hansard is searched automatically only when no dictionary
                // found the word -- otherwise it is left for the caller to
                // trigger on demand. A shorter-word hit does not count here
                // either, so this still fires for those.
                if (mutex.withLock { result.dictionaryHits.isEmpty() }) {
                    update { it.copy(hansard = HansardLookupState.Searching) }
                    val outcome = hansardExamples.examplesFor(word)
                    update { it.copy(hansard = HansardLookupState.Done(outcome)) }
                }
            }
        }
        // coroutineScope above joins both jobs; returning here closes the
        // channel and completes the flow.
    }

    /*
     * Just the decomposition step, on its own. lookup() runs this in
     * parallel with the dictionary lookups; a caller also uses it directly
     * for "show all decompositions" (expandAll = true) after the previewed
     * lookup, without redoing the dictionary/Hansard work.
     */
    suspend fun decompose(
        word: String,
        prefs: WordLookupPrefs,
        expandAll: Boolean = false,
    ): DecompositionOutcome {
        val analyzer = analyzerFor(prefs.analyzerChoice)
            ?: return DecompositionOutcome.Failure(FailureReason.FstNotAvailable)
        return decomposeWith(analyzer, word, prefs.lenient, expandAll, TransCoder.textScript(word))
    }

    // When not expanding, the analyzer is told to stop one past the preview
    // count -- enough to know whether a "show more" affordance is warranted,
    // without an exhaustive search up front.
    private suspend fun decomposeWith(
        analyzer: MorphologicalAnalyzer,
        word: String,
        lenient: Boolean,
        expandAll: Boolean,
        enteredScript: Script,
    ): DecompositionOutcome = withContext(Dispatchers.Default) {
        try {
            val fetchLimit = if (expandAll) Int.MAX_VALUE else PREVIEW_LIMIT + 1
            val decomps = analyzer.stopAfterN(fetchLimit).decomposeWord(word, lenient)
            val hasMore = !expandAll && decomps.size > PREVIEW_LIMIT
            val displayed = if (hasMore) decomps.take(PREVIEW_LIMIT) else decomps.toList()
            DecompositionOutcome.Success(
                word = word,
                lenient = lenient,
                decompositions = displayed.map { it.toMorphemeRows() },
                hasMore = hasMore,
                enteredScript = enteredScript,
            )
        } catch (e: TimeoutException) {
            DecompositionOutcome.Failure(FailureReason.Timeout)
        } catch (e: MorphologicalAnalyzerException) {
            DecompositionOutcome.Failure(FailureReason.AnalysisError(e.message))
        }
    }

    companion object {
        // How many decompositions a lookup surfaces before a "show more".
        const val PREVIEW_LIMIT = 3
    }
}

/*
 * Where the Hansard examples for a lookup stand: not searched (a definition
 * was found, so the search was left for the caller to trigger), searching,
 * or done with an outcome.
 */
sealed interface HansardLookupState {
    data object NotSearched : HansardLookupState
    data object Searching : HansardLookupState
    data class Done(val outcome: HansardExamplesOutcome) : HansardLookupState
}

data class WordLookupPrefs(
    val analyzerChoice: AnalyzerChoice,
    val lenient: Boolean,
)

data class WordLookupResult(
    val word: String,
    val enteredScript: Script,
    val decomposition: DecompositionOutcome,
    val dictionaryHits: List<DictionaryHit>,
    val shorterWordHits: List<ShorterWordDictionaryHit>,
    // False until every dictionary (Spalding is instant, Tusaalanga is a
    // network call) has answered.
    val dictionariesSettled: Boolean,
    val tusaalangaFetchError: String?,
    val hansard: HansardLookupState,
) {
    val hasDefinition: Boolean get() = dictionaryHits.isNotEmpty()

    // Offer "guess the meaning" exactly when no dictionary has a definition
    // and they have all answered. Deliberately does not also require a
    // successful decomposition -- the words that most need a guess are often
    // the ones the analyzer can't decompose at all.
    val shouldOfferGuessMeaning: Boolean
        get() = dictionaryHits.isEmpty() && dictionariesSettled && word.isNotBlank()
}
