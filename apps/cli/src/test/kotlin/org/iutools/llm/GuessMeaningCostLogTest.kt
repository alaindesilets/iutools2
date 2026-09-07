package org.iutools.llm

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * GuessMeaningCostLog with an in-memory store and a fixed time zone -- no
 * Android, no real clock. Checks the day/week/month bucketing, per-model
 * separation, and the write-time pruning.
 */
class GuessMeaningCostLogTest {

    private class InMemoryStore(var content: String = "") : CostLogStore {
        override fun read(): String = content
        override fun write(value: String) {
            content = value
        }
    }

    private val zone = ZoneId.of("UTC")
    private val store = InMemoryStore()
    private val log = GuessMeaningCostLog(store, zone)

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun accumulatedByModel_bucketsEntriesByDayWeekAndMonth() {
        // Anchor: Friday 2026-08-14, 15:00 -- the week's Monday is 2026-08-10.
        val now = at(2026, 8, 14, 15)

        log.record("claude-haiku-4-5", 0.001, at(2026, 8, 14, 9))  // same day
        log.record("claude-haiku-4-5", 0.002, at(2026, 8, 13, 10)) // this week, not today
        log.record("claude-haiku-4-5", 0.004, at(2026, 8, 1, 10))  // this month, previous week
        log.record("claude-haiku-4-5", 0.008, at(2026, 7, 15, 10)) // last month -- counts nowhere

        val accumulated = log.accumulatedByModel(now).getValue("claude-haiku-4-5")

        assertEquals(0.001, accumulated.today, 1e-9)
        assertEquals(0.003, accumulated.thisWeek, 1e-9)
        assertEquals(0.007, accumulated.thisMonth, 1e-9)
    }

    @Test
    fun accumulatedByModel_keepsDifferentModelsSeparate() {
        val now = at(2026, 8, 14, 15)

        log.record("claude-haiku-4-5", 0.01, now)
        log.record("gpt-4", 0.05, now)

        val byModel = log.accumulatedByModel(now)

        assertEquals(0.01, byModel.getValue("claude-haiku-4-5").today, 1e-9)
        assertEquals(0.05, byModel.getValue("gpt-4").today, 1e-9)
        // A model never recorded simply has no entry.
        assertNull(byModel["some-local-model.litertlm"])
    }

    @Test
    fun record_dropsEntriesFromBeforeTheStartOfTheNewestEntrysMonth() {
        log.record("m", 0.008, at(2026, 7, 15, 10))
        log.record("m", 0.001, at(2026, 8, 2, 10))

        // The July line was pruned when the August one was written.
        assertTrue(store.content.lineSequence().none { it.contains(":0.008:") })
        assertEquals(0.001, log.accumulatedByModel(at(2026, 8, 14, 15)).getValue("m").thisMonth, 1e-9)
    }

    @Test
    fun modelNameContainingColons_isKeptWholeThroughAWriteAndRead() {
        val now = at(2026, 8, 14, 15)

        log.record("some/model:v2:beta", 0.02, now)

        assertEquals(0.02, log.accumulatedByModel(now).getValue("some/model:v2:beta").today, 1e-9)
    }

    @Test
    fun accumulatedByModel_emptyLog_returnsEmptyMap() {
        assertEquals(emptyMap(), log.accumulatedByModel(at(2026, 8, 14, 15)))
    }
}
