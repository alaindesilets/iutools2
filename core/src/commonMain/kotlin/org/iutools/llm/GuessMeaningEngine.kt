package org.iutools.llm

/*
 * Runs one turn of the "Guess Meaning" conversation with an LLM: takes the
 * conversation so far plus the user's new message, asks the model, and
 * appends the model's reply (or an error notice) to the conversation.
 *
 * It reports progress by calling [send]'s onMessagesChanged twice -- once
 * right away with the user's message added (so the UI can show it and a
 * spinner before the network call returns), then again with the final
 * state. It keeps no state of its own: no cache, no running totals, no
 * storage, no knowledge of which provider answers (that is the injected
 * [LlmClient]). When a call succeeds it returns a [CallOutcome] with the
 * timing and token counts, and the caller decides what to do with those
 * (update its stats, record the cost); on every other path it returns
 * null.
 *
 * The same instance backs both the normal inline flow and a debug
 * "resend this exact prompt" screen -- both just call [send] and write the
 * resulting message list wherever they keep the conversation.
 */
class GuessMeaningEngine(private val llm: LlmClient) {

    /**
     * Adds [userText] to [history] as a user turn, asks the model, and adds
     * its reply. [onMessagesChanged] is called with the updated list at
     * each step (user turn added; then reply or error added). [labels]
     * supplies the pre-translated text for the non-reply outcomes.
     *
     * Returns the [CallOutcome] of a successful call, or null when nothing
     * was sent (blank [userText], or [useLocalModel]) or the call did not
     * produce a reply (auth failure, or any other failure).
     */
    suspend fun send(
        history: List<ChatMessage>,
        userText: String,
        systemPrompt: String,
        // Dead in the shipped app -- the on-device backend this would
        // select is disabled and out-of-tree. Kept as an explicit guard so
        // a future caller that passes true gets the "disabled" notice
        // rather than silently reaching the hosted model.
        useLocalModel: Boolean,
        labels: GuessMeaningErrorLabels,
        onMessagesChanged: (List<ChatMessage>) -> Unit,
    ): CallOutcome? {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return null

        val withUserTurn = history + ChatMessage(ChatRole.USER, trimmed)
        onMessagesChanged(withUserTurn)

        if (useLocalModel) {
            onMessagesChanged(withUserTurn + errorTurn(labels.localModelDisabled))
            return null
        }

        val request = LlmRequest(MODEL, MAX_TOKENS, systemPrompt, withUserTurn)
        return when (val response = llm.send(request)) {
            is LlmResponse.Ok -> {
                onMessagesChanged(
                    withUserTurn + ChatMessage(
                        role = ChatRole.ASSISTANT,
                        text = response.text,
                        latencyMs = response.latencyMs,
                        inputTokens = response.inputTokens,
                        outputTokens = response.outputTokens,
                    ),
                )
                CallOutcome(MODEL, response.latencyMs, response.inputTokens, response.outputTokens)
            }

            LlmResponse.Unauthorized -> {
                onMessagesChanged(withUserTurn + errorTurn(labels.unauthorized))
                null
            }

            is LlmResponse.Failed -> {
                onMessagesChanged(withUserTurn + errorTurn(fillPlaceholder(labels.genericTemplate, response.message)))
                null
            }
        }
    }

    private fun errorTurn(text: String) =
        ChatMessage(role = ChatRole.ASSISTANT, text = text, isError = true)

    companion object {
        const val MODEL = "claude-haiku-4-5"
        const val MAX_TOKENS = 4096L
    }
}
