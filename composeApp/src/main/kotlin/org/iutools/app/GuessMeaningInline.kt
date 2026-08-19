package org.iutools.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anthropic.models.messages.MessageParam
import kotlinx.coroutines.launch

/*
 * The inline Guess Meaning experience on WordLookupScreen: a button shown at
 * exactly the spot a dictionary definition would have appeared, that -- on a
 * single tap -- sends the auto-generated seed prompt (see
 * guessMeaningSeedPrompt() in WordLookupScreen.kt) straight to the AI and
 * shows a spinner, then the candidate meanings. No separate screen, no
 * visible prompt text or chat mechanics -- per Alain's request, this is what
 * both debug and release builds show by default. "Expliquer" next to the
 * candidates doesn't expand anything in place (an earlier version did, but
 * per Alain's request it never replaces the candidate list) -- it opens
 * ExplanationScreen.kt instead, via [GuessMeaningSection]'s onExplain, since
 * WordLookupScreen is the one with the word info (dictionary/decomposition/
 * Hansard) that screen's bottom pane needs.
 *
 * Uses GuessMeaningEngine for the actual backend calls, and reads/writes the
 * same `conversations`/`modelStats` maps ExplanationScreen uses (both
 * hoisted in MainActivity) -- so a debug-only prompt resubmission there (see
 * that file) is immediately reflected here once the user navigates back.
 *
 * Per Alain's request, Claude access now uses the user's own API key (see
 * AppSettings.loadApiKey), not a key baked into the build -- the button's
 * first tap checks for one before ever calling the backend, and routes to
 * Settings instead (see onNeedApiKey) if none is set yet, rather than
 * failing the call and showing an auth error. Irrelevant when useLocalModel
 * is true -- the on-device backend needs no key at all.
 */
@Composable
internal fun GuessMeaningSection(
    wordCacheKey: GuessMeaningCacheKey,
    seed: String,
    uiLanguage: AppLanguage,
    conversations: SnapshotStateMap<GuessMeaningConversationKey, List<ChatMessage>>,
    useLocalModel: Boolean,
    modelStats: SnapshotStateMap<String, AggregatedBackendStats>,
    onExplain: (GuessMeaningConversationKey) -> Unit,
    // Fires whenever the current attempt's candidate meanings change (empty
    // list when there are none yet, or once the word/attempt changes) -- per
    // Alain's request, lets WordLookupScreen highlight them inside the
    // Hansard bilingual examples shown alongside this section (see
    // HansardExamplesSection's highlightMeanings parameter).
    onCandidatesChanged: (List<String>) -> Unit = {},
    // Fires instead of sending when the Claude backend is needed but no API
    // key is configured yet -- WordLookupScreen wires this to open Settings
    // (see AppSettings.loadApiKey/saveApiKey and SettingsDialog's API key
    // field).
    onNeedApiKey: () -> Unit = {},
    // Passed down from WordLookupScreen's own live state, rather than read
    // here via `remember { AppSettings.loadApiKey(context) }` -- this
    // section stays mounted while the Settings dialog (a sibling
    // composable) opens/closes to save a new key, so a `remember` with no
    // key here would capture the value once and never see the update,
    // sending the user back to Settings forever even after they enter a
    // valid key.
    apiKey: String,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val systemPrompt = stringResource(R.string.chat_system_prompt)
    val unauthorizedErrorMessage = stringResource(R.string.chat_error_unauthorized)
    val localModelMissingMessage = stringResource(
        R.string.local_llm_model_missing,
        LocalLlmEngine.modelDirectory(context).absolutePath,
    )
    val genericErrorTemplate = stringResource(R.string.chat_error)

    // Fixed for this attempt (a fresh word, a different backend, or a
    // different UI language all mean a different cache entry -- see
    // GuessMeaningConversationKey) -- everything below keys off it so a new
    // word naturally starts collapsed again, without needing an explicit
    // reset.
    val key = remember(wordCacheKey, useLocalModel, uiLanguage, systemPrompt, seed) {
        GuessMeaningConversationKey(wordCacheKey, useLocalModel, uiLanguage, systemPrompt, seed)
    }
    // Starts true when this exact attempt is already cached (re-searching a
    // word tried earlier this session) -- shows the past result immediately,
    // no button tap or network call needed.
    var attemptStarted by remember(key) { mutableStateOf(!conversations[key].isNullOrEmpty()) }
    var sending by remember(key) { mutableStateOf(false) }
    val messages = conversations[key] ?: emptyList()
    val latestCandidates = remember(messages) {
        messages.lastOrNull { it.role == MessageParam.Role.ASSISTANT }
            ?.takeIf { !it.isError }
            ?.let { extractCandidateMeanings(it.text) }
            ?: emptyList()
    }
    LaunchedEffect(latestCandidates) { onCandidatesChanged(latestCandidates) }

    fun send(text: String) {
        if (sending) return
        sending = true
        scope.launch {
            GuessMeaningEngine.send(
                context = context,
                history = conversations[key] ?: emptyList(),
                text = text,
                systemPrompt = systemPrompt,
                useLocalModel = useLocalModel,
                apiKey = apiKey,
                modelStats = modelStats,
                unauthorizedErrorMessage = unauthorizedErrorMessage,
                localModelMissingMessage = localModelMissingMessage,
                genericErrorTemplate = genericErrorTemplate,
                onMessagesChanged = { conversations[key] = it },
            )
            sending = false
        }
    }

    if (!attemptStarted) {
        TextButton(
            onClick = {
                // Claude needs the user's own key (see this section's
                // header comment) -- route to Settings instead of sending
                // and letting the call fail with an auth error.
                if (!useLocalModel && apiKey.isBlank()) {
                    onNeedApiKey()
                    return@TextButton
                }
                attemptStarted = true
                // Only the very first tap for a given attempt calls the
                // backend -- re-tapping after this (there's nothing else to
                // tap once attemptStarted is true) never happens, and a
                // cached attempt skipped the call entirely above.
                if (conversations[key].isNullOrEmpty()) {
                    send(seed)
                }
            },
            modifier = Modifier.testTag("guess_meaning_button"),
        ) {
            // Sparkle (✨), not a Material Icon -- AutoAwesome (the literal
            // "AI feature" icon) lives in the "extended" icon set, not the
            // core one this project depends on. ✨ is the de facto standard
            // glyph for "AI feature" across chat apps when a real icon isn't
            // available -- swapped in for 🔮 (crystal ball), which Alain
            // reported rendering as a plain globe on his device.
            Text("✨ " + stringResource(R.string.guess_meaning_button))
        }
    } else {
        GuessMeaningResultPanel(
            messages = messages,
            candidates = latestCandidates,
            sending = sending,
            onExplainClick = { onExplain(key) },
        )
    }
}

/**
 * Renders whatever state a Guess Meaning attempt is currently in: a loading
 * spinner, an error, a raw-text fallback (if the reply didn't contain a
 * parseable "Candidate meanings:" section -- see CandidateMeanings.kt), or
 * the "AI best guesses" list with its "Explain" button. Pure rendering, no
 * network calls or navigation of its own -- [onExplainClick] is the caller's
 * business (see GuessMeaningSection.onExplain, wired to open
 * ExplanationScreen.kt). [candidates] is passed in (not recomputed here) so
 * GuessMeaningSection can also feed the same list to its onCandidatesChanged
 * callback -- one computation, two consumers.
 */
@Composable
internal fun GuessMeaningResultPanel(
    messages: List<ChatMessage>,
    candidates: List<String>,
    sending: Boolean,
    onExplainClick: () -> Unit,
) {
    val latestMessage = remember(messages) { messages.lastOrNull { it.role == MessageParam.Role.ASSISTANT } }

    when {
        sending && latestMessage == null -> {
            // Only the very first reply gets this indicator -- a later one
            // (e.g. a debug prompt resubmission on ExplanationScreen) is
            // already gone from this screen by the time it lands.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                Text(stringResource(R.string.guess_meaning_loading_label))
            }
        }
        latestMessage != null && latestMessage.isError -> {
            // Always shown -- an API/network failure means the feature
            // itself didn't work at all (missing/rejected API key, network
            // failure), which a normal user needs to know about, not just a
            // developer (unlike a single dictionary/Hansard fetcher failing,
            // see AGENTS.md's debug-only guidance for those).
            Text(
                text = latestMessage.text,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        latestMessage != null && !sending && candidates.isEmpty() -> {
            // The model answered, but its reply didn't contain a parseable
            // "Candidate meanings:" section -- fall back to showing its raw
            // answer rather than leaving this section blank.
            Text(
                text = latestMessage.text,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    if (candidates.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ai_best_guesses_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = onExplainClick) {
                    Text(stringResource(R.string.guess_meaning_explain_button))
                }
            }
            candidates.forEach { candidate -> Text("• $candidate") }
        }
    }
}
