package org.iutools.llm

/*
 * Estimate the USD cost of one LLM call from its token counts. The answer
 * is null when the model's price isn't known.
 *
 * The price table is maintained by hand from the providers' published
 * rates.
 */

private data class ModelPricing(val inputPerMillionUsd: Double, val outputPerMillionUsd: Double)

private val PRICING_PER_MILLION_TOKENS_USD = mapOf(
    "claude-haiku-4-5" to ModelPricing(inputPerMillionUsd = 1.0, outputPerMillionUsd = 5.0),
)

fun estimatedCostUsd(model: String, inputTokens: Long, outputTokens: Long): Double? {
    val pricing = PRICING_PER_MILLION_TOKENS_USD[model] ?: return null
    return (inputTokens / 1_000_000.0) * pricing.inputPerMillionUsd +
        (outputTokens / 1_000_000.0) * pricing.outputPerMillionUsd
}
