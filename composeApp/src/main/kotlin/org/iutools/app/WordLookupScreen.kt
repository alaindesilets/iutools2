package org.iutools.app

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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

private const val PREVIEW_LIMIT = 3

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
private data class DictionaryLookupResult(
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

private sealed interface FailureReason {
    data object Timeout : FailureReason
    data class AnalysisError(val detail: String?) : FailureReason
}

private sealed interface DecomposeState {
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

@Composable
fun WordLookupScreen(onOpenGuessMeaning: (GuessMeaningCacheKey, String) -> Unit = { _, _ -> }) {
    val analyzer = remember { MorphologicalAnalyzer_R2L() }
    val scope = rememberCoroutineScope()
    val baseContext = LocalContext.current

    var word by remember { mutableStateOf("") }
    var lenient by remember { mutableStateOf(false) }
    var uiLanguage by remember { mutableStateOf(AppSettings.loadLanguage(baseContext)) }
    var displayScript by remember { mutableStateOf(AppSettings.loadDisplayScript(baseContext)) }
    var showSettings by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<DecomposeState>(DecomposeState.Idle) }
    val listState = rememberLazyListState()
    var scrollToIndexOnExpand by remember { mutableStateOf<Int?>(null) }
    var multiWordChoices by remember { mutableStateOf<List<String>?>(null) }
    // Every dictionary source is checked, and Guess Meaning only offered once all of
    // them have answered -- see dictionaryLoading below. Spalding is a local/instant
    // lookup; Tusaalanga is a real network fetch (no distribution rights for its
    // content, must stay live -- see TusaalangaFetcher.kt), so this list fills in over
    // two separate updates, not one.
    var dictionaryResults by remember { mutableStateOf<List<DictionaryLookupResult>>(emptyList()) }
    var dictionaryLoading by remember { mutableStateOf(false) }
    // Tusaalanga's fetch failure (network/HTTP error, not just "word not found") --
    // debug-build-only, same purpose as the system-prompt inspection panel in
    // GuessMeaningScreen.kt: alerts a developer the fetcher broke, not end-user UX.
    var dictionaryFetchError by remember { mutableStateOf<String?>(null) }

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
        // Once the expanded list renders, jump to the first decomposition
        // that wasn't already visible (index PREVIEW_LIMIT, i.e. the 4th)
        // instead of leaving the user scrolled to the top of what they've
        // already seen.
        scrollToIndexOnExpand = PREVIEW_LIMIT
        state = DecomposeState.Loading
        scope.launch {
            state = analyze(analyzer, previous.word, previous.lenient, expandAll = true)
        }
    }

    LaunchedEffect(state) {
        val index = scrollToIndexOnExpand
        if (index != null && state is DecomposeState.Success) {
            scrollToIndexOnExpand = null
            listState.animateScrollToItem(index)
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                    if (s.decompositions.isEmpty()) {
                        Text(stringResource(R.string.no_decompositions, s.word))
                    } else {
                        Text(
                            if (s.hasMore) {
                                stringResource(R.string.decompositions_header_partial, s.word, PREVIEW_LIMIT)
                            } else {
                                stringResource(R.string.decompositions_header_full, s.decompositions.size, s.word)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(state = listState) {
                            itemsIndexed(s.decompositions) { index, rows ->
                                if (s.decompositions.size > 1) {
                                    Text(
                                        text = stringResource(R.string.decomposition_number, index + 1),
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                    )
                                }
                                MorphemeTable(
                                    rows,
                                    preferFrench = uiLanguage == AppLanguage.FRENCH,
                                    displayScript = displayScript,
                                    enteredScript = s.enteredScript,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (s.hasMore) {
                                item {
                                    TextButton(
                                        onClick = { loadMore(s) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.more_button))
                                    }
                                }
                            }
                        }
                    }
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
    // its own scroll region rather than pushing the rest of the screen down
    // indefinitely -- shared across every source found, not one region each.
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
