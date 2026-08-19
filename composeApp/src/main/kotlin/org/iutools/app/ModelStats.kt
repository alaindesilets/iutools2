package org.iutools.app

/*
 * Aggregate performance stats per backend/model for Guess Meaning, per
 * Alain's request while comparing Claude against the local model: average
 * latency, average input tokens, average output tokens. Deliberately only
 * fed from real backend calls (see the call sites in GuessMeaningScreen.kt --
 * inside send()'s Claude success branch and sendToLocalModel()'s
 * LocalGenerationEvent.Done handling) -- a conversation replayed from the
 * cache (see GuessMeaningConversationKey) never reaches those call sites, so
 * it's excluded automatically rather than needing an explicit check.
 *
 * Keyed by a per-backend/model label (the Claude model constant, or the
 * local model file's name -- see ModelStats key construction in
 * GuessMeaningScreen.kt) so switching to a different local model file tracks
 * separately rather than blending into one "local model" bucket.
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
