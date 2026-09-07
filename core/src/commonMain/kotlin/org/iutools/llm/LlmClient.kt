package org.iutools.llm

/*
 * A vendor-neutral way to send one chat request to a large language model
 * and get one reply back.
 *
 * The point of the interface is that the code which assembles a request and
 * interprets the reply (see GuessMeaningEngine) has no idea which provider
 * answers it, and needs no provider SDK on its classpath. A real
 * implementation wraps one vendor's client (today: Anthropic's Java SDK);
 * a test supplies a fake that returns canned replies.
 *
 * Failures are returned as [LlmResponse] variants, not thrown -- the caller
 * lives in shared code that can't reference any particular SDK's exception
 * types, and every outcome (including "the key was rejected" and "the
 * network call failed") has to be describable without them.
 */
interface LlmClient {
    suspend fun send(request: LlmRequest): LlmResponse
}

/*
 * One request to an LLM: which model, how many tokens it may spend on the
 * reply, the system prompt, and the conversation so far (oldest first).
 */
data class LlmRequest(
    val model: String,
    val maxTokens: Long,
    val systemPrompt: String,
    val messages: List<ChatMessage>,
)

/*
 * The outcome of one [LlmClient.send] call.
 *
 * [Ok] carries the reply text plus what it cost to get it -- how long the
 * call took and how many tokens went in and came back. [Unauthorized] is
 * the specific "the API key was missing, invalid, revoked, or expired"
 * case, kept separate because callers show a different, actionable message
 * for it. [Failed] is everything else (no network, timeout, a server
 * error, a malformed reply); its [message] is already flattened to a
 * single human-readable string, in English -- a caller that shows it to a
 * user is expected to wrap it in its own translated template.
 */
sealed interface LlmResponse {
    data class Ok(
        val text: String,
        val latencyMs: Long,
        val inputTokens: Long,
        val outputTokens: Long,
    ) : LlmResponse

    data object Unauthorized : LlmResponse

    data class Failed(val message: String) : LlmResponse
}

/*
 * What one successful model call cost, handed back by GuessMeaningEngine so
 * the caller can update its own running per-model stats and spend log.
 * GuessMeaningEngine itself keeps no state and touches no storage.
 */
data class CallOutcome(
    val model: String,
    val latencyMs: Long,
    val inputTokens: Long,
    val outputTokens: Long,
)
