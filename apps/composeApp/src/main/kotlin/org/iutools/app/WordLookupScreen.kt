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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import android.content.Context
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.iutools.corpus.BilingualExample
import org.iutools.corpus.HansardDownloadProgress
import org.iutools.corpus.HansardExamplesOutcome
import org.iutools.corpus.HansardIndexDownloader
import org.iutools.dictionary.DictionaryHit
import org.iutools.dictionary.DictionarySource
import org.iutools.dictionary.ShorterWordDictionaryHit
import org.iutools.dictionary.SpaldingDictionary
import org.iutools.i18n.AppLanguage
import org.iutools.llm.AggregatedBackendStats
import org.iutools.llm.ChatMessage
import org.iutools.llm.GuessMeaningCacheKey
import org.iutools.llm.GuessMeaningConversationKey
import org.iutools.llm.GuessMeaningSeedLabels
import org.iutools.llm.MorphemeRow
import org.iutools.llm.guessMeaningSeedPrompt
import org.iutools.lookup.DecompositionOutcome
import org.iutools.lookup.FailureReason
import org.iutools.lookup.HansardLookupState
import org.iutools.lookup.WordLookup
import org.iutools.lookup.WordLookupPrefs
import org.iutools.morph.AnalyzerChoice
import org.iutools.morph.MorphologicalAnalyzer
import org.iutools.morph.fst.MorphologicalAnalyzer_FST
import org.iutools.morph.r2l.MorphologicalAnalyzer_R2L
import java.io.File
import org.iutools.script.DisplayScript
import org.iutools.script.Script
import org.iutools.script.TransCoder
import org.iutools.script.displayForm
import org.iutools.script.displayFormBothScripts
import org.iutools.text.highlightRange
import org.iutools.text.highlightRanges
import org.iutools.text.splitIntoWords
// Property-reference delegation (`var x by screenState::x`, see
// WordLookupScreenState) needs these alongside the androidx.compose.runtime
// getValue/setValue already imported above (those are for MutableState
// delegates; these are for KMutableProperty0 delegates -- Kotlin picks the
// right overload per delegate type, no conflict).
import kotlin.getValue
import kotlin.setValue

// How many Hansard examples HansardExamplesSection shows before its own
// "More" reveals the rest (all of which are already fetched -- see
// NunavutHansardLocalIndex.MAX_EXAMPLES -- this just paginates the display).
private const val HANSARD_PREVIEW_LIMIT = 5

// How many Hansard examples guessMeaningSeedPrompt() sends to Claude --
// separate from HANSARD_PREVIEW_LIMIT (the on-screen preview count), per
// Alain's request.
private const val GUESS_MEANING_HANSARD_EXAMPLES = 10

// AppLanguage (+ defaultAppLanguage / toMeaningLanguage) moved to :core
// (org.iutools.i18n) -- the app-wide English/French choice is plain domain
// data, reusable beyond this screen. The Compose plumbing that acts on it
// (LocalizedContent, localizedStringResource, LocalizedAlertDialog below)
// stays here.

// Overrides stringResource()'s language for [content], independently of the
// device's system locale -- every screen that has its own uiLanguage
// (WordLookupScreen directly, ExplanationScreen via the WordInfoSnapshot it
// was opened with) needs this, since MainActivity.kt renders each as its own
// top-level composable rather than nesting them, so a wrap done inside one
// screen doesn't reach the others.
@Composable
internal fun LocalizedContent(uiLanguage: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val localizedConfiguration = remember(uiLanguage, baseConfiguration) {
        Configuration(baseConfiguration).apply { setLocale(uiLanguage.locale) }
    }
    val localizedContext = remember(uiLanguage, baseContext) {
        baseContext.createConfigurationContext(localizedConfiguration)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
        content = content,
    )
}

// The real Activity context, established once at the very top of the
// composition (see MainActivity.kt), before anything -- LocalizedContent
// included -- ever swaps LocalContext.current for a locale override.
// RealAndroidContext (below) uses this to escape back to it around
// interactive widgets that need a genuine Activity context to work. Defaults
// to null (rather than error()-ing on a missing provider) so tests that
// compose a screen directly -- never going through MainActivity's own
// top-level provider -- fall back to whatever LocalContext.current already
// is at that point instead of crashing; RealAndroidContext treats null the
// same way.
internal val LocalRealAndroidContext = staticCompositionLocalOf<Context?> { null }

// Resolves a single string in [uiLanguage] without touching any ambient
// CompositionLocal -- safe to call from anywhere, including inside
// RealAndroidContent, unlike stringResource() (see that composable's own
// header comment for why that distinction matters here).
@Composable
internal fun localizedStringResource(uiLanguage: AppLanguage, id: Int): String {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val localizedContext = remember(uiLanguage, baseContext, baseConfiguration) {
        baseContext.createConfigurationContext(Configuration(baseConfiguration).apply { setLocale(uiLanguage.locale) })
    }
    return localizedContext.getString(id)
}

@Composable
internal fun localizedStringResource(uiLanguage: AppLanguage, id: Int, vararg formatArgs: Any): String {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val localizedContext = remember(uiLanguage, baseContext, baseConfiguration) {
        baseContext.createConfigurationContext(Configuration(baseConfiguration).apply { setLocale(uiLanguage.locale) })
    }
    return localizedContext.getString(id, *formatArgs)
}

// OutlinedTextField's own platform text-editing internals (the floating
// copy/paste toolbar in particular) need LocalContext.current to resolve
// back to a real Activity to find its Window -- a locale-only synthetic
// context (what LocalizedContent provides, see above) crashed this every
// time on Alain's Samsung phone (long-press to paste in the API key field).
// Wraps [content] to restore the real context for exactly that reason; any
// localized label/placeholder text the widget needs must be resolved
// *before* entering this wrap, with localizedStringResource above -- a live
// stringResource() call made *inside* [content] would silently lose the
// language override, since it would resolve against the real (unlocalized)
// context this restores.
@Composable
internal fun RealAndroidContext(content: @Composable () -> Unit) {
    val real = LocalRealAndroidContext.current ?: LocalContext.current
    CompositionLocalProvider(LocalContext provides real, content = content)
}

// AlertDialog (like Dialog/Popup generally) renders into its own separate
// window, with its own AndroidComposeView root -- that root re-establishes
// LocalContext/LocalConfiguration from the *dialog's own* (unlocalized)
// context, so a LocalizedContent wrap further up the tree never reaches a
// dialog's title/text/button slots (confirmed the hard way: Alain found
// Settings' content stayed in English even though it's nested inside
// WordLookupScreen's own LocalizedContent wrap). Every AlertDialog in this
// app goes through here instead of calling AlertDialog(...) directly, so
// each slot gets its own LocalizedContent wrap, placed *inside* the dialog's
// own composition root where it can actually take effect.
@Composable
internal fun LocalizedAlertDialog(
    uiLanguage: AppLanguage,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { LocalizedContent(uiLanguage, confirmButton) },
        dismissButton = dismissButton?.let { slot -> { LocalizedContent(uiLanguage, slot) } },
        title = title?.let { slot -> { LocalizedContent(uiLanguage, slot) } },
        text = text?.let { slot -> { LocalizedContent(uiLanguage, slot) } },
        modifier = modifier,
    )
}

// DisplayScript (+ resolve/displayForm/displayFormBothScripts) and
// AnalyzerChoice moved to :core (org.iutools.script / org.iutools.morph) --
// plain presentation/analyzer-selection logic, reusable beyond this screen.
// FST-asset-load failure still falls back to R.string.error_fst_not_available
// here (see findWord() / fstTransducerFile).

// DictionaryHit / ShorterWordDictionaryHit / DictionarySource live in :core
// (org.iutools.dictionary) -- plain dictionary-lookup data, reusable beyond
// this screen. The screen turns DictionarySource into a localized name at
// render time (see DictionarySource.localizedTitle below). The
// screen-specific rule still lives here: a ShorterWordDictionaryHit does NOT
// count as "a definition was found" for gating the Guess Meaning button or
// the automatic Hansard search (see shouldOfferGuessMeaning()) -- only an
// exact-word hit does.

// A frozen copy of everything WordLookupScreen shows about the current word
// below its search controls (dictionary/decomposition/Hansard results) --
// built at the moment "Expliquer" is tapped (see GuessMeaningSection's
// onExplain) and handed to ExplanationScreen.kt so its bottom pane can show
// the same "fiche" without re-running any lookups or needing WordLookupScreen
// itself to still be composed. Deliberately a snapshot, not live state: that
// screen is meant as a static reference to read alongside the explanation
// (per Alain's request), not a second live copy of the search screen -- see
// WordInfoCard in ExplanationScreen.kt, which renders it read-only (no
// "load more"/"find examples"/"download" affordances).
internal data class WordInfoSnapshot(
    val decomposeState: DecompositionOutcome,
    val dictionaryResults: List<DictionaryHit>,
    val shorterWordDictionaryResults: List<ShorterWordDictionaryHit>,
    val dictionaryLoading: Boolean,
    val hansardResult: HansardExamplesOutcome?,
    val hansardLoading: Boolean,
    val lastSearchedWord: String,
    val lastSearchedWordScript: Script,
    val displayScript: DisplayScript,
    val uiLanguage: AppLanguage,
)

// DecompositionOutcome (+ FailureReason) lives in :core (org.iutools.lookup)
// -- the analyzer step's result, reusable beyond this screen (see
// doc/dev/plans/word-lookup-to-core.md). It keeps Idle/Loading alongside
// Success/Failure so this screen can use one field for the whole "state of
// the decomposition request", not just its finished outcome.

// internal (not private): unit-tested directly in WordLookupScreenTest.kt.
// Deliberately does NOT also require a successful decomposition -- an
// earlier version of this condition did, which silently hid the Guess
// Meaning button for exactly the words it's most needed for (ones the
// analyzer fails to decompose at all). Alain found this via a real word
// that had a shorter-word dictionary hit but no decomposition.
internal fun shouldOfferGuessMeaning(
    dictionaryResults: List<DictionaryHit>,
    dictionaryLoading: Boolean,
    lastSearchedWord: String,
): Boolean = dictionaryResults.isEmpty() && !dictionaryLoading && lastSearchedWord.isNotBlank()

// The compiled FST transducer ships as an app asset; the pure-Java reader
// (see MorphologicalAnalyzer_FST) needs a real File, so the asset is copied
// into the app's files dir on first use.
private const val FST_TRANSDUCER_ASSET = "lexicon-analyser.hfstol"

private fun fstTransducerFile(context: Context): File {
    val dest = File(context.filesDir, FST_TRANSDUCER_ASSET)
    // Re-copy whenever the bundled asset differs in size (e.g. after an app
    // update that rebuilt the transducer) -- cheap, ~690 KB.
    val assetBytes = context.assets.open(FST_TRANSDUCER_ASSET).use { it.readBytes() }
    if (!dest.exists() || dest.length() != assetBytes.size.toLong()) {
        dest.writeBytes(assetBytes)
    }
    return dest
}

// The localized dictionary name shown for a hit (:core's DictionaryHit
// carries only the source code).
@Composable
private fun DictionarySource.localizedTitle(): String = stringResource(
    when (this) {
        DictionarySource.SPALDING -> R.string.spalding_result_title
        DictionarySource.TUSAALANGA -> R.string.tusaalanga_result_title
    },
)

// Holds everything about the current word lookup that should survive
// navigating to Explanation and back -- MainActivity.kt switches between
// WordLookupScreen and ExplanationScreen with a plain `when` (no
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
    // Note: lenient morphological analysis is NOT here -- it's a persisted
    // setting (AppSettings.loadLenientAnalysis), loaded fresh in
    // WordLookupScreen the same way uiLanguage/displayScript/apiKey are, not
    // per-session UI state.
    var showSettings by mutableStateOf(false)
    var decomposeState by mutableStateOf<DecompositionOutcome>(DecompositionOutcome.Idle)
    var multiWordChoices by mutableStateOf<List<String>?>(null)
    var dictionaryResults by mutableStateOf<List<DictionaryHit>>(emptyList())
    var shorterWordDictionaryResults by mutableStateOf<List<ShorterWordDictionaryHit>>(emptyList())
    var dictionaryLoading by mutableStateOf(false)
    var dictionaryFetchError by mutableStateOf<String?>(null)
    var hansardResult by mutableStateOf<HansardExamplesOutcome?>(null)
    var hansardLoading by mutableStateOf(false)
    var lastSearchedWord by mutableStateOf("")
    // Alongside lastSearchedWord -- Guess Meaning needs both to build its
    // cache key (see GuessMeaningCacheKey) even when decomposeState isn't
    // Success (whose own word/lenient fields would otherwise cover this),
    // e.g. when the analyzer failed or timed out on a word with no exact
    // dictionary hit either -- see findWord()'s own comment on why Guess
    // Meaning shouldn't require a successful decomposition to be offered.
    var lastSearchedLenient by mutableStateOf(false)
    // The script lastSearchedWord was typed/detected in -- captured once in
    // findWord() (TransCoder.textScript(wordToAnalyze)) rather than
    // re-detected wherever lastSearchedWord is displayed, so every "not
    // found" / "found for a shorter word" message converts it to the user's
    // chosen displayScript consistently instead of showing it in whatever
    // script it happened to be typed in -- see findWord() and Alain's report
    // of the two words in that message appearing in different scripts.
    var lastSearchedWordScript by mutableStateOf(Script.ROMAN)
}

// internal (not private/public): takes an internal WordLookupScreenState
// parameter (see that class's header comment), so this can't be public.
@Composable
internal fun WordLookupScreen(
    screenState: WordLookupScreenState = remember { WordLookupScreenState() },
    // Fires when "Expliquer" is tapped inside GuessMeaningSection below --
    // opens ExplanationScreen.kt with the attempt's key and a frozen
    // WordInfoSnapshot of everything this screen currently shows about the
    // word, per Alain's request. Never navigates away for the rest of the
    // Guess Meaning flow (button/spinner/candidates all stay inline, see
    // GuessMeaningInline.kt).
    onOpenExplanation: (GuessMeaningConversationKey, WordInfoSnapshot) -> Unit = { _, _ -> },
    // Opens the Morpheme Dictionary screen (see MorphemeDictionaryScreen.kt).
    onOpenMorphemeDictionary: () -> Unit = {},
    // Shared with MainActivity (not copied) -- ExplanationScreen reads/
    // writes the same maps (e.g. after a debug-only prompt resubmission), so
    // this screen picks up the result without a network call of its own.
    guessMeaningConversations: SnapshotStateMap<GuessMeaningConversationKey, List<ChatMessage>> = mutableStateMapOf(),
    useLocalModel: Boolean = false,
    guessMeaningModelStats: SnapshotStateMap<String, AggregatedBackendStats> = mutableStateMapOf(),
) {
    val scope = rememberCoroutineScope()
    val baseContext = LocalContext.current

    // Delegates to screenState's properties (by property reference, not by
    // value) -- every read/write below still looks like a local `var`, but
    // actually lives in screenState, so it survives this composable being
    // torn down and recreated. See WordLookupScreenState's header comment.
    var word by screenState::word
    var uiLanguage by remember { mutableStateOf(AppSettings.loadLanguage(baseContext)) }
    var displayScript by remember { mutableStateOf(AppSettings.loadDisplayScript(baseContext)) }
    // Persisted setting (default on), edited from the Settings dialog's
    // "Morphological analysis" section -- loaded eagerly like the two above,
    // not held in screenState (see that class's note).
    var lenient by remember { mutableStateOf(AppSettings.loadLenientAnalysis(baseContext)) }
    // Same section of Settings. Default UQAILAUT (Benoit's R2L analyzer).
    var analyzerChoice by remember { mutableStateOf(AppSettings.loadAnalyzerChoice(baseContext)) }
    // The FST transducer is an app asset; materialize it to a file once, and
    // remember whether it actually loads. If FST is selected but this failed,
    // findWord() shows R.string.error_fst_not_available instead of analysing.
    val fstTransducerFile = remember { runCatching { fstTransducerFile(baseContext) }.getOrNull() }
    val fstAvailable = remember(fstTransducerFile) {
        fstTransducerFile != null && MorphologicalAnalyzer_FST.isAvailable(fstTransducerFile)
    }
    val useFst = analyzerChoice == AnalyzerChoice.FST && fstAvailable
    val analyzer: MorphologicalAnalyzer = remember(useFst) {
        if (useFst) MorphologicalAnalyzer_FST(fstTransducerFile!!) else MorphologicalAnalyzer_R2L()
    }
    DisposableEffect(analyzer) { onDispose { runCatching { analyzer.close() } } }
    // All the lookup logic -- run the analyzer, query Spalding + Tusaalanga,
    // conditionally search the Hansard corpus, decide what each result gates
    // -- lives in :core now (see WordLookup / doc/dev/plans/word-lookup-to-core.md).
    // This screen just feeds it the word + prefs and paints the results it
    // streams back. The analyzer instance stays owned here (the
    // DisposableEffect above closes it); WordLookup only borrows it.
    val hansardExampleSource = remember(baseContext) { NunavutHansardLocalIndex.asExampleSource(baseContext) }
    val wordLookup = remember(analyzer, fstAvailable) {
        WordLookup(
            hansardExamples = hansardExampleSource,
            analyzerFor = { choice -> if (choice == AnalyzerChoice.FST && !fstAvailable) null else analyzer },
        )
    }
    var lookupJob by remember { mutableStateOf<Job?>(null) }
    // The user's own Claude.ai API key, per Alain's request -- replaces the
    // developer-only key baked into the build (see AppSettings.loadApiKey).
    // Loaded eagerly, like uiLanguage/displayScript above -- GuessMeaningSection
    // needs to know the real, current key status the first time its button is
    // tapped, not just after Settings has been opened once this session (a
    // deferred load previously lived here, but it made GuessMeaningSection's
    // own copy of the key go stale after Settings saved a new one -- see that
    // composable's apiKey parameter comment).
    var apiKey by remember { mutableStateOf(AppSettings.loadApiKey(baseContext)) }
    // Fed by GuessMeaningSection's onCandidatesChanged below -- lets the
    // Hansard section highlight the AI's proposed meanings inside the
    // bilingual examples, per Alain's request. Plain local state (not
    // screenState): resets to empty on recomposition after navigating away
    // and back, which is fine since GuessMeaningSection reports it again
    // right away from its own cached conversation.
    var guessMeaningCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSettings by screenState::showSettings
    var state by screenState::decomposeState
    var multiWordChoices by screenState::multiWordChoices
    // Every dictionary source is checked, and Guess Meaning only offered once all of
    // them have answered -- see dictionaryLoading below. Spalding is a local/instant
    // lookup; Tusaalanga is a real network fetch (no distribution rights for its
    // content, must stay live -- see TusaalangaFetcher.kt), so this list fills in over
    // two separate updates, not one.
    var dictionaryResults by screenState::dictionaryResults
    var shorterWordDictionaryResults by screenState::shorterWordDictionaryResults
    var dictionaryLoading by screenState::dictionaryLoading
    // Tusaalanga's fetch failure (network/HTTP error, not just "word not found") --
    // debug-build-only, same purpose as the system-prompt inspection panel in
    // ExplanationScreen.kt: alerts a developer the fetcher broke, not end-user UX.
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
    var lastSearchedLenient by screenState::lastSearchedLenient
    var lastSearchedWordScript by screenState::lastSearchedWordScript

    suspend fun runHansardSearch(searchWord: String) {
        hansardLoading = true
        hansardResult = hansardExampleSource.examplesFor(searchWord)
        hansardLoading = false
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun findWord() {
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

        // enteredScript captured here (not read off a DecompositionOutcome.Success,
        // which won't exist if decomposition fails) so dictionary words can still be
        // displayed in the user's chosen script even without a decomposition, and so
        // every "not found" / "found for a shorter word" message converts
        // lastSearchedWord to displayScript consistently -- without this the two
        // words in that message could show up in different scripts (Alain's report).
        val enteredScript = TransCoder.textScript(wordToAnalyze)
        lastSearchedWord = wordToAnalyze
        lastSearchedLenient = lenientAtSearch
        lastSearchedWordScript = enteredScript
        // Otherwise a previous word's Guess Meaning candidates could keep
        // highlighting this new word's Hansard examples until GuessMeaningSection
        // happens to be composed again and report its own.
        guessMeaningCandidates = emptyList()

        // Spalding is embedded and instant, so its hit is shown the moment
        // findWord() returns rather than after the lookup flow's first turn --
        // DisplayScriptSwitchUiTest.kt relies on that. WordLookup below runs the
        // same Spalding lookup as part of its self-contained flow (so a headless
        // caller needs nothing pre-computed); its first emission overwrites these
        // with the identical values. A shorter-word hit is deliberately kept out
        // of dictionaryResults -- it does not count as "a definition was found".
        val spaldingHit = SpaldingDictionary.lookup(wordToAnalyze)?.let { entry ->
            DictionaryHit(DictionarySource.SPALDING, entry.word, entry.meaning, enteredScript)
        }
        val spaldingShorterHit = if (spaldingHit == null) {
            SpaldingDictionary.lookupLongestPrefix(wordToAnalyze)?.let { (_, entry) ->
                ShorterWordDictionaryHit(
                    originalWord = wordToAnalyze,
                    hit = DictionaryHit(DictionarySource.SPALDING, entry.word, entry.meaning, enteredScript),
                )
            }
        } else {
            null
        }
        dictionaryResults = listOfNotNull(spaldingHit)
        shorterWordDictionaryResults = listOfNotNull(spaldingShorterHit)
        dictionaryFetchError = null
        dictionaryLoading = true
        hansardResult = null
        hansardLoading = false
        state = DecompositionOutcome.Loading

        // Everything else -- Tusaalanga, the decomposition (in parallel), the
        // conditional Hansard search, and which result gates what -- is
        // WordLookup's job now. Each emission is a fuller snapshot of the same
        // lookup; map it onto this screen's state fields.
        lookupJob?.cancel()
        lookupJob = scope.launch {
            wordLookup.lookup(wordToAnalyze, WordLookupPrefs(analyzerChoice, lenientAtSearch)).collect { result ->
                state = result.decomposition
                dictionaryResults = result.dictionaryHits
                shorterWordDictionaryResults = result.shorterWordHits
                dictionaryLoading = !result.dictionariesSettled
                dictionaryFetchError = result.tusaalangaFetchError
                when (val hansard = result.hansard) {
                    HansardLookupState.NotSearched -> {
                        hansardResult = null
                        hansardLoading = false
                    }
                    HansardLookupState.Searching -> {
                        hansardResult = null
                        hansardLoading = true
                    }
                    is HansardLookupState.Done -> {
                        hansardResult = hansard.outcome
                        hansardLoading = false
                    }
                }
            }
        }
    }

    fun selectWord(chosen: String) {
        multiWordChoices = null
        word = chosen
        findWord()
    }

    fun loadMore(previous: DecompositionOutcome.Success) {
        state = DecompositionOutcome.Loading
        scope.launch {
            state = wordLookup.decompose(
                previous.word,
                WordLookupPrefs(analyzerChoice, previous.lenient),
                expandAll = true,
            )
        }
    }

    LocalizedContent(uiLanguage) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // One continuous scroll for the whole screen, per Alain's request --
        // previously each result section (dictionary/Hansard/decompositions)
        // scrolled independently in its own bounded box, which meant a swipe
        // starting outside that box's bounds did nothing.
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            // Resolved here, not deeper down, so the dictionary names follow
            // uiLanguage -- handed to guessMeaningSeedPrompt() via
            // GuessMeaningSeedLabels below. (The on-screen dictionary sections
            // resolve their own titles from DictionarySource.localizedTitle().)
            val spaldingResultTitle = stringResource(R.string.spalding_result_title)
            val tusaalangaResultTitle = stringResource(R.string.tusaalanga_result_title)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onOpenMorphemeDictionary,
                    modifier = Modifier.testTag("search_morpheme_button"),
                ) {
                    Text(stringResource(R.string.search_morpheme_button))
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
                    lenientAnalysis = lenient,
                    onLenientAnalysisChanged = {
                        lenient = it
                        AppSettings.saveLenientAnalysis(baseContext, it)
                    },
                    analyzerChoice = analyzerChoice,
                    onAnalyzerChoiceSelected = {
                        analyzerChoice = it
                        AppSettings.saveAnalyzerChoice(baseContext, it)
                    },
                    apiKey = apiKey,
                    onApiKeyChanged = {
                        apiKey = it
                        AppSettings.saveApiKey(baseContext, it)
                    },
                    onDismiss = { showSettings = false },
                )
            }

            multiWordChoices?.let { choices ->
                MultiWordChoiceDialog(
                    uiLanguage = uiLanguage,
                    words = choices,
                    onSelect = { selectWord(it) },
                    onDismiss = { multiWordChoices = null },
                )
            }

            Text(
                text = stringResource(R.string.screen_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Wrapped in RealAndroidContext, not just composed directly under
            // this screen's own LocalizedContent wrap -- see that
            // composable's header comment: pasting into a plain
            // LocalizedContent-wrapped field crashed on Alain's Samsung
            // phone. wordLabel is resolved *before* entering the wrap so it
            // still reflects uiLanguage.
            val wordLabel = localizedStringResource(uiLanguage, R.string.word_label)
            RealAndroidContext {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text(wordLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { findWord() }),
                    modifier = Modifier.fillMaxWidth().testTag("word_input"),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Makes every Text from here to the end of the screen
            // copy-pasteable, per Alain's request -- covers this screen's
            // own content, but NOT the dialogs (SettingsDialog/
            // MultiWordChoiceDialog/MorphemeDetailDialog), which each render
            // into their own separate window (AlertDialog internally uses
            // Dialog/Popup) and so need their own SelectionContainer -- this
            // one doesn't reach across that boundary. Starts after
            // word_input above, not from the top of the screen -- a
            // SelectionContainer wrapping an OutlinedTextField is a
            // known-risky combination in Compose (the two fight over the
            // same selection/gesture handling), and crashed on Alain's
            // Samsung phone (see RealAndroidContext's own header comment for
            // the sibling paste crash this same class of bug caused).
            SelectionContainer {
            Column {
            Button(
                onClick = { findWord() },
                enabled = word.isNotBlank() && state != DecompositionOutcome.Loading,
                modifier = Modifier.fillMaxWidth().testTag("find_word_button"),
            ) {
                Text(stringResource(R.string.find_word_button))
            }

            // Header for the whole result "fiche": the searched word in both
            // scripts, the user's display-script choice first.
            if (lastSearchedWord.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = displayFormBothScripts(lastSearchedWord, displayScript, lastSearchedWordScript),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("word_header"),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dictionary results come first, per Alain's request -- a word can be a
            // real dictionary entry even when the analyzer below finds no
            // decomposition for it (or vice versa); both are shown, independently.
            val guessMeaningState = state
            // Title for the first of the results area's three sections. Shown
            // whenever that area has anything in it -- a dictionary hit, the
            // Guess Meaning flow, a shorter-word hit, or the "checking
            // dictionaries" spinner.
            val showDefinitionSection = dictionaryResults.isNotEmpty() ||
                shouldOfferGuessMeaning(dictionaryResults, dictionaryLoading, lastSearchedWord) ||
                shorterWordDictionaryResults.isNotEmpty() ||
                dictionaryLoading
            if (showDefinitionSection) {
                SectionHeading(stringResource(R.string.section_title_definition_meaning))
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (dictionaryResults.isNotEmpty()) {
                DictionaryResultSection(dictionaryResults, displayScript)
                Spacer(modifier = Modifier.height(16.dp))
            } else if (shouldOfferGuessMeaning(dictionaryResults, dictionaryLoading, lastSearchedWord)) {
                // Guess Meaning is shown at exactly the spot a definition would
                // have appeared, per Alain's request -- the AI's candidate
                // meanings serve the same role a dictionary hit would have,
                // once the exact word has come up empty in every dictionary
                // source (see "Court-circuit sur correspondance exacte" in the
                // plan doc; dictionaryLoading gates this so the button doesn't
                // flash on screen before Tusaalanga's async answer has had a
                // chance to arrive). A shorter/related-word hit does NOT
                // suppress this -- see ShorterWordDictionaryResult's own
                // comment -- it's included in the seed instead (converted to
                // syllabic, see guessMeaningSeedPrompt()). Deliberately does
                // NOT also require a successful decomposition (an earlier
                // version did): the analyzer failing outright is exactly the
                // kind of word Guess Meaning exists for -- Alain found a real
                // case (a shorter-word dictionary hit, but the exact word
                // didn't decompose) where that requirement silently hid the
                // button entirely. guessMeaningDecompositions below is empty
                // for that case, and guessMeaningSeedPrompt() already omits
                // its decomposition section entirely when given an empty list.
                val guessMeaningDecompositions = (guessMeaningState as? DecompositionOutcome.Success)?.decompositions ?: emptyList()
                val guessMeaningSeedLabels = GuessMeaningSeedLabels(
                    questionTemplate = stringResource(R.string.guess_meaning_seed_question_template),
                    decompositionSingleIntro = stringResource(R.string.guess_meaning_seed_decomposition_single_intro),
                    decompositionMultipleIntro = stringResource(R.string.guess_meaning_seed_decomposition_multiple_intro),
                    decompositionNumberTemplate = stringResource(R.string.guess_meaning_seed_decomposition_number),
                    unknownMorpheme = stringResource(R.string.guess_meaning_seed_unknown_morpheme),
                    dictionarySourceNames = mapOf(
                        DictionarySource.SPALDING to spaldingResultTitle,
                        DictionarySource.TUSAALANGA to tusaalangaResultTitle,
                    ),
                    shorterWordDictionaryIntroTemplate = stringResource(R.string.guess_meaning_seed_shorter_word_intro),
                    hansardExamplesExactIntroTemplate = stringResource(R.string.guess_meaning_seed_hansard_exact_intro),
                    hansardExamplesShorterWordIntroTemplate = stringResource(R.string.guess_meaning_seed_hansard_shorter_word_intro),
                )
                // Whatever's already been found by the time this button is
                // clicked -- the Hansard search runs independently (see
                // findWord()) and may still be loading, or may have found
                // nothing, in which case this is just an empty list and
                // guessMeaningSeedPrompt() omits that section entirely.
                // Found and FoundForShorterWord are both included -- a
                // shorter-word Hansard match is still real example text,
                // even though it doesn't gate this button's own visibility --
                // but see hansardExamplesAreExactMatch below, which tells
                // guessMeaningSeedPrompt() which of the two it is, so it can
                // word the "verify against these" instruction correctly.
                val hansardExamplesForSeed = when (val hansard = hansardResult) {
                    is HansardExamplesOutcome.Found -> hansard.examples
                    is HansardExamplesOutcome.FoundForShorterWord -> hansard.examples
                    else -> emptyList()
                }.take(GUESS_MEANING_HANSARD_EXAMPLES)
                val guessMeaningWordKey = GuessMeaningCacheKey(lastSearchedWord, lastSearchedLenient)
                val guessMeaningSeed = guessMeaningSeedPrompt(
                    lastSearchedWord,
                    guessMeaningDecompositions,
                    hansardExamplesForSeed,
                    hansardResult is HansardExamplesOutcome.Found,
                    shorterWordDictionaryResults,
                    uiLanguage == AppLanguage.FRENCH,
                    guessMeaningSeedLabels,
                )
                // States explicitly that nothing was found, per Alain's
                // request, rather than jumping straight to the Guess Meaning
                // button with no explanation for why it's there -- only when
                // truly nothing was found, exact or shorter-word. When a
                // shorter-word hit exists, ShorterWordDictionarySection below
                // already explains what was found instead of the exact word.
                if (shorterWordDictionaryResults.isEmpty()) {
                    Text(stringResource(R.string.no_definition_found))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // Renders the button (or, once tapped, the spinner/result) in
                // place -- see GuessMeaningInline.kt. "Expliquer" opens
                // ExplanationScreen with a snapshot of everything below (see
                // WordInfoSnapshot) -- built here, not inside
                // GuessMeaningSection, since that's the only place with
                // access to it.
                GuessMeaningSection(
                    wordCacheKey = guessMeaningWordKey,
                    seed = guessMeaningSeed,
                    uiLanguage = uiLanguage,
                    conversations = guessMeaningConversations,
                    useLocalModel = useLocalModel,
                    modelStats = guessMeaningModelStats,
                    onExplain = { conversationKey ->
                        onOpenExplanation(
                            conversationKey,
                            WordInfoSnapshot(
                                decomposeState = state,
                                dictionaryResults = dictionaryResults,
                                shorterWordDictionaryResults = shorterWordDictionaryResults,
                                dictionaryLoading = dictionaryLoading,
                                hansardResult = hansardResult,
                                hansardLoading = hansardLoading,
                                lastSearchedWord = lastSearchedWord,
                                lastSearchedWordScript = lastSearchedWordScript,
                                displayScript = displayScript,
                                uiLanguage = uiLanguage,
                            ),
                        )
                    },
                    onCandidatesChanged = { guessMeaningCandidates = it },
                    onNeedApiKey = { showSettings = true },
                    apiKey = apiKey,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            // Shown regardless of dictionaryResults -- a shorter-word hit can
            // coexist with "nothing found for the exact word" (it doesn't
            // count as a real hit, see ShorterWordDictionaryResult).
            if (shorterWordDictionaryResults.isNotEmpty()) {
                ShorterWordDictionarySection(shorterWordDictionaryResults, displayScript)
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (dictionaryLoading) {
                Text(stringResource(R.string.dictionary_checking_label), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }
            // Debug-build-only: alerts a developer that the Tusaalanga fetch itself
            // broke (network/HTTP error), not that the word simply wasn't found --
            // not meant for a normal user's build, same reasoning as the debug-only
            // system-prompt panel in ExplanationScreen.kt.
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
            // Alain's request. Titled once a search has produced any
            // decomposition outcome (a spinner, an error, or results);
            // DecompositionSection's own collapsible header stays, as the
            // per-word count line under this title.
            if (state !is DecompositionOutcome.Idle) {
                SectionHeading(stringResource(R.string.section_title_decomposition))
                Spacer(modifier = Modifier.height(8.dp))
            }
            when (val s = state) {
                is DecompositionOutcome.Idle -> {}
                is DecompositionOutcome.Loading -> CircularProgressIndicator()
                is DecompositionOutcome.Failure -> Text(
                    text = when (val reason = s.reason) {
                        is FailureReason.Timeout -> stringResource(R.string.error_timeout)
                        is FailureReason.AnalysisError ->
                            stringResource(R.string.error_analysis, reason.detail ?: "")
                        is FailureReason.FstNotAvailable -> stringResource(R.string.error_fst_not_available)
                    },
                    color = MaterialTheme.colorScheme.error,
                )
                is DecompositionOutcome.Success -> {
                    DecompositionSection(
                        success = s,
                        preferFrench = uiLanguage == AppLanguage.FRENCH,
                        displayScript = displayScript,
                        onLoadMore = { loadMore(s) },
                    )
                }
            }
            if (state is DecompositionOutcome.Success) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Title for the third section -- shown whenever the Hansard area
            // has anything: the on-demand "find examples" button, the search
            // spinner, or a result/notice.
            val showExamplesSection = hansardLoading || hansardResult != null ||
                (dictionaryResults.isNotEmpty() && !dictionaryLoading)
            if (showExamplesSection) {
                SectionHeading(stringResource(R.string.section_title_examples))
                Spacer(modifier = Modifier.height(8.dp))
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
                    is HansardExamplesOutcome.Found -> {
                        HansardExamplesSection(result.word, result.examples, displayScript, guessMeaningCandidates)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    is HansardExamplesOutcome.FoundForShorterWord -> {
                        ShorterWordNotice(
                            original = displayForm(lastSearchedWord, displayScript, lastSearchedWordScript),
                            matched = displayForm(result.word, displayScript, Script.SYLLABIC),
                            prefixText = stringResource(R.string.hansard_shorter_word_prefix),
                            middleText = stringResource(R.string.hansard_shorter_word_middle),
                            suffixText = stringResource(R.string.hansard_shorter_word_suffix),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HansardExamplesSection(result.word, result.examples, displayScript, guessMeaningCandidates)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    HansardExamplesOutcome.NotFound -> {
                        Text(
                            stringResource(
                                R.string.hansard_no_examples,
                                displayForm(lastSearchedWord, displayScript, lastSearchedWordScript),
                            ),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    // Shown for every build, not just debug -- unlike
                    // dictionaryFetchError above, a real end user (not just a
                    // dev) can genuinely hit these: the DB just hasn't been
                    // downloaded yet, or the app was updated past the schema
                    // version they downloaded earlier. Same download flow
                    // fixes both -- see HansardDownloadSection below.
                    HansardExamplesOutcome.IndexMissing, is HansardExamplesOutcome.IndexVersionMismatch -> {
                        HansardDownloadSection(onDownloaded = { runHansardSearch(lastSearchedWord) })
                        if (BuildConfig.DEBUG) {
                            val debugDetail = if (result is HansardExamplesOutcome.IndexVersionMismatch) {
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
            }
            }
        }
    }
    }
}

// One consistent look for every section heading -- both the Settings dialog
// and the three top-level sections of the results area (definition/meaning,
// decomposition, bilingual examples): bold and a step larger than the
// section's own body text, so the sections read as distinct blocks.
@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
// internal (not private): also opened from MorphemeDictionaryScreen's header,
// which shares the same Settings affordance as this screen.
internal fun SettingsDialog(
    uiLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    displayScript: DisplayScript,
    onDisplayScriptSelected: (DisplayScript) -> Unit,
    lenientAnalysis: Boolean,
    onLenientAnalysisChanged: (Boolean) -> Unit,
    analyzerChoice: AnalyzerChoice,
    onAnalyzerChoiceSelected: (AnalyzerChoice) -> Unit,
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    LocalizedAlertDialog(
        uiLanguage = uiLanguage,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("settings_dialog_close_button")) {
                Text(stringResource(R.string.close_button))
            }
        },
        title = { Text(stringResource(R.string.settings_button)) },
        text = {
            // No single SelectionContainer wrapping this whole Column, unlike
            // WordLookupScreen's own body -- a TextField has its own built-in
            // text selection, and nesting one inside a SelectionContainer is
            // a known-risky combination in Compose (the two fight over the
            // same selection/gesture handling). Alain's second Samsung crash
            // -- select-all then backspace in the API key field -- matches
            // that exactly, so the static text below gets its own, narrower
            // SelectionContainer wraps (for copy/paste, per Alain's original
            // request) instead, leaving the OutlinedTextField outside any of
            // them.
            Column {
                SelectionContainer {
                Column {
                SectionHeading(stringResource(R.string.ui_language_label))
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

                SectionHeading(stringResource(R.string.display_script_label))
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

                Spacer(modifier = Modifier.height(12.dp))

                // Morphological-analysis options. Just the one toggle for now
                // (lenient analysis, moved here from the main screen per
                // Alain's request); its own section so more analyzer options
                // have an obvious home later.
                SectionHeading(stringResource(R.string.settings_morphology_section))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.lenient_switch_label))
                    // scale(): Material3's Switch has no size parameter and
                    // its default is visually heavy next to this dialog's
                    // text -- Alain asked for it smaller.
                    Switch(
                        checked = lenientAnalysis,
                        onCheckedChange = onLenientAnalysisChanged,
                        modifier = Modifier.scale(0.8f).testTag("lenient_analysis_switch"),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Same visual treatment as the display-script choice above.
                Text(
                    text = stringResource(R.string.settings_choose_analyzer_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Column {
                    val analyzerLabelFor = mapOf(
                        AnalyzerChoice.UQAILAUT to R.string.analyzer_choice_uqailaut,
                        AnalyzerChoice.FST to R.string.analyzer_choice_fst,
                    )
                    AnalyzerChoice.entries.forEach { choice ->
                        TextButton(
                            onClick = { onAnalyzerChoiceSelected(choice) },
                            modifier = Modifier.testTag("analyzer_choice_${choice.name.lowercase()}"),
                        ) {
                            Text(
                                text = stringResource(analyzerLabelFor.getValue(choice)),
                                fontWeight = if (choice == analyzerChoice) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Per Alain's request: Guess Meaning now uses the user's own
                // Claude.ai key instead of one baked into the build (see
                // AppSettings.loadApiKey) -- GuessMeaningSection routes here
                // (see its onNeedApiKey) the first time the button is tapped
                // with none set yet, which is why this message is phrased as
                // an explanation, not just a field label.
                SectionHeading(stringResource(R.string.settings_ai_section))
                Text(
                    text = stringResource(R.string.settings_api_key_message),
                    style = MaterialTheme.typography.labelMedium,
                )
                }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Masked by default (this is a secret), with a toggle to reveal
                // it in cleartext -- per Alain's request, so he can actually see
                // what got typed/pasted when a paste seems not to have taken.
                // No Material "eye" icon here: Icons.Filled.Visibility/
                // VisibilityOff live in material-icons-extended, which this
                // project deliberately doesn't depend on (see the emoji-glyph
                // buttons elsewhere on this screen, e.g. the settings gear).
                var apiKeyVisible by remember { mutableStateOf(false) }
                // RealAndroidContext, not composed directly under this
                // dialog's own LocalizedContent wrap (see LocalizedAlertDialog)
                // -- see RealAndroidContext's header comment: this is the
                // exact field Alain found crashed every time on his Samsung
                // phone when long-pressing to paste a key.
                val apiKeyLabel = localizedStringResource(uiLanguage, R.string.settings_api_key_label)
                RealAndroidContext {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChanged,
                        label = { Text(apiKeyLabel) },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(
                                onClick = { apiKeyVisible = !apiKeyVisible },
                                modifier = Modifier.testTag("api_key_visibility_toggle"),
                            ) {
                                Text(if (apiKeyVisible) "🙈" else "👁")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                Text(
                    text = stringResource(R.string.settings_api_key_hint),
                    style = MaterialTheme.typography.labelSmall,
                )
                }
            }
        },
    )
}

@Composable
private fun MultiWordChoiceDialog(
    uiLanguage: AppLanguage,
    words: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    LocalizedAlertDialog(
        uiLanguage = uiLanguage,
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_button)) } },
        title = { Text(stringResource(R.string.multi_word_dialog_title)) },
        text = {
            SelectionContainer {
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
            }
        },
    )
}

// internal (not private): also called from ExplanationScreen.kt's
// WordInfoCard (read-only "fiche" pane), same reasoning as
// ShorterWordDictionarySection/DecompositionSection/HansardExamplesSection
// below.
@Composable
internal fun DictionaryResultSection(results: List<DictionaryHit>, displayScript: DisplayScript) {
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
                text = result.source.localizedTitle(),
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

// internal (not private): see DictionaryResultSection's own comment above.
@Composable
internal fun ShorterWordDictionarySection(results: List<ShorterWordDictionaryHit>, displayScript: DisplayScript) {
    // Same bounded-scroll treatment as DictionaryResultSection, and for the
    // same reason (a Spalding entry can run 7000+ characters) -- not folded
    // into the continuous page.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        results.forEachIndexed { index, shorterResult ->
            val result = shorterResult.hit
            ShorterWordNotice(
                original = displayForm(shorterResult.originalWord, displayScript, result.enteredScript),
                matched = displayForm(result.word, displayScript, result.enteredScript),
                prefixText = stringResource(R.string.dictionary_shorter_word_prefix),
                middleText = stringResource(R.string.dictionary_shorter_word_middle),
                suffixText = stringResource(R.string.dictionary_shorter_word_suffix),
            )
            Text(
                text = result.source.localizedTitle(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = displayForm(result.word, displayScript, result.enteredScript),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("shorter_word_dictionary_result_word_$index"),
            )
            Text(text = result.meaning)
            if (index != results.lastIndex) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// Shared by the Hansard and dictionary "not found for X, but found for
// [shorter] Y" messages -- Y is highlighted within X (it's the prefix of X
// that matched, see PrefixFallback.kt), per Alain's request, so it's
// visually obvious which part of the original word the shorter result
// covers. Built from three separate string-resource fragments (not one
// %1$s/%2$s-templated string) specifically so X can carry a styled
// highlight span -- a plain templated string can't have part of an
// interpolated argument styled differently from the rest.
// internal (not private): also called directly from ExplanationScreen.kt's
// WordInfoCard, for the same "found for a shorter word" Hansard case
// WordLookupScreen itself renders below (not wrapped in
// HansardExamplesSection, so it needs its own reuse).
@Composable
internal fun ShorterWordNotice(original: String, matched: String, prefixText: String, middleText: String, suffixText: String) {
    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    val annotated = remember(original, matched, prefixText, middleText, suffixText, highlightColor) {
        buildAnnotatedString {
            append(prefixText)
            val originalStart = length
            append(original)
            highlightRange(original, matched)?.let { range ->
                addStyle(
                    SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold),
                    originalStart + range.first,
                    originalStart + range.last + 1,
                )
            }
            append(middleText)
            append(matched)
            append(suffixText)
        }
    }
    Text(annotated)
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
        // elsewhere in this screen, e.g. "⚙ "/"✨ ").
        Text(text = if (expanded) "▾" else "▸", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

// internal (not private): also called from ExplanationScreen.kt's
// WordInfoCard, with showLoadMore = false (that pane is a read-only
// snapshot -- see WordInfoSnapshot's header comment -- so a "More" button
// wired to nothing would be misleading).
@Composable
internal fun DecompositionSection(
    success: DecompositionOutcome.Success,
    preferFrench: Boolean,
    displayScript: DisplayScript,
    onLoadMore: () -> Unit,
    showLoadMore: Boolean = true,
) {
    if (success.decompositions.isEmpty()) {
        Text(
            stringResource(
                R.string.no_decompositions,
                displayForm(success.word, displayScript, success.enteredScript),
            ),
        )
        return
    }
    // Keyed on the word so a fresh search starts expanded again, rather than
    // carrying over whatever collapse state the previous word's section was
    // left in.
    var expanded by remember(success.word) { mutableStateOf(true) }
    CollapsibleSectionHeader(
        text = if (success.hasMore) {
            stringResource(R.string.decompositions_header_partial, success.word, WordLookup.PREVIEW_LIMIT)
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
    if (success.hasMore && showLoadMore) {
        TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.more_button))
        }
    }
}

// internal (not private): also called from ExplanationScreen.kt's
// WordInfoCard. Its own "More" button just reveals more of the already-
// fetched `examples` (no network call, see below), so it's left fully
// functional even in that read-only pane.
@Composable
internal fun HansardExamplesSection(
    word: String,
    examples: List<BilingualExample>,
    displayScript: DisplayScript,
    // The AI's candidate meanings for this word, if Guess Meaning has found
    // any yet -- highlighted inside each example's English sentence, per
    // Alain's request, so it's visually obvious whether (and where) each
    // guess is actually backed by the bilingual example it was cross-
    // referenced against (see the "make sure each meaning..." instruction
    // in chat_system_prompt). Empty until then, or if Guess Meaning was
    // never offered for this word (a real dictionary hit found something).
    highlightMeanings: List<String> = emptyList(),
) {
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
        // Same highlighting mechanics as the Inuktitut sentence above, but
        // against however many candidate meanings are currently proposed --
        // more than one can legitimately highlight in the same sentence
        // (e.g. "house" and "home" both matching).
        val annotatedEnglish = remember(example.english, highlightMeanings, highlightColor) {
            buildAnnotatedString {
                append(example.english)
                highlightRanges(example.english, highlightMeanings).forEach { range ->
                    addStyle(
                        SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold),
                        range.first,
                        range.last + 1,
                    )
                }
            }
        }
        Text(
            text = annotatedEnglish,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("hansard_example_en_$index"),
        )
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
    // The whole attribution line is a link to the corpus's official NRC
    // Digital Repository page (see NunavutHansardLocalIndex.NRC_CORPUS_URL) --
    // Text renders LinkAnnotation spans as tappable, opening the system
    // browser, no onClick wiring of our own needed.
    val attributionLinkColor = MaterialTheme.colorScheme.primary
    Text(
        text = buildAnnotatedString {
            withLink(
                LinkAnnotation.Url(
                    url = NunavutHansardLocalIndex.NRC_CORPUS_URL,
                    styles = TextLinkStyles(
                        SpanStyle(color = attributionLinkColor, textDecoration = TextDecoration.Underline),
                    ),
                ),
            ) {
                append(stringResource(R.string.hansard_attribution))
            }
        },
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
            val destination = NunavutHansardLocalIndex.dbFile(context) ?: run {
                progress = HansardDownloadProgress.Failed("No external files directory available on this device")
                return@launch
            }
            var last: HansardDownloadProgress? = null
            HansardIndexDownloader.downloadTo(destination).collect {
                progress = it
                last = it
            }
            if (last is HansardDownloadProgress.Done) {
                // The index caches "Missing"; drop that so it re-opens the
                // file that just landed.
                NunavutHansardLocalIndex.invalidateCache()
                onDownloaded()
            }
        }
    }

    when (val p = progress) {
        null, is HansardDownloadProgress.Failed -> {
            // Plain-language explanation of why there are no examples yet and
            // what to do about it, per Alain's request -- replaces relying on
            // the bare (debug-only) "index not found on this device" line to
            // convey it.
            Text(
                text = stringResource(R.string.hansard_install_prompt),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
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
            // Derived from preferFrench, already the only language signal
            // this deep in the call chain (DecompositionSection ->
            // MorphemeTable), rather than threading a whole new uiLanguage
            // parameter down through both.
            uiLanguage = if (preferFrench) AppLanguage.FRENCH else AppLanguage.ENGLISH,
            row = row,
            displayScript = displayScript,
            enteredScript = enteredScript,
            onDismiss = { selectedRow = null },
        )
    }
}

@Composable
private fun MorphemeDetailDialog(
    uiLanguage: AppLanguage,
    row: MorphemeRow,
    displayScript: DisplayScript,
    enteredScript: Script,
    onDismiss: () -> Unit,
) {
    val morpheme = row.fullRecord
    LocalizedAlertDialog(
        uiLanguage = uiLanguage,
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_button)) } },
        title = { Text(displayForm(row.surfaceForm, displayScript, enteredScript)) },
        text = {
            SelectionContainer {
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
