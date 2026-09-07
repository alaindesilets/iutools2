package org.iutools.llm

/*
 * Per-model running averages of how the LLM backends are performing:
 * latency, and input/output token counts per call.
 */

data class BackendCallStats(
    val latencyMs: Long,
    val inputTokens: Long,
    val outputTokens: Long,
)

data class AggregatedBackendStats(
    val callCount: Int = 0,
    val totalLatencyMs: Long = 0,
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
) {
    val averageLatencySeconds: Double
        get() = if (callCount == 0) 0.0 else (totalLatencyMs.toDouble() / callCount) / 1000.0

    val averageInputTokens: Double
        get() = if (callCount == 0) 0.0 else totalInputTokens.toDouble() / callCount

    val averageOutputTokens: Double
        get() = if (callCount == 0) 0.0 else totalOutputTokens.toDouble() / callCount

    operator fun plus(call: BackendCallStats) = AggregatedBackendStats(
        callCount = callCount + 1,
        totalLatencyMs = totalLatencyMs + call.latencyMs,
        totalInputTokens = totalInputTokens + call.inputTokens,
        totalOutputTokens = totalOutputTokens + call.outputTokens,
    )
}
