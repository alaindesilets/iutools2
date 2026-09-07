package org.iutools.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.iutools.linguisticdata.MorphemeHumanReadableDescr
import org.iutools.morphemedict.MorphemeDictionary
import org.iutools.script.Script

/*
 * The Morpheme Dictionary screen: type the start of a morpheme's canonical
 * form and see the matching morphemes, each with a plain-language description
 * of its grammatical role and its meaning.
 *
 * This is the "which morphemes match" half of the original iutools Morpheme
 * Dictionary web app (see :core's MorphemeDictionary). Real example words
 * from a corpus are a later increment -- see that class's header.
 */
@Composable
internal fun MorphemeDictionaryScreen(onBack: () -> Unit) {
    val baseContext = LocalContext.current
    // Settings this screen actually uses (var, so the shared SettingsDialog's
    // changes take effect here immediately).
    var uiLanguage by remember { mutableStateOf(AppSettings.loadLanguage(baseContext)) }
    var displayScript by remember { mutableStateOf(AppSettings.loadDisplayScript(baseContext)) }
    // Settings this screen doesn't use itself, held only to feed the shared
    // SettingsDialog (same dialog as WordLookupScreen's).
    var lenient by remember { mutableStateOf(AppSettings.loadLenientAnalysis(baseContext)) }
    var analyzerChoice by remember { mutableStateOf(AppSettings.loadAnalyzerChoice(baseContext)) }
    var apiKey by remember { mutableStateOf(AppSettings.loadApiKey(baseContext)) }
    var showSettings by remember { mutableStateOf(false) }

    val dictionary = remember { MorphemeDictionary() }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MorphemeHumanReadableDescr>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query, uiLanguage) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(200) // debounce keystrokes
        results = withContext(Dispatchers.Default) {
            dictionary.search(trimmed, preferFrench = uiLanguage == AppLanguage.FRENCH)
        }
        searching = false
    }

    LocalizedContent(uiLanguage) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onBack, modifier = Modifier.testTag("search_word_button")) {
                        Text(stringResource(R.string.search_word_button))
                    }
                    TextButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.testTag("settings_button"),
                    ) {
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

                Text(
                    text = stringResource(R.string.morpheme_dict_title),
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(modifier = Modifier.height(16.dp))

                val fieldLabel = localizedStringResource(uiLanguage, R.string.morpheme_dict_field_label)
                RealAndroidContext {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(fieldLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("morpheme_dict_input"),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val trimmed = query.trim()
                when {
                    trimmed.isEmpty() -> HintText(stringResource(R.string.morpheme_dict_hint))
                    searching && results.isEmpty() ->
                        HintText(stringResource(R.string.morpheme_dict_searching))
                    results.isEmpty() ->
                        HintText(stringResource(R.string.morpheme_dict_no_results, trimmed))
                    else -> SelectionContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().testTag("morpheme_dict_results"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(results, key = { it.id }) { descr ->
                                MorphemeResultCard(descr, displayScript)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MorphemeResultCard(descr: MorphemeHumanReadableDescr, displayScript: DisplayScript) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // The canonical form from :core is always Roman; render it in the
            // user's chosen display script.
            Text(
                text = displayForm(descr.canonicalForm, displayScript, Script.ROMAN),
                style = MaterialTheme.typography.titleMedium,
            )
            descr.grammar?.takeIf { it.isNotBlank() }?.let { grammar ->
                Text(
                    text = grammar,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            descr.meaning?.takeIf { it.isNotBlank() }?.let { meaning ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = meaning, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
