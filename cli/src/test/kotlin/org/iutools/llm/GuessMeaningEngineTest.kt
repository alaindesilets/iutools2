package org.iutools.llm

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * GuessMeaningEngine with a fake LlmClient: checks what it appends to the
 * conversation and what it returns for each kind of response, and that it
 * reports the user's turn before the call and never reaches the model on
 * the no-send paths.
 */
class GuessMeaningEngineTest {

    // A stand-in LLM: hands back whatever response it was built with, and
    // remembers whether (and with what) it was asked.
    private class FakeLlmClient(private val response: LlmResponse) : LlmClient {
        var callCount = 0
        var lastRequest: LlmRequest? = null

        override suspend fun send(request: LlmRequest): LlmResponse {
            callCount++
            lastRequest = request
            return response
        }
    }

    private val labels = GuessMeaningErrorLabels(
        unauthorized = "Your API key was refused.",
        localModelDisabled = "The on-device model is disabled.",
        genericTemplate = "Something went wrong: %1\$s",
    )

    private class SendRun(val outcome: CallOutcome?, val updates: List<List<ChatMessage>>) {
        val finalMessages: List<ChatMessage> get() = updates.last()
        val lastTurn: ChatMessage get() = finalMessages.last()
    }

    // Drives one GuessMeaningEngine.send() call, capturing every list it
    // pushes to onMessagesChanged and the value it returns.
    private fun sendUserTurn(
        client: FakeLlmClient,
        history: List<ChatMessage> = emptyList(),
        userText: String = "What does iglu mean?",
        useLocalModel: Boolean = false,
    ): SendRun {
        val updates = mutableListOf<List<ChatMessage>>()
        val outcome = runBlocking {
            GuessMeaningEngine(client).send(
                history = history,
                userText = userText,
                systemPrompt = SYSTEM_PROMPT,
                useLocalModel = useLocalModel,
                labels = labels,
                onMessagesChanged = { updates.add(it) },
            )
        }
        return SendRun(outcome, updates)
    }

    @Test
    fun send_okResponse_appendsTheAssistantReplyWithItsTimingAndTokens() {
        val client = FakeLlmClient(LlmResponse.Ok("a house", latencyMs = 1200, inputTokens = 40, outputTokens = 8))

        val run = sendUserTurn(client)

        assertEquals(ChatRole.ASSISTANT, run.lastTurn.role)
        assertEquals("a house", run.lastTurn.text)
        assertEquals(false, run.lastTurn.isError)
        assertEquals(1200L, run.lastTurn.latencyMs)
        assertEquals(40L, run.lastTurn.inputTokens)
        assertEquals(8L, run.lastTurn.outputTokens)
    }

    @Test
    fun send_okResponse_returnsTheOutcomeForTheCaller() {
        val client = FakeLlmClient(LlmResponse.Ok("a house", latencyMs = 1200, inputTokens = 40, outputTokens = 8))

        val outcome = sendUserTurn(client).outcome

        assertEquals(CallOutcome(GuessMeaningEngine.MODEL, 1200, 40, 8), outcome)
    }

    @Test
    fun send_reportsTheUserTurnBeforeCallingTheModel() {
        val client = FakeLlmClient(LlmResponse.Ok("a house", 1, 1, 1))

        val run = sendUserTurn(client, userText = "What does iglu mean?")

        // First push: just the user's turn, no reply yet.
        val firstPush = run.updates.first()
        assertEquals(1, firstPush.size)
        assertEquals(ChatRole.USER, firstPush.single().role)
        assertEquals("What does iglu mean?", firstPush.single().text)
    }

    @Test
    fun send_okResponse_requestCarriesTheModelSystemPromptAndWholeHistory() {
        val client = FakeLlmClient(LlmResponse.Ok("a house", 1, 1, 1))
        val history = listOf(
            ChatMessage(ChatRole.USER, "earlier question"),
            ChatMessage(ChatRole.ASSISTANT, "earlier answer"),
        )

        sendUserTurn(client, history = history, userText = "follow-up")

        val request = client.lastRequest!!
        assertEquals(GuessMeaningEngine.MODEL, request.model)
        assertEquals(GuessMeaningEngine.MAX_TOKENS, request.maxTokens)
        assertEquals(SYSTEM_PROMPT, request.systemPrompt)
        assertEquals(listOf("earlier question", "earlier answer", "follow-up"), request.messages.map { it.text })
    }

    @Test
    fun send_unauthorized_appendsTheUnauthorizedNoticeAsAnErrorAndReturnsNull() {
        val client = FakeLlmClient(LlmResponse.Unauthorized)

        val run = sendUserTurn(client)

        assertEquals("Your API key was refused.", run.lastTurn.text)
        assertTrue(run.lastTurn.isError)
        assertNull(run.outcome)
    }

    @Test
    fun send_failed_wrapsTheFailureMessageInTheTemplateAsAnErrorAndReturnsNull() {
        val client = FakeLlmClient(LlmResponse.Failed("Unable to resolve host api.anthropic.com"))

        val run = sendUserTurn(client)

        assertEquals("Something went wrong: Unable to resolve host api.anthropic.com", run.lastTurn.text)
        assertTrue(run.lastTurn.isError)
        assertNull(run.outcome)
    }

    @Test
    fun send_blankUserText_doesNothingAndNeverTouchesTheModel() {
        val client = FakeLlmClient(LlmResponse.Ok("unused", 1, 1, 1))

        val run = sendUserTurn(client, userText = "   ")

        assertNull(run.outcome)
        assertEquals(0, client.callCount)
        assertTrue(run.updates.isEmpty())
    }

    @Test
    fun send_useLocalModel_appendsTheDisabledNoticeWithoutCallingTheModel() {
        val client = FakeLlmClient(LlmResponse.Ok("unused", 1, 1, 1))

        val run = sendUserTurn(client, useLocalModel = true)

        assertEquals("The on-device model is disabled.", run.lastTurn.text)
        assertTrue(run.lastTurn.isError)
        assertEquals(0, client.callCount)
        assertNull(run.outcome)
    }

    companion object {
        private const val SYSTEM_PROMPT = "You explain Inuktitut words."
    }
}
