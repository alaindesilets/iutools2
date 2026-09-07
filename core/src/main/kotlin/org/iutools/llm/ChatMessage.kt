package org.iutools.llm

/*
 * One turn in a LLM chat: who produced it, the text, and -- for a model
 * reply -- the timing and token counts of the call behind it.
 */
enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val isError: Boolean = false,
    val latencyMs: Long? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    // Display only: a reply from an on-device model rather than a hosted
    // one. Always false in the current build.
    val isLocalModel: Boolean = false,
)
