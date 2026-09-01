package org.iutools.app

/*
 * Aggregate performance stats per backend/model for Guess Meaning: average
 * latency, average input tokens, average output tokens. Deliberately only
 * fed from real backend calls (see the call site in GuessMeaningEngine.kt's
 * send(), inside the Claude success branch) -- a conversation replayed from
 * the cache (see GuessMeaningConversationKey) never reaches that call site,
 * so it's excluded automatically rather than needing an explicit check.
 *
 * Keyed by a per-backend/model label (currently only ever the Claude model
 * constant) -- the on-device backend this once also tracked, keyed by its
 * model file's name so different local models tracked separately, is
 * disabled; see composeApp/disabled-features/local-llm/README.md.
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
