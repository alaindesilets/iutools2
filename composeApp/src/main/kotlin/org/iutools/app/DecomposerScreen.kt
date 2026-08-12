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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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

private fun displayForm(text: String, script: DisplayScript, enteredScript: Script): String {
    val target = when (script) {
        DisplayScript.ROMAN -> Script.ROMAN
        DisplayScript.SYLLABIC -> Script.SYLLABIC
        DisplayScript.AS_ENTERED -> if (enteredScript == Script.SYLLABIC) Script.SYLLABIC else Script.ROMAN
    }
    return TransCoder.ensureScript(target, text)
}

private data class MorphemeRow(
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
fun DecomposerScreen(onOpenGuessMeaning: () -> Unit = {}) {
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

    fun decompose() {
        val wordToAnalyze = word.trim()
        if (wordToAnalyze.isEmpty()) return
        val lenientAtSearch = lenient
        keyboardController?.hide()
        focusManager.clearFocus()
        state = DecomposeState.Loading
        scope.launch {
            state = analyze(analyzer, wordToAnalyze, lenientAtSearch, expandAll = false)
        }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onOpenGuessMeaning) {
                    Text("🔮 " + stringResource(R.string.guess_meaning_button))
                }
                TextButton(onClick = { showSettings = true }) {
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
                keyboardActions = KeyboardActions(onDone = { decompose() }),
                modifier = Modifier.fillMaxWidth(),
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
                onClick = { decompose() },
                enabled = word.isNotBlank() && state != DecomposeState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.decompose_button))
            }

            Spacer(modifier = Modifier.height(16.dp))

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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_button)) } },
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
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { selectedRow = row })
                    .padding(vertical = 8.dp),
            ) {
                Text(text = displayForm(row.surfaceForm, displayScript, enteredScript), modifier = Modifier.weight(1f))
                Text(
                    text = row.fullRecord?.preferredMeaning(preferFrench) ?: stringResource(R.string.unknown_meaning),
                    modifier = Modifier.weight(1f),
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
