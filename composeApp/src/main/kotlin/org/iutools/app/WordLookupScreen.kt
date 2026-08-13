package org.iutools.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.res.Configuration
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.iutools.linguisticdata.LinguisticData
import org.iutools.linguisticdata.Morpheme
import org.iutools.morph.Decomposition
import org.iutools.morph.MorphologicalAnalyzerException
import org.iutools.morph.r2l.MorphologicalAnalyzer_R2L
import org.iutools.script.Script
import org.iutools.script.TransCoder
import java.util.Locale
import java.util.concurrent.TimeoutException
// Property-reference delegation (`var x by screenState::x`, see
// WordLookupScreenState) needs these alongside the androidx.compose.runtime
// getValue/setValue already imported above (those are for MutableState
// delegates; these are for KMutableProperty0 delegates -- Kotlin picks the
// right overload per delegate type, no conflict).
import kotlin.getValue
import kotlin.setValue

private const val PREVIEW_LIMIT = 3

// How many Hansard examples HansardExamplesSection shows before its own
// "More" reveals the rest (all of which are already fetched -- see
// NunavutHansardLocalIndex.MAX_EXAMPLES -- this just paginates the display).
private const val HANSARD_PREVIEW_LIMIT = 5

// The in-app switch overrides the UI language independently of the device's
// system locale. endonym: each language's name is shown in itself, so it
// doesn't need translating.
enum class AppLanguage(val locale: Locale, val endonym: String) {
    ENGLISH(Locale.ENGLISH, "English"),
    FRENCH(Locale.FRENCH, "Français"),
}

fun defaultAppLanguage(): AppLanguage =
    if (Locale.getDefault().language == "fr") AppLanguage.FRENCH else AppLanguage.ENGLISH

// Chooses which script Inuktitut surface text (morpheme forms, canonical
// forms) is displayed in. AS_ENTERED follows whatever script the analyzed
// word was originally typed in (tracked per-analysis on DecomposeState.Success),
// falling back to Roman -- see displayForm().
enum class DisplayScript { ROMAN, SYLLABIC, AS_ENTERED }

// One hit from one dictionary source (Spalding, Tusaalanga, ...). Carries its own
// display title (source name, already localized) rather than a source enum, since
// DictionaryResultSection just needs to print it -- no other code branches on which
// source a result came from. Pairs the hit with the script the user typed it in --
// captured at lookup time, since (unlike DecomposeState.Success) a dictionary lookup
// can succeed even when decomposition fails, so there's no other enteredScript to
// read it off of.
// internal (not private): referenced by WordLookupScreenState below, which
// needs to be internal itself -- see that class's header comment.
internal data class DictionaryLookupResult(
    val title: String,
    val word: String,
    val meaning: String,
    val enteredScript: Script,
)

private fun displayForm(text: String, script: DisplayScript, enteredScript: Script): String {
    val target = when (script) {
        DisplayScript.ROMAN -> Script.ROMAN
        DisplayScript.SYLLABIC -> Script.SYLLABIC
        DisplayScript.AS_ENTERED -> if (enteredScript == Script.SYLLABIC) Script.SYLLABIC else Script.ROMAN
    }
    return TransCoder.ensureScript(target, text)
}

// internal (not private): constructed directly in WordLookupScreenTest.kt to
// exercise guessMeaningSeedPrompt() without needing a real analyzer run.
internal data class MorphemeRow(
    val surfaceForm: String,
    val morphemeId: String,
    val fullRecord: Morpheme?,
)

// internal (not private): DecomposeState below (internal for the same
// reason as DictionaryLookupResult) carries a FailureReason.
internal sealed interface FailureReason {
    data object Timeout : FailureReason
    data class AnalysisError(val detail: String?) : FailureReason
}

// internal (not private): referenced by WordLookupScreenState below, which
// needs to be internal itself -- see that class's header comment.
internal sealed interface DecomposeState {
    data object Idle : DecomposeState
    data object Loading : DecomposeState
    data class Success(
        val word: String,
        val lenient: Boolean,
        val decompositions: List<List<MorphemeRow>>,
        val hasMore: Boolean,
        val enteredScript: Script,
    ) : DecomposeState
    data class Failure(val reason: FailureReason) : DecomposeState
}

private fun Decomposition.toMorphemeRows(): List<MorphemeRow> {
    val linguisticData = LinguisticData.getInstance()
    val surfaceForms = surfaceForms()
    val morphemeIds = getMorphemes()
    return surfaceForms.indices.map { i ->
        val morphemeId = morphemeIds[i]
        MorphemeRow(
            surfaceForm = surfaceForms[i],
            morphemeId = morphemeId,
            fullRecord = linguisticData.getMorpheme(morphemeId),
        )
    }
}

// Localized scaffold text for guessMeaningSeedPrompt() -- resolved once via
// stringResource() in the composable that builds the seed prompt (see the
// "Guess Meaning" button in WordLookupScreen), since guessMeaningSeedPrompt()
// itself runs inside a button click handler, not a @Composable context.
// internal (not private): constructed directly in WordLookupScreenTest.kt.
internal data class GuessMeaningSeedLabels(
    val word: String,
    val decompositionHeader: String,
    val decompositionNumberTemplate: String,
    val unknownMorpheme: String,
    val question: String,
)

/**
 * Builds the text that seeds the Guess Meaning chat's input field: the
 * analyzed word plus its morphological decomposition(s), formatted for a
 * human (and an LLM) to read -- not sent automatically, the user can still
 * edit it before pressing Send. The instructions asking Claude to guess the
 * meaning live in GuessMeaningScreen's system prompt instead, kept separate
 * from this user-message content. Both this seed and that system prompt
 * follow the app's UI language, per Alain's request, so Claude's replies do
 * too.
 *
 * Takes [word]/[decompositions] directly rather than a DecomposeState.Success
 * -- it only ever reads those two fields, and this keeps it testable
 * (WordLookupScreenTest.kt) without needing a full DecomposeState instance.
 */
internal fun guessMeaningSeedPrompt(
    word: String,
    decompositions: List<List<MorphemeRow>>,
    preferFrench: Boolean,
    labels: GuessMeaningSeedLabels,
): String {
    val decompositionsText = decompositions.mapIndexed { index, rows ->
        val header = if (decompositions.size > 1) {
            String.format(labels.decompositionNumberTemplate, index + 1) + "\n"
        } else {
            ""
        }
        val morphemes = rows.joinToString("\n") { row ->
            val meaning = row.fullRecord?.preferredMeaning(preferFrench) ?: labels.unknownMorpheme
            "- ${row.surfaceForm} (${row.morphemeId}): $meaning"
        }
        header + morphemes
    }.joinToString("\n\n")

    return """
        ${labels.word} $word

        ${labels.decompositionHeader}

        $decompositionsText

        ${labels.question}
    """.trimIndent()
}

// internal (not private): unit-tested directly in WordLookupScreenTest.kt.
internal fun splitIntoWords(text: String): List<String> =
    text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

/**
 * Runs the analysis on a background thread. When [expandAll] is false, the
 * analyzer is told to stop as soon as it has found one more decomposition
 * than we display (PREVIEW_LIMIT + 1) -- enough to know whether a "More"
 * button is warranted, without paying for an exhaustive search up front.
 */
private suspend fun analyze(
    analyzer: MorphologicalAnalyzer_R2L,
    word: String,
    lenient: Boolean,
    expandAll: Boolean,
): DecomposeState = withContext(Dispatchers.Default) {
    try {
        val fetchLimit = if (expandAll) Int.MAX_VALUE else PREVIEW_LIMIT + 1
        val decomps = analyzer.stopAfterN(fetchLimit).decomposeWord(word, lenient)
        val hasMore = !expandAll && decomps.size > PREVIEW_LIMIT
        val displayed = if (hasMore) decomps.take(PREVIEW_LIMIT) else decomps.toList()
        DecomposeState.Success(
            word = word,
            lenient = lenient,
            decompositions = displayed.map { it.toMorphemeRows() },
            hasMore = hasMore,
            enteredScript = TransCoder.textScript(word),
        )
    } catch (e: TimeoutException) {
        DecomposeState.Failure(FailureReason.Timeout)
    } catch (e: MorphologicalAnalyzerException) {
        DecomposeState.Failure(FailureReason.AnalysisError(e.message))
    }
}

// Holds everything about the current word lookup that should survive
// navigating to Guess Meaning and back -- MainActivity.kt switches between
// WordLookupScreen and GuessMeaningScreen with a plain `when` (no
// Navigation Compose, see its header comment), which destroys and
// recreates WordLookupScreen's composition on every switch. Plain
// `remember { mutableStateOf(...) }` fields don't survive that -- state
// needs to live in an object created by the CALLER (MainActivity, whose own
// composition scope isn't destroyed by the `when` branch it contains), then
// passed in, or "Guess Meaning" -> back would land on an empty search
// screen instead of the word's card as it was -- exactly what Alain
// reported.
internal class WordLookupScreenState {
    var word by mutableStateOf("")
    var lenient by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var decomposeState by mutableStateOf<DecomposeState>(DecomposeState.Idle)
    var multiWordChoices by mutableStateOf<List<String>?>(null)
    var dictionaryResults by mutableStateOf<List<DictionaryLookupResult>>(emptyList())
    var dictionaryLoading by mutableStateOf(false)
    var dictionaryFetchError by mutableStateOf<String?>(null)
    var hansardResult by mutableStateOf<NunavutHansardResult?>(null)
    var hansardLoading by mutableStateOf(false)
    var lastSearchedWord by mutableStateOf("")
}

// internal (not private/public): takes an internal WordLookupScreenState
// parameter (see that class's header comment), so this can't be public.
@Composable
internal fun WordLookupScreen(
    screenState: WordLookupScreenState = remember { WordLookupScreenState() },
    onOpenGuessMeaning: (GuessMeaningCacheKey, String) -> Unit = { _, _ -> },
) {
    val analyzer = remember { MorphologicalAnalyzer_R2L() }
    val scope = rememberCoroutineScope()
    val baseContext = LocalContext.current

    // Delegates to screenState's properties (by property reference, not by
    // value) -- every read/write below still looks like a local `var`, but
    // actually lives in screenState, so it survives this composable being
    // torn down and recreated. See WordLookupScreenState's header comment.
    var word by screenState::word
    var lenient by screenState::lenient
    var uiLanguage by remember { mutableStateOf(AppSettings.loadLanguage(baseContext)) }
    var displayScript by remember { mutableStateOf(AppSettings.loadDisplayScript(baseContext)) }
    var showSettings by screenState::showSettings
    var state by screenState::decomposeState
    var multiWordChoices by screenState::multiWordChoices
    // Every dictionary source is checked, and Guess Meaning only offered once all of
    // them have answered -- see dictionaryLoading below. Spalding is a local/instant
    // lookup; Tusaalanga is a real network fetch (no distribution rights for its
    // content, must stay live -- see TusaalangaFetcher.kt), so this list fills in over
    // two separate updates, not one.
    var dictionaryResults by screenState::dictionaryResults
    var dictionaryLoading by screenState::dictionaryLoading
    // Tusaalanga's fetch failure (network/HTTP error, not just "word not found") --
    // debug-build-only, same purpose as the system-prompt inspection panel in
    // GuessMeaningScreen.kt: alerts a developer the fetcher broke, not end-user UX.
    var dictionaryFetchError by screenState::dictionaryFetchError

    // Bilingual Hansard examples (see NunavutHansardLocalIndex.kt): searched
    // automatically once every dictionary has answered with nothing, or on
    // demand (see the "Find bilingual examples of use" button below) when a
    // dictionary already found something -- see findWord()'s dictionary
    // coroutine and runHansardSearch() below. lastSearchedWord is the word
    // that search was/will be run against, captured separately from the
    // (possibly since-edited) word text field, for that on-demand button.
    var hansardResult by screenState::hansardResult
    var hansardLoading by screenState::hansardLoading
    var lastSearchedWord by screenState::lastSearchedWord

    suspend fun runHansardSearch(searchWord: String) {
        hansardLoading = true
        hansardResult = NunavutHansardLocalIndex.fetch(baseContext, searchWord)
        hansardLoading = false
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Overrides stringResource()'s language for everything below, independently
    // of the device's system locale.
    val baseConfiguration = LocalConfiguration.current
    val localizedConfiguration = remember(uiLanguage, baseConfiguration) {
        Configuration(baseConfiguration).apply { setLocale(uiLanguage.locale) }
    }
    val localizedContext = remember(uiLanguage, baseContext) {
        baseContext.createConfigurationContext(localizedConfiguration)
    }

    fun findWord(spaldingResultTitle: String, tusaalangaResultTitle: String) {
        val wordToAnalyze = word.trim()
        if (wordToAnalyze.isEmpty()) return
        val individualWords = splitIntoWords(wordToAnalyze)
        if (individualWords.size > 1) {
            // The analyzer only ever decomposes a single word -- rather than feeding
            // it a multi-word string and getting a confusing failure, ask which word
            // was meant, then resubmit as if only that word had been typed.
            multiWordChoices = individualWords
            return
        }
        val lenientAtSearch = lenient
        keyboardController?.hide()
        focusManager.clearFocus()
        lastSearchedWord = wordToAnalyze
        hansardResult = null
        hansardLoading = false

        // Dictionaries first. Checked regardless of whether a decomposition is found
        // below: a word can be a real dictionary entry even when the analyzer can't
        // decompose it -- this used to be a dead end (no decomposition meant the
        // dictionaries never even got checked, since that lookup only ran inside the
        // Guess Meaning button's own onClick).
        //
        // enteredScript captured here (not read off DecomposeState.Success, which
        // won't exist if decomposition fails) so dictionary words can still be
        // displayed in the user's chosen script even when there's no decomposition.
        val enteredScript = TransCoder.textScript(wordToAnalyze)

        // Spalding is a local/instant lookup, so it's populated synchronously here
        // (not inside the coroutine below) -- DisplayScriptSwitchUiTest.kt relies on
        // this being visible the moment findWord() returns, no waiting needed.
        val spaldingHit = SpaldingDictionary.lookup(baseContext, wordToAnalyze)?.let { entry ->
            DictionaryLookupResult(spaldingResultTitle, entry.word, entry.meaning, enteredScript)
        }
        dictionaryResults = listOfNotNull(spaldingHit)
        dictionaryFetchError = null
        dictionaryLoading = true

        // Tusaalanga is a real network call (see TusaalangaFetcher.kt for why it can't
        // be embedded like Spalding) -- runs separately and appends to whatever
        // Spalding already found, rather than blocking on it.
        scope.launch {
            val tusaalanga = TusaalangaFetcher.fetch(wordToAnalyze)
            if (tusaalanga is TusaalangaResult.Found) {
                dictionaryResults = dictionaryResults + DictionaryLookupResult(
                    tusaalangaResultTitle,
                    tusaalanga.entry.word,
                    tusaalanga.entry.meaning,
                    enteredScript,
                )
            }
            dictionaryFetchError = (tusaalanga as? TusaalangaResult.FetchFailed)?.message
            dictionaryLoading = false
            // Only searched automatically when no dictionary found anything --
            // otherwise it's offered as an on-demand "Find bilingual examples
            // of use" button instead (see the button below), rather than
            // running both lookups every time.
            if (dictionaryResults.isEmpty()) {
                runHansardSearch(wordToAnalyze)
            }
        }

        state = DecomposeState.Loading
        scope.launch {
            state = analyze(analyzer, wordToAnalyze, lenientAtSearch, expandAll = false)
        }
    }

    fun selectWord(chosen: String, spaldingResultTitle: String, tusaalangaResultTitle: String) {
        multiWordChoices = null
        word = chosen
        findWord(spaldingResultTitle, tusaalangaResultTitle)
    }

    fun loadMore(previous: DecomposeState.Success) {
        state = DecomposeState.Loading
        scope.launch {
            state = analyze(analyzer, previous.word, previous.lenient, expandAll = true)
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // One continuous scroll for the whole screen, per Alain's request --
        // previously each result section (dictionary/Hansard/decompositions)
        // scrolled independently in its own bounded box, which meant a swipe
        // starting outside that box's bounds did nothing.
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            // Resolved here (not inside findWord() itself, which isn't @Composable) so
            // dictionary result titles follow uiLanguage -- passed into findWord()/
            // selectWord() at each call site below.
            val spaldingResultTitle = stringResource(R.string.spalding_result_title)
            val tusaalangaResultTitle = stringResource(R.string.tusaalanga_result_title)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Only offered once a morphological analysis has been produced (Guess
                // Meaning seeds its prompt from that analysis, see
                // guessMeaningSeedPrompt(), so it has nothing to work from before then)
                // AND every dictionary source has answered with no hit -- see
                // "Court-circuit sur correspondance exacte" in the plan doc.
                // dictionaryLoading gates this so the button doesn't flash on screen
                // before Tusaalanga's (async) answer has had a chance to arrive.
                val currentState = state
                if (!dictionaryLoading && dictionaryResults.isEmpty() &&
                    currentState is DecomposeState.Success &&
                    currentState.decompositions.isNotEmpty()
                ) {
                    val guessMeaningSeedLabels = GuessMeaningSeedLabels(
                        word = stringResource(R.string.guess_meaning_seed_word_label),
                        decompositionHeader = stringResource(R.string.guess_meaning_seed_decomposition_header),
                        decompositionNumberTemplate = stringResource(R.string.guess_meaning_seed_decomposition_number),
                        unknownMorpheme = stringResource(R.string.guess_meaning_seed_unknown_morpheme),
                        question = stringResource(R.string.guess_meaning_seed_question),
                    )
                    TextButton(
                        onClick = {
                            onOpenGuessMeaning(
                                GuessMeaningCacheKey(currentState.word, currentState.lenient),
                                guessMeaningSeedPrompt(
                                    currentState.word,
                                    currentState.decompositions,
                                    uiLanguage == AppLanguage.FRENCH,
                                    guessMeaningSeedLabels,
                                ),
                            )
                        },
                    ) {
                        Text("🔮 " + stringResource(R.string.guess_meaning_button))
                    }
                } else {
                    Spacer(modifier = Modifier)
                }
                TextButton(onClick = { showSettings = true }, modifier = Modifier.testTag("settings_button")) {
                    Text("⚙ " + stringResource(R.string.settings_button))
                }
            }

            if (showSettings) {
                SettingsDialog(
                    uiLanguage = uiLanguage,
                    onLanguageSelected = {
                        uiLanguage = it
                        AppSettings.saveLanguage(baseContext, it)
                    },
                    displayScript = displayScript,
                    onDisplayScriptSelected = {
                        displayScript = it
                        AppSettings.saveDisplayScript(baseContext, it)
                    },
                    onDismiss = { showSettings = false },
                )
            }

            multiWordChoices?.let { choices ->
                MultiWordChoiceDialog(
                    words = choices,
                    onSelect = { selectWord(it, spaldingResultTitle, tusaalangaResultTitle) },
                    onDismiss = { multiWordChoices = null },
                )
            }

            Text(
                text = stringResource(R.string.screen_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = word,
                onValueChange = { word = it },
                label = { Text(stringResource(R.string.word_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { findWord(spaldingResultTitle, tusaalangaResultTitle) }),
                modifier = Modifier.fillMaxWidth().testTag("word_input"),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.lenient_switch_label))
                Switch(checked = lenient, onCheckedChange = { lenient = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { findWord(spaldingResultTitle, tusaalangaResultTitle) },
                enabled = word.isNotBlank() && state != DecomposeState.Loading,
                modifier = Modifier.fillMaxWidth().testTag("find_word_button"),
            ) {
                Text(stringResource(R.string.find_word_button))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dictionary results come first, per Alain's request -- a word can be a
            // real dictionary entry even when the analyzer below finds no
            // decomposition for it (or vice versa); both are shown, independently.
            if (dictionaryResults.isNotEmpty()) {
                DictionaryResultSection(dictionaryResults, displayScript)
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (dictionaryLoading) {
                Text(stringResource(R.string.dictionary_checking_label), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }
            // Debug-build-only: alerts a developer that the Tusaalanga fetch itself
            // broke (network/HTTP error), not that the word simply wasn't found --
            // not meant for a normal user's build, same reasoning as the debug-only
            // system-prompt panel in GuessMeaningScreen.kt.
            if (BuildConfig.DEBUG) {
                dictionaryFetchError?.let { error ->
                    Text(
                        text = stringResource(R.string.dictionary_fetch_error, error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Decompositions come before the Hansard bilingual examples, per
            // Alain's request.
            when (val s = state) {
                is DecomposeState.Idle -> {}
                is DecomposeState.Loading -> CircularProgressIndicator()
                is DecomposeState.Failure -> Text(
                    text = when (val reason = s.reason) {
                        is FailureReason.Timeout -> stringResource(R.string.error_timeout)
                        is FailureReason.AnalysisError ->
                            stringResource(R.string.error_analysis, reason.detail ?: "")
                    },
                    color = MaterialTheme.colorScheme.error,
                )
                is DecomposeState.Success -> {
                    DecompositionSection(
                        success = s,
                        preferFrench = uiLanguage == AppLanguage.FRENCH,
                        displayScript = displayScript,
                        onLoadMore = { loadMore(s) },
                    )
                }
            }
            if (state is DecomposeState.Success) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // A dictionary hit doesn't auto-search the Hansard (see findWord()) --
            // offered as an on-demand button instead, shown in the same spot the
            // results would otherwise appear.
            if (hansardResult == null && !hansardLoading && !dictionaryLoading && dictionaryResults.isNotEmpty()) {
                TextButton(
                    onClick = { scope.launch { runHansardSearch(lastSearchedWord) } },
                    modifier = Modifier.testTag("hansard_examples_button"),
                ) {
                    Text(stringResource(R.string.hansard_examples_button))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (hansardLoading) {
                Text(stringResource(R.string.hansard_checking_label), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }
            hansardResult?.let { result ->
                when (result) {
                    is NunavutHansardResult.Found -> {
                        HansardExamplesSection(result.word, result.examples, displayScript)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    NunavutHansardResult.NotFound -> {
                        Text(stringResource(R.string.hansard_no_examples, lastSearchedWord))
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    // Shown for every build, not just debug -- unlike
                    // dictionaryFetchError above, a real end user (not just a
                    // dev) can genuinely hit these: the DB just hasn't been
                    // downloaded yet, or the app was updated past the schema
                    // version they downloaded earlier. Same download flow
                    // fixes both -- see NunavutHansardDownloader.kt.
                    NunavutHansardResult.IndexMissing, is NunavutHansardResult.IndexVersionMismatch -> {
                        HansardDownloadSection(onDownloaded = { runHansardSearch(lastSearchedWord) })
                        if (BuildConfig.DEBUG) {
                            val debugDetail = if (result is NunavutHansardResult.IndexVersionMismatch) {
                                stringResource(R.string.hansard_index_version_mismatch, result.found, result.expected)
                            } else {
                                stringResource(R.string.hansard_index_missing)
                            }
                            Text(
                                text = debugDetail,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                NunavutHansardLocalIndex.debugStatus(baseContext)?.let { status ->
                    Text(text = "Hansard DB: $status", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
    }
}

@Composable
private fun SettingsDialog(
    uiLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    displayScript: DisplayScript,
    onDisplayScriptSelected: (DisplayScript) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("settings_dialog_close_button")) {
                Text(stringResource(R.string.close_button))
            }
        },
        title = { Text(stringResource(R.string.settings_button)) },
        text = {
            Column {
                Text(stringResource(R.string.ui_language_label), style = MaterialTheme.typography.labelMedium)
                Row {
                    AppLanguage.entries.forEach { language ->
                        TextButton(onClick = { onLanguageSelected(language) }) {
                            Text(
                                text = language.endonym,
                                fontWeight = if (language == uiLanguage) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(stringResource(R.string.display_script_label), style = MaterialTheme.typography.labelMedium)
                Column {
                    val labelFor = mapOf(
                        DisplayScript.ROMAN to R.string.display_script_roman,
                        DisplayScript.SYLLABIC to R.string.display_script_syllabic,
                        DisplayScript.AS_ENTERED to R.string.display_script_as_entered,
                    )
                    DisplayScript.entries.forEach { script ->
                        TextButton(onClick = { onDisplayScriptSelected(script) }) {
                            Text(
                                text = stringResource(labelFor.getValue(script)),
                                fontWeight = if (script == displayScript) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun MultiWordChoiceDialog(
    words: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_button)) } },
        title = { Text(stringResource(R.string.multi_word_dialog_title)) },
        text = {
            Column {
                words.forEach { candidateWord ->
                    TextButton(
                        onClick = { onSelect(candidateWord) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(candidateWord, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
    )
}

@Composable
private fun DictionaryResultSection(results: List<DictionaryLookupResult>, displayScript: DisplayScript) {
    // Shown inline in the main results area now (not a dialog): dictionary and
    // decomposition results are peers, not an interruption. Entries can run from a
    // couple hundred characters (Tusaalanga) to 7000+ (Spalding, which bundles a
    // headword with all its related variants in one block of prose), so this gets
    // its own bounded scroll region rather than pushing the rest of the screen down
    // indefinitely -- shared across every source found, not one region each.
    // Deliberately NOT folded into the single continuous page the way
    // DecompositionSection/HansardExamplesSection were (see WordLookupScreen's
    // outer Column) -- Alain's request to make scrolling continuous was scoped
    // to those two, and an unbounded 7000-char Spalding entry inline would
    // dominate the page exactly as this comment already warned against.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        results.forEachIndexed { index, result ->
            Text(
                text = result.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = displayForm(result.word, displayScript, result.enteredScript),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("dictionary_result_word_$index"),
            )
            // The definition itself is always English prose (including any cross-
            // references to other Inuktitut words it contains) -- script conversion
            // only applies to the clean, isolated headword above, per the plan doc's
            // Spalding TODO (same limitation applies to Tusaalanga's prose).
            Text(text = result.meaning)
            if (index != results.lastIndex) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// internal (not private): unit-tested directly -- pure string search, no
// Compose dependency.
internal fun highlightRange(sentence: String, word: String): IntRange? {
    if (word.isBlank()) return null
    val start = sentence.indexOf(word, ignoreCase = true)
    return if (start < 0) null else start until (start + word.length)
}

@Composable
private fun CollapsibleSectionHeader(text: String, expanded: Boolean, onToggle: () -> Unit, testTag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        // A plain glyph, not a Material Icon -- ExpandMore/ExpandLess live in
        // the "extended" icon set, not the core one this project depends on,
        // and pulling in that whole extra artifact for one chevron isn't
        // worth it (same reasoning as the emoji-in-Text glyphs used
        // elsewhere in this screen, e.g. "⚙ "/"🔮 ").
        Text(text = if (expanded) "▾" else "▸", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DecompositionSection(
    success: DecomposeState.Success,
    preferFrench: Boolean,
    displayScript: DisplayScript,
    onLoadMore: () -> Unit,
) {
    if (success.decompositions.isEmpty()) {
        Text(stringResource(R.string.no_decompositions, success.word))
        return
    }
    // Keyed on the word so a fresh search starts expanded again, rather than
    // carrying over whatever collapse state the previous word's section was
    // left in.
    var expanded by remember(success.word) { mutableStateOf(true) }
    CollapsibleSectionHeader(
        text = if (success.hasMore) {
            stringResource(R.string.decompositions_header_partial, success.word, PREVIEW_LIMIT)
        } else {
            stringResource(R.string.decompositions_header_full, success.decompositions.size, success.word)
        },
        expanded = expanded,
        onToggle = { expanded = !expanded },
        testTag = "decomposition_section_header",
    )
    if (!expanded) return
    Spacer(modifier = Modifier.height(8.dp))
    success.decompositions.forEachIndexed { index, rows ->
        if (success.decompositions.size > 1) {
            Text(
                text = stringResource(R.string.decomposition_number, index + 1),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        MorphemeTable(rows, preferFrench = preferFrench, displayScript = displayScript, enteredScript = success.enteredScript)
        Spacer(modifier = Modifier.height(12.dp))
    }
    if (success.hasMore) {
        TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.more_button))
        }
    }
}

@Composable
private fun HansardExamplesSection(word: String, examples: List<BilingualExample>, displayScript: DisplayScript) {
    // Keyed on the word, same reasoning as DecompositionSection: a fresh
    // search starts expanded and re-collapsed to the first
    // HANSARD_PREVIEW_LIMIT examples, not wherever the previous word's
    // section was left.
    var expanded by remember(word) { mutableStateOf(true) }
    var visibleCount by remember(word) { mutableStateOf(minOf(HANSARD_PREVIEW_LIMIT, examples.size)) }
    CollapsibleSectionHeader(
        text = stringResource(R.string.hansard_examples_header),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        testTag = "hansard_section_header",
    )
    if (!expanded) return
    Spacer(modifier = Modifier.height(8.dp))
    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    // The corpus's Inuktitut side is always syllabics (unlike a
    // dictionary hit, there's no per-entry enteredScript to read -- see
    // NunavutHansardLocalIndex.kt) -- and unlike a dictionary definition,
    // the whole line here is Inuktitut prose, not a headword plus English
    // commentary, so script conversion applies to the full sentence, not
    // just an isolated word.
    val displayedWord = displayForm(word, displayScript, Script.SYLLABIC)
    examples.take(visibleCount).forEachIndexed { index, example ->
        val displayedSentence = displayForm(example.inuktitut, displayScript, Script.SYLLABIC)
        // Highlighting the searched word gives a visual hint of roughly
        // where its English equivalent falls in the translation
        // (beginning/middle/end), per Alain's request -- same idea as
        // inuktitutcomputing.ca's own highlighted-word search (see
        // NunavutHansardLocalIndex.kt's header comment for why this
        // feature no longer depends on that site, but the UX idea is
        // still a good one). Best-effort: converting the sentence and
        // the word to the display script separately, then re-locating
        // the word by substring search, can occasionally miss (e.g.
        // word-boundary spelling changes in the romanization) -- same
        // imprecision the original site's plain string search had.
        val annotatedSentence = remember(displayedSentence, displayedWord, highlightColor) {
            buildAnnotatedString {
                append(displayedSentence)
                highlightRange(displayedSentence, displayedWord)?.let { range ->
                    addStyle(
                        SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold),
                        range.first,
                        range.last + 1,
                    )
                }
            }
        }
        Text(text = annotatedSentence, modifier = Modifier.testTag("hansard_example_iu_$index"))
        Text(text = example.english, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(12.dp))
    }
    // Purely a display reveal, no new fetch -- every example up to
    // NunavutHansardLocalIndex.MAX_EXAMPLES is already in `examples`.
    if (visibleCount < examples.size) {
        TextButton(
            onClick = { visibleCount = minOf(visibleCount + HANSARD_PREVIEW_LIMIT, examples.size) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.more_button))
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    Text(
        text = stringResource(R.string.hansard_attribution),
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun HansardDownloadSection(onDownloaded: suspend () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<HansardDownloadProgress?>(null) }

    fun startDownload() {
        scope.launch {
            var last: HansardDownloadProgress? = null
            NunavutHansardDownloader.download(context).collect {
                progress = it
                last = it
            }
            if (last is HansardDownloadProgress.Done) {
                onDownloaded()
            }
        }
    }

    when (val p = progress) {
        null, is HansardDownloadProgress.Failed -> {
            p?.let { failed ->
                Text(
                    text = stringResource(R.string.hansard_download_failed, failed.message),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            // A filled Button, not TextButton -- this is the only way to get
            // the feature working at all (unlike e.g. the on-demand "Find
            // bilingual examples" button elsewhere, where TextButton's
            // plain-text styling is fine since dictionary results are
            // already on screen). Confirmed by a real screenshot: a
            // TextButton here read as inert text, not something tappable.
            Button(
                onClick = { startDownload() },
                modifier = Modifier.fillMaxWidth().testTag("hansard_download_button"),
            ) {
                Text(stringResource(R.string.hansard_download_button))
            }
            Text(
                text = stringResource(R.string.hansard_download_hint),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        is HansardDownloadProgress.InProgress -> {
            val percent = if (p.totalBytes > 0) (p.bytesDownloaded * 100 / p.totalBytes).toInt() else 0
            Text(stringResource(R.string.hansard_download_progress, percent))
        }
        HansardDownloadProgress.Decompressing -> Text(stringResource(R.string.hansard_download_decompressing))
        // Momentary -- onDownloaded() above re-runs the Hansard search, whose
        // result replaces this whole section once it lands.
        HansardDownloadProgress.Done -> {}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MorphemeTable(
    rows: List<MorphemeRow>,
    preferFrench: Boolean,
    displayScript: DisplayScript,
    enteredScript: Script,
) {
    var selectedRow by remember { mutableStateOf<MorphemeRow?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.table_header_morpheme),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.table_header_meaning),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { selectedRow = row })
                    .padding(vertical = 8.dp),
            ) {
                Text(text = displayForm(row.surfaceForm, displayScript, enteredScript), modifier = Modifier.weight(1f))
                Text(
                    text = row.fullRecord?.preferredMeaning(preferFrench) ?: stringResource(R.string.unknown_meaning),
                    modifier = Modifier.weight(1f).testTag("morpheme_meaning_$index"),
                )
            }
            HorizontalDivider()
        }
    }

    selectedRow?.let { row ->
        MorphemeDetailDialog(
            row = row,
            displayScript = displayScript,
            enteredScript = enteredScript,
            onDismiss = { selectedRow = null },
        )
    }
}

@Composable
private fun MorphemeDetailDialog(
    row: MorphemeRow,
    displayScript: DisplayScript,
    enteredScript: Script,
    onDismiss: () -> Unit,
) {
    val morpheme = row.fullRecord
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_button)) } },
        title = { Text(displayForm(row.surfaceForm, displayScript, enteredScript)) },
        text = {
            Column {
                DetailField(stringResource(R.string.detail_id), row.morphemeId)
                DetailField(
                    stringResource(R.string.detail_canonical_form),
                    morpheme?.morpheme?.let { displayForm(it, displayScript, enteredScript) },
                )
                DetailField(stringResource(R.string.detail_meaning_french), morpheme?.frenchMeaning)
                DetailField(stringResource(R.string.detail_meaning_english), morpheme?.englishMeaning)
                DetailField(stringResource(R.string.detail_dialect), morpheme?.dialect)
            }
        },
    )
}

@Composable
private fun DetailField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(text = value)
    }
}
