package org.iutools.llm

import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * causeChainMessage() turns a wrapped exception into one readable line.
 * These pin the join, the de-duplication, and the fallback.
 */
class CauseChainMessageTest {

    @Test
    fun causeChainMessage_singleException_isItsOwnMessage() {
        assertEquals("boom", causeChainMessage(RuntimeException("boom")))
    }

    @Test
    fun causeChainMessage_nestedDistinctMessages_areJoinedOutermostFirst() {
        val inner = RuntimeException("Unable to resolve host api.anthropic.com")
        val outer = RuntimeException("Request failed", inner)

        assertEquals("Request failed: Unable to resolve host api.anthropic.com", causeChainMessage(outer))
    }

    @Test
    fun causeChainMessage_repeatedMessageInTheChain_appearsOnce() {
        val inner = RuntimeException("Request failed")
        val outer = RuntimeException("Request failed", inner)

        assertEquals("Request failed", causeChainMessage(outer))
    }

    @Test
    fun causeChainMessage_linkWithNoMessage_isSkipped() {
        val inner = RuntimeException("real reason")
        val middle = RuntimeException(null as String?, inner)
        val outer = RuntimeException("top", middle)

        assertEquals("top: real reason", causeChainMessage(outer))
    }

    @Test
    fun causeChainMessage_nothingInChainHasAMessage_fallsBackToToString() {
        val error = IllegalStateException()

        assertEquals(error.toString(), causeChainMessage(error))
    }
}
