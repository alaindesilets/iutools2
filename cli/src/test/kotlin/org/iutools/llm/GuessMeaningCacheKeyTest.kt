package org.iutools.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/*
 * These keys exist to decide "same request -> reuse the cached reply".
 * The tests pin exactly which fields take part in that decision: an
 * identical key must compare equal, and changing any one field must make
 * it a different key.
 */
class GuessMeaningCacheKeyTest {

    private val word = GuessMeaningCacheKey(word = "iglu", lenient = false)

    private val conversation = GuessMeaningConversationKey(
        word = word,
        isLocalModel = false,
        meaningLanguage = MeaningLanguage.ENGLISH,
        systemPrompt = "You are a helpful assistant.",
        seedMessage = "What does iglu mean?",
    )

    @Test
    fun cacheKey_sameWordAndLenient_areEqual() {
        assertEquals(GuessMeaningCacheKey("iglu", false), word)
    }

    @Test
    fun cacheKey_differentWord_areNotEqual() {
        assertNotEquals(word, word.copy(word = "iglut"))
    }

    @Test
    fun cacheKey_differentLenientFlag_areNotEqual() {
        assertNotEquals(word, word.copy(lenient = true))
    }

    @Test
    fun conversationKey_identicalFields_areEqual() {
        assertEquals(conversation.copy(), conversation)
    }

    @Test
    fun conversationKey_differentWord_areNotEqual() {
        assertNotEquals(conversation, conversation.copy(word = word.copy(word = "iglut")))
    }

    @Test
    fun conversationKey_differentBackend_areNotEqual() {
        assertNotEquals(conversation, conversation.copy(isLocalModel = true))
    }

    @Test
    fun conversationKey_differentLanguage_areNotEqual() {
        assertNotEquals(conversation, conversation.copy(meaningLanguage = MeaningLanguage.FRENCH))
    }

    @Test
    fun conversationKey_differentSystemPrompt_areNotEqual() {
        assertNotEquals(conversation, conversation.copy(systemPrompt = "Answer in one word."))
    }

    @Test
    fun conversationKey_differentSeedMessage_areNotEqual() {
        assertNotEquals(conversation, conversation.copy(seedMessage = "Define iglu."))
    }
}
