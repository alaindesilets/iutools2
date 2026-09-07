package org.iutools.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LlmCostTest {

    // claude-haiku-4-5's rates, from the price table: $1 per 1M input
    // tokens, $5 per 1M output tokens.
    private val model = "claude-haiku-4-5"

    @Test
    fun knownModel_addsInputCostAndOutputCost() {
        // 1M input @ $1  +  1M output @ $5  =  $6
        assertEquals(6.0, estimatedCostUsd(model, inputTokens = 1_000_000, outputTokens = 1_000_000)!!, 1e-9)
    }

    @Test
    fun knownModel_inputTokensPricedAtTheInputRateOnly() {
        // 2M input, no output -> 2 * $1
        assertEquals(2.0, estimatedCostUsd(model, inputTokens = 2_000_000, outputTokens = 0)!!, 1e-9)
    }

    @Test
    fun knownModel_outputTokensPricedAtTheOutputRateOnly() {
        // 3M output, no input -> 3 * $5
        assertEquals(15.0, estimatedCostUsd(model, inputTokens = 0, outputTokens = 3_000_000)!!, 1e-9)
    }

    @Test
    fun knownModel_zeroTokens_isZeroNotNull() {
        // "no cost" and "price unknown" are different answers -- a priced
        // model with a zero-token call costs $0, it isn't null.
        assertEquals(0.0, estimatedCostUsd(model, inputTokens = 0, outputTokens = 0)!!, 1e-9)
    }

    @Test
    fun unknownModel_isNull() {
        assertNull(estimatedCostUsd("some-local-model.litertlm", inputTokens = 100, outputTokens = 50))
    }
}
