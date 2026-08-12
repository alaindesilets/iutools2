package org.iutools.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
 * Guess Meaning spike, Phase 1 (see doc/spike-llm-local-iutools-mobile.md):
 * a plain chat screen wired to the real Anthropic API, with no injected
 * morphological content or tools yet (that's Phase 2+) -- the goal here is
 * only to prove the app can call Claude from Android, handle the API key
 * safely, and surface per-request latency/token metrics as a Phase 6
 * comparison baseline.
 */

private const val MODEL = "claude-opus-5"
private const val MAX_TOKENS = 4096L

private data class ChatMessage(
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

private suspend fun callClaude(history: List<ChatMessage>): ClaudeCallResult = withContext(Dispatchers.IO) {
    val client = AnthropicOkHttpClient.builder().apiKey(BuildConfig.ANTHROPIC_API_KEY).build()
    val params = MessageCreateParams.builder()
        .model(MODEL)
        .maxTokens(MAX_TOKENS)
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
fun GuessMeaningScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Resolved here (composable context) so the coroutine below -- which
    // can't call stringResource() itself -- can still show a localized
    // message for an invalid/expired key (HTTP 401) instead of Anthropic's
    // raw exception text.
    val unauthorizedErrorMessage = stringResource(R.string.chat_error_unauthorized)

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        input = ""
        val historyWithNewMessage = messages + ChatMessage(MessageParam.Role.USER, text)
        messages = historyWithNewMessage
        sending = true
        scope.launch {
            messages = try {
                val result = callClaude(historyWithNewMessage)
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text("←") }
                Text(
                    text = stringResource(R.string.guess_meaning_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
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
                itemsIndexed(messages) { _, message ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = if (message.role == MessageParam.Role.USER) "You" else "Claude",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = message.text,
                            color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                        )
                        if (message.latencyMs != null) {
                            Text(
                                text = stringResource(
                                    R.string.chat_metrics,
                                    message.latencyMs,
                                    message.inputTokens ?: 0,
                                    message.outputTokens ?: 0,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    HorizontalDivider()
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
