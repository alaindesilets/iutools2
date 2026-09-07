package org.iutools.llm

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Talks to Claude through Anthropic's official Java SDK -- the one concrete
 * [LlmClient] the shipped app uses.
 *
 * [apiKey] is read afresh on every call (the user can change their key in
 * Settings between calls), so a single instance can be kept for the app's
 * lifetime. The blocking SDK call is moved off the caller's thread with
 * Dispatchers.IO. Every failure the SDK can raise is turned into an
 * [LlmResponse] -- a rejected key into [LlmResponse.Unauthorized], anything
 * else into [LlmResponse.Failed] with its cause chain flattened (see
 * [causeChainMessage]) -- so callers never see an SDK exception type.
 */
class LlmClient_Anthropic(private val apiKey: () -> String) : LlmClient {

    override suspend fun send(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        try {
            val client = AnthropicOkHttpClient.builder().apiKey(apiKey()).build()
            val params = MessageCreateParams.builder()
                .model(request.model)
                .maxTokens(request.maxTokens)
                .system(request.systemPrompt)
                .messages(
                    request.messages.map {
                        MessageParam.builder().role(it.role.toAnthropic()).content(it.text).build()
                    },
                )
                .build()

            val startedAt = System.currentTimeMillis()
            val response = client.messages().create(params)
            val latencyMs = System.currentTimeMillis() - startedAt

            val text = response.content()
                .mapNotNull { it.text().orElse(null) }
                .joinToString("") { it.text() }

            LlmResponse.Ok(
                text = text,
                latencyMs = latencyMs,
                inputTokens = response.usage().inputTokens(),
                outputTokens = response.usage().outputTokens(),
            )
        } catch (e: UnauthorizedException) {
            // Same 401 for a missing, invalid, revoked, or expired key --
            // no need (or way) to tell them apart here.
            LlmResponse.Unauthorized
        } catch (e: Exception) {
            LlmResponse.Failed(causeChainMessage(e))
        }
    }

    // ChatRole is vendor-neutral; the SDK's own role type is only used here.
    private fun ChatRole.toAnthropic(): MessageParam.Role = when (this) {
        ChatRole.USER -> MessageParam.Role.USER
        ChatRole.ASSISTANT -> MessageParam.Role.ASSISTANT
    }
}
