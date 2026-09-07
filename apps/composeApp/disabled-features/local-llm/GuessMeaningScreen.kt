package org.iutools.app

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.anthropic.models.messages.MessageParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.iutools.llm.GuessMeaningCostLog

/*
 * The "Advanced" Guess Meaning screen: a debug-only tool for prompt-tuning,
 * reachable only from the debug-only button next to GuessMeaningSection in
 * WordLookupScreen.kt (see that file). A normal user never sees this screen
 * -- the actual feature, for everyone, is the inline flow in
 * GuessMeaningInline.kt (auto-send, spinner, candidate meanings, "Explain"),
 * added per Alain's request to remove the chat-window feel entirely from the
 * normal experience. This screen is what that inline flow replaced as the
 * *default* view; it still exists because prompt-tuning during development
 * genuinely needs to see the raw turn-by-turn transcript, edit the system
 * prompt, and review/edit the seed message before sending it -- none of
 * which a real user should ever have to deal with.
 *
 * Backend calls (Claude, or the on-device model) go through
 * GuessMeaningEngine.send(), shared with GuessMeaningSection, both reading/
 * writing the same `conversations`/`modelStats` maps hoisted in MainActivity
 * -- opening this screen mid-attempt (or going back to the inline flow)
 * picks up the same conversation rather than starting over or diverging.
 *
 * local-llm-spike branch, Phase 6 (see doc/spike-llm-local-iutools-mobile.md):
 * the local-model toggle/picker here switches the backend to an on-device
 * model via LocalLlmEngine.kt instead of Claude, for direct side-by-side
 * comparison. Both backends share the same "Candidate meanings:" contract --
 * extractCandidateMeanings() in CandidateMeanings.kt reads that section back
 * out of either backend's reply for the "AI best guesses" panel below the
 * chat.
 *
 * Prompt-tuning support (added once the first real local-model test showed
 * Qwen2-0.5B producing poor guesses, per Alain's request to keep iterating on
 * the local model rather than give up on it): the system prompt shown below
 * is editable here, not just a read-only display of chat_system_prompt --
 * and [GuessMeaningConversationKey] folds the exact system prompt text and
 * seed/first message into the cache key alongside the word and backend, so
 * trying a different prompt or a tweaked seed for the same word creates its
 * own cache entry rather than colliding with (or silently replaying) a
 * previous attempt. The key for a given conversation is fixed once at the
 * first send() of a fresh attempt (see currentAttemptKey) -- editing the
 * prompt fields mid-conversation only affects the *next* fresh attempt, not
 * the one already in progress, and follow-ups keep updating the same cache
 * entry regardless of later edits. The "Nouvel essai" button clears the
 * current attempt so a new prompt/seed combination can be tried without
 * leaving the screen.
 */
@Composable
fun GuessMeaningScreen(
    onBack: () -> Unit,
    // Null only when opened with no word context at all (shouldn't normally
    // happen given how MainActivity wires navigation, but keeps this screen
    // usable/previewable standalone without crashing).
    wordCacheKey: GuessMeaningCacheKey?,
    seed: String = "",
    // Shared with MainActivity (not copied) so writes here are immediately
    // visible there too, and survive navigating away and back -- see
    // [GuessMeaningConversationKey].
    conversations: SnapshotStateMap<GuessMeaningConversationKey, List<ChatMessage>> = mutableStateMapOf(),
    // local-llm-spike branch: hoisted up to MainActivity (not plain local
    // remember state here) so it can be folded into the conversation cache
    // key above -- otherwise re-opening an already-answered word always
    // replayed whichever backend answered it *first*, regardless of this
    // toggle's position, since the cache used to be keyed by word alone.
    // Also makes the choice "sticky" across words, which is what you'd want
    // while going back and forth comparing backends on several words in a
    // row, rather than it silently resetting to Claude every time.
    useLocalModel: Boolean = false,
    onUseLocalModelChanged: (Boolean) -> Unit = {},
    // Shared with MainActivity, same reasoning as [conversations] -- survives
    // navigating away and back, so stats accumulate across every word tried
    // in a session, not just the current one. See ModelStats.kt.
    modelStats: SnapshotStateMap<String, AggregatedBackendStats> = mutableStateMapOf(),
) {
    val scope = rememberCoroutineScope()
    val baseContext = LocalContext.current
    // Bug found while wiring the language into the cache key below: unlike
    // WordLookupScreen (see its own localizedContext/localizedConfiguration),
    // this screen had no locale override at all, so it silently followed the
    // device's system locale rather than Alain's in-app language toggle --
    // read from the same persisted setting WordLookupScreen's settings
    // dialog writes (AppSettings.saveLanguage), not threaded through
    // navigation, since it's just as easy to re-read here. `val`, not `var`:
    // this screen has no UI of its own to change it -- that lives in
    // WordLookupScreen's settings dialog.
    val uiLanguage = remember { AppSettings.loadLanguage(baseContext) }
    val baseConfiguration = LocalConfiguration.current
    val localizedConfiguration = remember(uiLanguage, baseConfiguration) {
        Configuration(baseConfiguration).apply { setLocale(uiLanguage.locale) }
    }
    // Used directly (context.getString(...)) for strings resolved before the
    // CompositionLocalProvider below is established -- stringResource() only
    // picks up a LocalContext/LocalConfiguration override from inside that
    // provider's content, and several of these are needed earlier (seeded
    // into remember{} state, or captured in send()'s closure defined before
    // any UI is emitted).
    val localizedContext = remember(uiLanguage, baseContext) {
        baseContext.createConfigurationContext(localizedConfiguration)
    }
    val defaultSystemPrompt = localizedContext.getString(R.string.chat_system_prompt)
    // The prompt actually sent as the system instruction -- editable (see
    // the file header comment), pre-filled from the validated default. Seeded
    // once from defaultSystemPrompt via remember's single-evaluation
    // semantics -- deliberately not re-synced on later recompositions, or
    // editing/clearing the field would keep getting overwritten.
    var systemPrompt by remember { mutableStateOf(defaultSystemPrompt) }
    // The attempt (if any) already cached for the word+backend+default-prompt
    // combination -- what you'd see without having edited anything yet.
    // Computed once; editing the prompt/seed only takes effect on the *next*
    // fresh attempt (see currentAttemptKey and send()), not retroactively.
    val defaultKey = remember(wordCacheKey, useLocalModel, defaultSystemPrompt, seed) {
        wordCacheKey?.let { GuessMeaningConversationKey(it, useLocalModel, uiLanguage, defaultSystemPrompt, seed) }
    }
    val initialMessages = defaultKey?.let { conversations[it] } ?: emptyList()
    var input by remember { mutableStateOf(if (initialMessages.isEmpty()) seed else "") }
    var messages by remember { mutableStateOf(initialMessages) }
    // The key this screen instance is currently reading from / writing to --
    // null until the first send() of a fresh attempt establishes it (from
    // whatever systemPrompt/seed text is live at that moment), fixed for the
    // rest of that attempt's follow-ups regardless of later prompt edits.
    // Starts non-null when there's already a cached default-prompt attempt
    // to resume (see initialMessages above).
    var currentAttemptKey by remember { mutableStateOf(if (initialMessages.isNotEmpty()) defaultKey else null) }
    var sending by remember { mutableStateOf(false) }
    // Feedback for the "Choisir le fichier modèle" picker below -- null
    // until the human picks a file at least once this screen instance.
    var modelImportStatus by remember { mutableStateOf<String?>(null) }
    val modelImportedMessage = localizedContext.getString(R.string.local_llm_model_imported)
    val modelImportFailedTemplate = localizedContext.getString(R.string.local_llm_model_import_failed)
    // Storage Access Framework picker -- per Alain's preference, this
    // avoids needing USB debugging / adb push just to get a model file onto
    // the device: download the .litertlm in the device's own browser (lands
    // in the ordinary Downloads folder), then pick it here. No storage
    // permission needed either way. "*/*" because .litertlm isn't a
    // registered MIME type to filter by more narrowly.
    val pickModelFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            modelImportStatus = try {
                withContext(Dispatchers.IO) { LocalLlmEngine.importModel(baseContext, uri) }
                modelImportedMessage
            } catch (e: Exception) {
                modelImportFailedTemplate.format(e.message ?: e.toString())
            }
        }
    }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showSystemPrompt by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    // Resolved here (not inline via stringResource() -- see localizedContext's
    // own comment above) so the coroutine below can still show a localized
    // message for an invalid/expired key (HTTP 401) instead of Anthropic's
    // raw exception text.
    val unauthorizedErrorMessage = localizedContext.getString(R.string.chat_error_unauthorized)
    // Same reasoning, interpolating the exact directory findModelFile()
    // looked in so a developer testing this can immediately see where to
    // `adb push` the model file.
    val localModelMissingMessage = localizedContext.getString(
        R.string.local_llm_model_missing,
        LocalLlmEngine.modelDirectory(baseContext).absolutePath,
    )
    val genericErrorTemplate = localizedContext.getString(R.string.chat_error)
    // The user's own key (see AppSettings.loadApiKey) -- this screen is
    // unreachable via any button right now (see the file header comment),
    // but kept correct rather than left pointing at the retired
    // BuildConfig.ANTHROPIC_API_KEY, in case it's ever reconnected.
    val apiKey = remember { AppSettings.loadApiKey(baseContext) }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        keyboardController?.hide()
        focusManager.clearFocus()
        input = ""
        sending = true
        // Establish this attempt's cache key on its first send (capturing
        // whatever systemPrompt/backend are live right now); follow-ups
        // reuse the same key regardless of later prompt edits -- see the
        // file header comment and currentAttemptKey's own comment above.
        if (currentAttemptKey == null) {
            currentAttemptKey = wordCacheKey?.let { GuessMeaningConversationKey(it, useLocalModel, uiLanguage, systemPrompt, text) }
        }
        scope.launch {
            GuessMeaningEngine.send(
                context = baseContext,
                history = messages,
                text = text,
                systemPrompt = systemPrompt,
                useLocalModel = useLocalModel,
                apiKey = apiKey,
                modelStats = modelStats,
                unauthorizedErrorMessage = unauthorizedErrorMessage,
                localModelMissingMessage = localModelMissingMessage,
                genericErrorTemplate = genericErrorTemplate,
                onMessagesChanged = { messages = it },
            )
            sending = false
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(messages) {
        currentAttemptKey?.let { conversations[it] = messages }
    }

    // Overrides stringResource()'s language for everything below, independently
    // of the device's system locale -- same pattern as WordLookupScreen's own
    // localizedContext/localizedConfiguration (see uiLanguage's comment above
    // for why this screen needed its own copy rather than inheriting one).
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Wraps the whole screen (not just message bubbles, as before) so
        // every bit of text -- title, system prompt panel, metrics, chat
        // messages -- is copy-pasteable, per Alain's request. Nesting
        // SelectionContainers is unsupported in Compose, so this replaces
        // the narrower one that used to wrap just message.text below,
        // rather than adding another one inside it.
        SelectionContainer {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                }
                Text(
                    text = stringResource(R.string.guess_meaning_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // The system prompt drives Claude's behavior just as much as the
            // visible message, but it's never part of the conversation
            // itself -- shown as the first item of the same scrollable list
            // as the chat itself (below) rather than in its own height-capped
            // box, so seeing the whole thing just takes the same familiar
            // scroll gesture as the rest of the conversation.
            TextButton(onClick = { showSystemPrompt = !showSystemPrompt }) {
                Text((if (showSystemPrompt) "▾ " else "▸ ") + stringResource(R.string.chat_system_prompt_label))
            }
            // local-llm-spike branch: no config screen yet (see the plan
            // doc's "hors scope" list), so this stays a plain toggle here
            // rather than a real settings entry -- ahead of the API-key
            // check below, since local mode needs no key.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.local_llm_toggle_label))
                Switch(checked = useLocalModel, onCheckedChange = onUseLocalModelChanged)
            }
            TextButton(onClick = { pickModelFileLauncher.launch(arrayOf("*/*")) }) {
                Text(stringResource(R.string.local_llm_pick_model_button))
            }
            modelImportStatus?.let { status ->
                Text(status, style = MaterialTheme.typography.labelSmall)
            }
            // Clears the conversation so the next send() starts a fresh
            // attempt under a new cache key built from whatever
            // systemPrompt/input are live at that point -- lets a
            // different prompt/seed variant be tried for this same word
            // without losing the one just shown (still in `conversations`
            // under its own key) and without leaving the screen.
            TextButton(onClick = { messages = emptyList(); currentAttemptKey = null; input = seed }) {
                Text(stringResource(R.string.guess_meaning_new_attempt_button))
            }
            // Per-backend/model averages accumulated across every real
            // (non-cached) call this session -- see ModelStats.kt for why
            // cache replays never reach here. One row per stats key
            // (Claude's MODEL constant, or a local model file's name).
            TextButton(onClick = { showStats = !showStats }) {
                Text((if (showStats) "▾ " else "▸ ") + stringResource(R.string.guess_meaning_stats_label))
            }
            if (showStats) {
                if (modelStats.isEmpty()) {
                    Text(stringResource(R.string.guess_meaning_stats_empty), style = MaterialTheme.typography.labelSmall)
                }
                // Per Alain's request: cost stats specific to each model,
                // not one lumped total, since he's planning to compare
                // several online models at once (see
                // GuessMeaningCostTracker.kt) -- read once per
                // recomposition of this panel, not per row, since it's
                // the same map for every model label below. Not wrapped
                // in remember(): modelStats is a SnapshotStateMap whose
                // identity never changes on mutation, so keying remember
                // on it would never re-run after the first composition;
                // this read is cheap (SharedPreferences) and this block
                // already only recomposes when modelStats/showStats
                // actually change. A model absent from this map (never a
                // real priced call yet -- always true for a local model)
                // shows zeroes, which is exactly the right answer for it.
                val costsByModel = GuessMeaningCostLog(sharedPreferencesCostLogStore(baseContext)).accumulatedByModel()
                modelStats.forEach { (label, stats) ->
                    Text(
                        text = stringResource(
                            R.string.guess_meaning_stats_row,
                            label,
                            stats.callCount,
                            stats.averageLatencySeconds,
                            stats.averageInputTokens,
                            stats.averageOutputTokens,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    val modelCosts = costsByModel[label] ?: GuessMeaningCostLog.AccumulatedCosts()
                    Text(
                        text = stringResource(
                            R.string.guess_meaning_stats_cost_row,
                            modelCosts.today,
                            modelCosts.thisWeek,
                            modelCosts.thisMonth,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                    )
                }
            }

            if (apiKey.isBlank() && !useLocalModel) {
                Text(
                    text = stringResource(R.string.chat_missing_api_key),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
                return@Column
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                if (showSystemPrompt) {
                    item {
                        // Editable (see the file header comment) so different
                        // wordings can be tried against the local model --
                        // edits here only affect the *next* fresh attempt
                        // (see currentAttemptKey), not a conversation already
                        // in progress.
                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            label = { Text(stringResource(R.string.chat_system_prompt_label)) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            minLines = 3,
                            maxLines = 12,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(messages) { _, message ->
                    val isUser = message.role == MessageParam.Role.USER
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                    ) {
                        Surface(
                            color = when {
                                message.isError -> MaterialTheme.colorScheme.errorContainer
                                isUser -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.85f),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = when {
                                        isUser -> stringResource(R.string.chat_role_you)
                                        message.isLocalModel -> stringResource(R.string.local_llm_role_label)
                                        else -> stringResource(R.string.chat_role_claude)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    text = message.text,
                                    color = if (message.isError) MaterialTheme.colorScheme.error else Color.Unspecified,
                                )
                                if (message.latencyMs != null) {
                                    Text(
                                        // Same shape for both backends now that the local
                                        // one also reports real prefill/decode token counts
                                        // (see LocalGenerationEvent.Done and ModelStats.kt)
                                        // rather than fake zeros.
                                        text = stringResource(
                                            R.string.chat_metrics,
                                            message.latencyMs / 1000.0,
                                            message.inputTokens ?: 0,
                                            message.outputTokens ?: 0,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
                if (sending) {
                    item { CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp)) }
                }
            }

            // "AI best guesses": pulled from the latest non-error assistant
            // reply, either backend (see CandidateMeanings.kt) -- kept
            // outside the scrolling LazyColumn above, right next to the
            // input, so it doesn't need scrolling to find once an answer is
            // in. Updates live while a local-model reply is still streaming
            // in, since extractCandidateMeanings() tolerates a partially-
            // generated list -- the fuller running text is still visible in
            // the chat log above in the meantime. No "Explain" button here,
            // unlike the inline flow (GuessMeaningInline.kt) -- the full
            // reasoning is already visible in the chat log above, and a
            // fuller explanation is just a normal follow-up message away.
            val latestCandidates = remember(messages) {
                messages.lastOrNull { it.role == MessageParam.Role.ASSISTANT && !it.isError }
                    ?.let { extractCandidateMeanings(it.text) }
                    ?: emptyList()
            }
            if (latestCandidates.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.ai_best_guesses_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    latestCandidates.forEach { candidate -> Text("• $candidate") }
                }
            }

            // Estimated USD cost, per Alain's request -- see
            // GuessMeaningCostTracker.kt. Only meaningful for Claude replies
            // (a priced model), never for the local model, so this sums only
            // this conversation's non-local assistant replies and hides
            // itself entirely when that sum is zero (no priced reply yet, or
            // useLocalModel is on for this whole attempt). Broken down per
            // model (not just Claude) since Alain is planning to compare
            // several online models -- see the Stats panel above for the
            // full per-model breakdown; this line only ever needs MODEL's
            // own totals, since every priced reply in THIS conversation came
            // from MODEL (the local path is never priced).
            val wordCostUsd = remember(messages) {
                messages
                    .filter { it.role == MessageParam.Role.ASSISTANT && !it.isError && !it.isLocalModel }
                    .sumOf { estimatedCostUsd("claude-haiku-4-5", it.inputTokens ?: 0, it.outputTokens ?: 0) ?: 0.0 }
            }
            if (wordCostUsd > 0.0) {
                // Re-read on every new message, not just once -- cheap
                // (SharedPreferences), and needs to reflect the record() call
                // send() just made for the reply that triggered this
                // recomposition.
                val accumulatedCosts = remember(messages) {
                    GuessMeaningCostLog(sharedPreferencesCostLogStore(baseContext)).accumulatedByModel()["claude-haiku-4-5"] ?: GuessMeaningCostLog.AccumulatedCosts()
                }
                Text(
                    text = stringResource(
                        R.string.guess_meaning_cost_summary,
                        wordCostUsd,
                        accumulatedCosts.today,
                        accumulatedCosts.thisWeek,
                        accumulatedCosts.thisMonth,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.chat_input_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { send() }, enabled = !sending && input.isNotBlank()) {
                    Text(stringResource(R.string.chat_send_button))
                }
            }
        }
        }
    }
    }
}
