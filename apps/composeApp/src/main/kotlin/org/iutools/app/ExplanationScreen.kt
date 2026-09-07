package org.iutools.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.iutools.llm.AggregatedBackendStats
import org.iutools.llm.ChatMessage
import org.iutools.llm.ChatRole
import org.iutools.llm.GuessMeaningConversationKey
import org.iutools.llm.extractCandidateMeanings
import org.iutools.llm.extractExplanation
import org.iutools.script.Script

/*
 * "Explications": opened from "Expliquer" on WordLookupScreen (see
 * GuessMeaningSection.onExplain in GuessMeaningInline.kt), per Alain's
 * request that the explanation never replace the candidate meanings list
 * there -- two independently-scrollable panes instead. The top shows the
 * AI's candidate meanings and the reasoning it already produced for them
 * (see CandidateMeanings.kt, no new request to the model -- just text
 * already sitting in the reply). The bottom shows the same "fiche"
 * WordLookupScreen was showing for this word (dictionary/decomposition/
 * Hansard results), minus the search controls -- a frozen WordInfoSnapshot
 * (see WordLookupScreen.kt), not a live second copy of that screen, so the
 * user can read the explanation while referring to the word's known info,
 * each pane scrolling on its own.
 *
 * Debug-only "Inspecter le prompt" (see InspectPromptDialog below): shows
 * the *full* prompt that produced the current candidates/explanation -- both
 * the system instructions (chat_system_prompt) and the seed message, each
 * editable separately since that's how they're actually sent (see
 * sendGuessMeaningTurn()'s systemPrompt/text parameters) -- and
 * resubmittable. A lighter replacement for the old "Advanced" chat screen's
 * prompt-tuning workflow, scoped to one word at a time. Resubmitting
 * overwrites this same attempt's cache entry (see
 * GuessMeaningConversationKey) rather than creating a new one -- its
 * seedMessage field goes slightly stale (it still names the *original*
 * seed, not the edited one) but nothing else reads that field back out of a
 * stored conversation, so this is harmless -- then navigates back to
 * WordLookupScreen, where GuessMeaningSection picks up the new result from
 * that same cache entry automatically, no separate wiring needed.
 */
@Composable
internal fun ExplanationScreen(
    conversationKey: GuessMeaningConversationKey,
    wordInfo: WordInfoSnapshot,
    conversations: SnapshotStateMap<GuessMeaningConversationKey, List<ChatMessage>>,
    modelStats: SnapshotStateMap<String, AggregatedBackendStats>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val apiKey = remember { AppSettings.loadApiKey(context) }
    val unauthorizedErrorMessage = stringResource(R.string.chat_error_unauthorized)
    val localModelDisabledMessage = stringResource(R.string.local_llm_disabled_message)
    val genericErrorTemplate = stringResource(R.string.chat_error)
    var showInspectPrompt by remember { mutableStateOf(false) }
    var resubmitting by remember { mutableStateOf(false) }

    val messages = conversations[conversationKey] ?: emptyList()
    val latestMessage = remember(messages) {
        messages.lastOrNull { it.role == ChatRole.ASSISTANT && !it.isError }
    }
    val candidates = remember(latestMessage) {
        latestMessage?.let { extractCandidateMeanings(it.text) } ?: emptyList()
    }
    val explanation = remember(latestMessage) {
        latestMessage?.let { extractExplanation(it.text) } ?: ""
    }

    // Wraps everything below, including InspectPromptDialog further down
    // (CompositionLocals propagate across the AlertDialog/Popup boundary) --
    // this screen is its own top-level composable (see MainActivity.kt), not
    // nested inside WordLookupScreen, so it needs its own copy of the same
    // in-app-language override, sourced from the WordInfoSnapshot it was
    // opened with rather than a live uiLanguage state of its own.
    LocalizedContent(wordInfo.uiLanguage) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                }
                Text(
                    text = stringResource(R.string.explanation_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // Top pane: candidate meanings + the reasoning behind them, own scroll.
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    if (candidates.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.ai_best_guesses_title),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        candidates.forEach { candidate -> Text("• $candidate") }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(explanation)
                    if (BuildConfig.DEBUG) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showInspectPrompt = true }) {
                            Text(stringResource(R.string.inspect_prompt_button))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            HorizontalDivider()

            // Bottom pane: the word's "fiche", its own independent scroll --
            // per Alain's request, so reading the explanation and checking
            // the word's known info don't fight over one shared scroll.
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    WordInfoCard(wordInfo, highlightMeanings = candidates)
                }
            }
        }
    }

    if (showInspectPrompt) {
        InspectPromptDialog(
            // Same value as conversationKey's language (both fixed at first
            // send); wordInfo carries it as AppLanguage, which this dialog's
            // LocalizedContent needs.
            uiLanguage = wordInfo.uiLanguage,
            // The exact system + seed text that produced the current
            // candidates/explanation -- conversationKey.systemPrompt is
            // fixed at first send (see GuessMeaningConversationKey's header
            // comment), so it's still accurate even if the app's default
            // chat_system_prompt has since been edited.
            initialSystemPrompt = conversationKey.systemPrompt,
            initialSeed = messages.firstOrNull { it.role == ChatRole.USER }?.text ?: "",
            resubmitting = resubmitting,
            onDismiss = { showInspectPrompt = false },
            onResubmit = { editedSystemPrompt, editedSeed ->
                resubmitting = true
                scope.launch {
                    // Starts this attempt's history over from scratch (not a
                    // follow-up appended to the old one) -- the point is to
                    // see what the edited prompt alone produces.
                    conversations[conversationKey] = emptyList()
                    sendGuessMeaningTurn(
                        context = context,
                        history = emptyList(),
                        text = editedSeed,
                        systemPrompt = editedSystemPrompt,
                        useLocalModel = conversationKey.isLocalModel,
                        apiKey = apiKey,
                        modelStats = modelStats,
                        unauthorizedErrorMessage = unauthorizedErrorMessage,
                        localModelDisabledMessage = localModelDisabledMessage,
                        genericErrorTemplate = genericErrorTemplate,
                        onMessagesChanged = { conversations[conversationKey] = it },
                    )
                    resubmitting = false
                    showInspectPrompt = false
                    onBack()
                }
            },
        )
    }
    }
}

@Composable
private fun InspectPromptDialog(
    uiLanguage: AppLanguage,
    initialSystemPrompt: String,
    initialSeed: String,
    resubmitting: Boolean,
    onDismiss: () -> Unit,
    onResubmit: (systemPrompt: String, seed: String) -> Unit,
) {
    var systemPromptDraft by remember { mutableStateOf(initialSystemPrompt) }
    var seedDraft by remember { mutableStateOf(initialSeed) }
    val clipboardManager = LocalClipboardManager.current
    val systemPromptSectionLabel = stringResource(R.string.inspect_prompt_system_prompt_section)
    val seedSectionLabel = stringResource(R.string.inspect_prompt_seed_label)
    LocalizedAlertDialog(
        uiLanguage = uiLanguage,
        onDismissRequest = { if (!resubmitting) onDismiss() },
        title = {
            // Per Alain's request: lets him paste the current draft into a
            // real text editor on his laptop to review/edit it more
            // comfortably than the dialog's small fields allow -- copies
            // both fields together (system prompt, then seed), each under
            // its own labeled section, since that's the two-part shape the
            // backend actually sends (see sendGuessMeaningTurn()).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.inspect_prompt_dialog_title),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(
                                "$systemPromptSectionLabel:\n$systemPromptDraft\n\n$seedSectionLabel:\n$seedDraft",
                            ),
                        )
                    },
                ) {
                    // No Material "copy" icon here for the same reason as
                    // elsewhere in this app (e.g. WordLookupScreen's settings
                    // gear/eye toggle) -- ContentCopy lives in
                    // material-icons-extended, which this project doesn't
                    // depend on.
                    Text("📋")
                }
            }
        },
        text = {
            // Two separate fields, not one combined block of text: the
            // Claude/local-model API takes the system instructions and the
            // seed message as two distinct parameters (see
            // sendGuessMeaningTurn() and :core's GuessMeaningEngine), so
            // editing them separately matches
            // what actually gets sent. No single SelectionContainer wrapping
            // the whole Column, unlike a first version of this dialog --
            // each field already has its own built-in text selection, and
            // nesting one inside a SelectionContainer is a known-risky
            // combination in Compose (the two fight over the same selection/
            // gesture handling) -- see RealAndroidContext's own header
            // comment (WordLookupScreen.kt) for the crash that caused on
            // Alain's Samsung phone. Only the field labels get their own,
            // narrower SelectionContainer wrap.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(stringResource(R.string.chat_system_prompt_label), style = MaterialTheme.typography.labelMedium)
                }
                // RealAndroidContext, not composed directly under this
                // dialog's own LocalizedContent wrap -- see that
                // composable's header comment (WordLookupScreen.kt):
                // pasting into a plain LocalizedContent-wrapped field
                // crashed on Alain's Samsung phone. Neither field has its
                // own label slot (their labels are the sibling Text()
                // calls above, which stay outside this wrap), so nothing
                // needs pre-resolving here.
                RealAndroidContext {
                    OutlinedTextField(
                        value = systemPromptDraft,
                        onValueChange = { systemPromptDraft = it },
                        minLines = 4,
                        maxLines = 10,
                        enabled = !resubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                SelectionContainer {
                    Text(stringResource(R.string.inspect_prompt_seed_label), style = MaterialTheme.typography.labelMedium)
                }
                RealAndroidContext {
                    OutlinedTextField(
                        value = seedDraft,
                        onValueChange = { seedDraft = it },
                        minLines = 5,
                        maxLines = 10,
                        enabled = !resubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (resubmitting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onResubmit(systemPromptDraft, seedDraft) },
                enabled = !resubmitting && systemPromptDraft.isNotBlank() && seedDraft.isNotBlank(),
            ) {
                Text(stringResource(R.string.inspect_prompt_resubmit_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !resubmitting) {
                Text(stringResource(R.string.close_button))
            }
        },
    )
}

/**
 * Read-only rendering of a WordInfoSnapshot -- the same sections
 * WordLookupScreen shows for a word (dictionary/decomposition/Hansard
 * results), minus every interactive affordance that would trigger a new
 * fetch (see WordInfoSnapshot's own header comment for why). [highlightMeanings]
 * is highlighted inside the Hansard examples' English sentences, same as on
 * WordLookupScreen (see HansardExamplesSection) -- passed in rather than
 * recomputed, since ExplanationScreen already has it for its own top pane.
 */
@Composable
internal fun WordInfoCard(info: WordInfoSnapshot, highlightMeanings: List<String> = emptyList()) {
    if (info.dictionaryResults.isNotEmpty()) {
        DictionaryResultSection(info.dictionaryResults, info.displayScript)
        Spacer(modifier = Modifier.height(16.dp))
    } else if (!info.dictionaryLoading &&
        info.decomposeState is DecomposeState.Success &&
        info.decomposeState.decompositions.isNotEmpty()
    ) {
        Text(stringResource(R.string.no_definition_found))
        Spacer(modifier = Modifier.height(16.dp))
    }
    if (info.shorterWordDictionaryResults.isNotEmpty()) {
        ShorterWordDictionarySection(info.shorterWordDictionaryResults, info.displayScript)
        Spacer(modifier = Modifier.height(16.dp))
    }
    if (info.dictionaryLoading) {
        Text(stringResource(R.string.dictionary_checking_label), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(16.dp))
    }

    when (val s = info.decomposeState) {
        is DecomposeState.Idle -> {}
        is DecomposeState.Loading -> CircularProgressIndicator()
        is DecomposeState.Failure -> Text(
            text = when (val reason = s.reason) {
                is FailureReason.Timeout -> stringResource(R.string.error_timeout)
                is FailureReason.AnalysisError -> stringResource(R.string.error_analysis, reason.detail ?: "")
                is FailureReason.FstNotAvailable -> stringResource(R.string.error_fst_not_available)
            },
            color = MaterialTheme.colorScheme.error,
        )
        is DecomposeState.Success -> {
            DecompositionSection(
                success = s,
                preferFrench = info.uiLanguage == AppLanguage.FRENCH,
                displayScript = info.displayScript,
                onLoadMore = {},
                showLoadMore = false,
            )
        }
    }
    if (info.decomposeState is DecomposeState.Success) {
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (info.hansardLoading) {
        Text(stringResource(R.string.hansard_checking_label), style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(16.dp))
    }
    info.hansardResult?.let { result ->
        when (result) {
            is NunavutHansardResult.Found -> {
                HansardExamplesSection(result.word, result.examples, info.displayScript, highlightMeanings)
                Spacer(modifier = Modifier.height(16.dp))
            }
            is NunavutHansardResult.FoundForShorterWord -> {
                ShorterWordNotice(
                    original = displayForm(info.lastSearchedWord, info.displayScript, info.lastSearchedWordScript),
                    matched = displayForm(result.word, info.displayScript, Script.SYLLABIC),
                    prefixText = stringResource(R.string.hansard_shorter_word_prefix),
                    middleText = stringResource(R.string.hansard_shorter_word_middle),
                    suffixText = stringResource(R.string.hansard_shorter_word_suffix),
                )
                Spacer(modifier = Modifier.height(8.dp))
                HansardExamplesSection(result.word, result.examples, info.displayScript, highlightMeanings)
                Spacer(modifier = Modifier.height(16.dp))
            }
            NunavutHansardResult.NotFound -> {
                Text(
                    stringResource(
                        R.string.hansard_no_examples,
                        displayForm(info.lastSearchedWord, info.displayScript, info.lastSearchedWordScript),
                    ),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            // No download affordance here (read-only pane, see this file's
            // header comment) -- just a debug-only note that the DB wasn't
            // available at snapshot time, same audience as
            // WordLookupScreen's own equivalent debug detail.
            NunavutHansardResult.IndexMissing, is NunavutHansardResult.IndexVersionMismatch -> {
                if (BuildConfig.DEBUG) {
                    Text(
                        text = stringResource(R.string.hansard_index_missing),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
