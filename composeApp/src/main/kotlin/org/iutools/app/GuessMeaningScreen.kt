package org.iutools.app

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Guess Meaning spike (see doc/spike-llm-local-iutools-mobile.md):
 * Phase 1 proved the app can call Claude from Android, handle the API key
 * safely, and surface per-request latency/token metrics as a Phase 6
 * comparison baseline. Phase 2 adds the morphological content: DecomposerScreen
 * generates a seed message (see guessMeaningSeedPrompt() there) from the word's
 * decomposition and passes it in as [initialInput] -- still editable, still sent
 * manually, per the spike plan's "Comportement du bouton" section. No web
 * search/dictionary tools yet (Phase 3+); the system prompt (chat_system_prompt
 * string resource, one per language) says so explicitly so Claude doesn't
 * assume it can look anything up. It also always asks for the reasoning in
 * the user's UI language, so Claude's replies match it too -- except the
 * closing "Candidate meanings:" list, deliberately kept in English in both
 * languages per Alain's request (a stable cross-language gloss, matching how
 * the linguistic data always carries an English meaning field). The prompt
 * asks for a brief (1-2 sentence) rationale before the list on the first
 * reply, saving a fuller explanation for a follow-up if asked -- a middle
 * ground between cost (output tokens are priced ~5x input tokens on Opus,
 * and a full multi-paragraph explanation was consistently the larger share
 * of each call's cost) and quality (some visible reasoning still lets
 * Claude condition its answer on that reasoning -- cutting it entirely
 * risked a worse guess, not just a shorter one, per Alain's concern).
 *
 * Conversations are cached per (word, lenient) in MainActivity (not private
 * to this file, so it can hold the cache -- see [ChatMessage] and
 * [GuessMeaningCacheKey] below) so re-opening Guess Meaning for a word
 * already analyzed replays the previous conversation instead of calling
 * Claude again; sending a follow-up message from that replayed conversation
 * still goes through send() normally, so it naturally carries the full
 * prior history as context plus whatever the *current* system prompt is.
 */

private const val MODEL = "claude-opus-5"
private const val MAX_TOKENS = 4096L

data class GuessMeaningCacheKey(val word: String, val lenient: Boolean)

data class ChatMessage(
    val role: MessageParam.Role,
    val text: String,
    val isError: Boolean = false,
    val latencyMs: Long? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
)

private class ClaudeCallResult(
    val text: String,
    val latencyMs: Long,
    val inputTokens: Long,
    val outputTokens: Long,
)

private suspend fun callClaude(history: List<ChatMessage>, systemPrompt: String): ClaudeCallResult = withContext(Dispatchers.IO) {
    val client = AnthropicOkHttpClient.builder().apiKey(BuildConfig.ANTHROPIC_API_KEY).build()
    val params = MessageCreateParams.builder()
        .model(MODEL)
        .maxTokens(MAX_TOKENS)
        .system(systemPrompt)
        .messages(history.map { MessageParam.builder().role(it.role).content(it.text).build() })
        .build()

    val startedAt = System.currentTimeMillis()
    val response = client.messages().create(params)
    val latencyMs = System.currentTimeMillis() - startedAt

    val text = response.content()
        .mapNotNull { it.text().orElse(null) }
        .joinToString("") { it.text() }

    ClaudeCallResult(
        text = text,
        latencyMs = latencyMs,
        inputTokens = response.usage().inputTokens(),
        outputTokens = response.usage().outputTokens(),
    )
}

@Composable
fun GuessMeaningScreen(
    onBack: () -> Unit,
    initialInput: String = "",
    initialMessages: List<ChatMessage> = emptyList(),
    onMessagesChanged: (List<ChatMessage>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initialInput) }
    var messages by remember { mutableStateOf(initialMessages) }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showSystemPrompt by remember { mutableStateOf(false) }
    // Resolved here (composable context) so the coroutine below -- which
    // can't call stringResource() itself -- can still show a localized
    // message for an invalid/expired key (HTTP 401) instead of Anthropic's
    // raw exception text.
    val unauthorizedErrorMessage = stringResource(R.string.chat_error_unauthorized)
    // Same reason as unauthorizedErrorMessage above: resolved here so the
    // coroutine below can use it, and asks Claude to reason/reply in this
    // same language -- see the header comment for the one deliberate
    // exception (the "Candidate meanings:" list stays English).
    val systemPrompt = stringResource(R.string.chat_system_prompt)

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        keyboardController?.hide()
        focusManager.clearFocus()
        input = ""
        val historyWithNewMessage = messages + ChatMessage(MessageParam.Role.USER, text)
        messages = historyWithNewMessage
        sending = true
        scope.launch {
            messages = try {
                val result = callClaude(historyWithNewMessage, systemPrompt)
                historyWithNewMessage + ChatMessage(
                    role = MessageParam.Role.ASSISTANT,
                    text = result.text,
                    latencyMs = result.latencyMs,
                    inputTokens = result.inputTokens,
                    outputTokens = result.outputTokens,
                )
            } catch (e: UnauthorizedException) {
                // Covers an invalid, revoked, or expired key alike -- the API
                // returns the same 401 for all three, so there's no reliable
                // way (or need) to tell them apart here.
                historyWithNewMessage + ChatMessage(
                    role = MessageParam.Role.ASSISTANT,
                    text = unauthorizedErrorMessage,
                    isError = true,
                )
            } catch (e: Exception) {
                historyWithNewMessage + ChatMessage(
                    role = MessageParam.Role.ASSISTANT,
                    text = e.message ?: e.toString(),
                    isError = true,
                )
            }
            sending = false
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(messages) {
        onMessagesChanged(messages)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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

            // Debug builds only: the system prompt drives Claude's behavior just as
            // much as the visible message, but it's never part of the conversation
            // itself (see the "system prompt vs. user message" discussion in the
            // spike notes) -- normal users shouldn't see internal prompt-engineering
            // text, but it needs to stay inspectable while developing/tuning it. Shown
            // as the first item of the same scrollable list as the chat itself (below)
            // rather than in its own height-capped box, so seeing the whole thing just
            // takes the same familiar scroll gesture as the rest of the conversation.
            if (BuildConfig.DEBUG) {
                TextButton(onClick = { showSystemPrompt = !showSystemPrompt }) {
                    Text((if (showSystemPrompt) "▾ " else "▸ ") + stringResource(R.string.chat_system_prompt_label))
                }
            }

            if (BuildConfig.ANTHROPIC_API_KEY.isBlank()) {
                Text(
                    text = stringResource(R.string.chat_missing_api_key),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
                return@Column
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                if (BuildConfig.DEBUG && showSystemPrompt) {
                    item {
                        Text(
                            text = systemPrompt,
                            style = MaterialTheme.typography.bodySmall,
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
                                    text = if (isUser) {
                                        stringResource(R.string.chat_role_you)
                                    } else {
                                        stringResource(R.string.chat_role_claude)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                SelectionContainer {
                                    Text(
                                        text = message.text,
                                        color = if (message.isError) MaterialTheme.colorScheme.error else Color.Unspecified,
                                    )
                                }
                                if (message.latencyMs != null) {
                                    Text(
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
