package org.iutools.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelStatsTest {

    @Test
    fun aggregatedBackendStats_empty_hasZeroAverages() {
        val stats = AggregatedBackendStats()

        assertEquals(0, stats.callCount)
        assertEquals(0.0, stats.averageLatencySeconds, 0.0)
        assertEquals(0.0, stats.averageInputTokens, 0.0)
        assertEquals(0.0, stats.averageOutputTokens, 0.0)
    }

    @Test
    fun aggregatedBackendStats_plus_accumulatesTotalsAndCount() {
        val stats = AggregatedBackendStats() +
            BackendCallStats(latencyMs = 2000, inputTokens = 100, outputTokens = 50) +
            BackendCallStats(latencyMs = 4000, inputTokens = 200, outputTokens = 150)

        assertEquals(2, stats.callCount)
        // Long literals (not Int) -- assertEquals(Int, Long) resolves to the
        // Object-overload and boxes each side differently, so it silently
        // never passes even when the values match numerically.
        assertEquals(6000L, stats.totalLatencyMs)
        assertEquals(300L, stats.totalInputTokens)
        assertEquals(200L, stats.totalOutputTokens)
    }

    @Test
    fun aggregatedBackendStats_plus_averagesDivideByCallCount() {
        val stats = AggregatedBackendStats() +
            BackendCallStats(latencyMs = 2000, inputTokens = 100, outputTokens = 50) +
            BackendCallStats(latencyMs = 4000, inputTokens = 200, outputTokens = 150)

        // (2000 + 4000) ms / 2 calls = 3000 ms = 3.0 s
        assertEquals(3.0, stats.averageLatencySeconds, 0.0001)
        assertEquals(150.0, stats.averageInputTokens, 0.0001)
        assertEquals(100.0, stats.averageOutputTokens, 0.0001)
    }
}
