package org.iutools.llm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/*
 * A persisted running tally of what "Guess Meaning" has spent on LLM calls,
 * broken down per model and per time window (today / this week / this
 * month).
 *
 * It is persisted because a "today's total" that resets on every app launch
 * would be useless. Per model, not one lump sum, because different models'
 * per-token prices differ severalfold and a combined figure would hide
 * which one is driving the cost. A model with no recorded calls simply has
 * no entry, which callers read as $0.
 *
 * The log is a small blob of "timestampMillis:costUsd:model" lines. Where
 * that blob lives is the caller's problem -- it supplies a [CostLogStore]
 * (SharedPreferences in the app, an in-memory string in a test). Each
 * [record] also prunes anything older than the start of the current month,
 * so the blob stays bounded.
 */
class GuessMeaningCostLog(
    private val store: CostLogStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** Records one real (non-cached) online-model call's estimated cost. */
    fun record(model: String, costUsd: Double, atMillis: Long = System.currentTimeMillis()) {
        val kept = parseEntries(store.read()).filter { it.atMillis >= startOfMonth(atMillis) }
        store.write(serializeEntries(kept + Entry(atMillis, costUsd, model)))
    }

    /**
     * Accumulated cost per model as of [atMillis]. A model with no recorded
     * calls has no entry -- treat a missing key as [AccumulatedCosts] (all
     * zero), not as an error.
     */
    fun accumulatedByModel(atMillis: Long = System.currentTimeMillis()): Map<String, AccumulatedCosts> {
        val dayStart = startOfDay(atMillis)
        val weekStart = startOfWeek(atMillis)
        val monthStart = startOfMonth(atMillis)
        return parseEntries(store.read()).groupBy { it.model }.mapValues { (_, entries) ->
            AccumulatedCosts(
                today = entries.filter { it.atMillis >= dayStart }.sumOf { it.costUsd },
                thisWeek = entries.filter { it.atMillis >= weekStart }.sumOf { it.costUsd },
                thisMonth = entries.filter { it.atMillis >= monthStart }.sumOf { it.costUsd },
            )
        }
    }

    data class AccumulatedCosts(
        val today: Double = 0.0,
        val thisWeek: Double = 0.0,
        val thisMonth: Double = 0.0,
    )

    private data class Entry(val atMillis: Long, val costUsd: Double, val model: String)

    // "timestampMillis:costUsd:model" per line. The model is last and left
    // un-escaped -- it's the only field that can contain arbitrary
    // characters (a local model's exact filename, in principle) -- so
    // parsing splits on the first two colons only. Plain lines rather than
    // a set, so two calls that happen to match on timestamp/cost/model
    // aren't silently deduplicated.
    private fun parseEntries(raw: String): List<Entry> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split(':', limit = 3)
            if (parts.size != 3) return@mapNotNull null
            val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
            val cost = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            Entry(timestamp, cost, parts[2])
        }.toList()

    private fun serializeEntries(entries: List<Entry>): String =
        entries.joinToString("\n") { (timestamp, cost, model) -> "$timestamp:$cost:$model" }

    private fun dateOf(atMillis: Long): LocalDate =
        Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()

    private fun LocalDate.startOfDayMillis(): Long =
        atStartOfDay(zone).toInstant().toEpochMilli()

    private fun startOfDay(atMillis: Long): Long = dateOf(atMillis).startOfDayMillis()

    // Monday-based, regardless of locale -- "this week" must not mean
    // different things depending where the app runs.
    private fun startOfWeek(atMillis: Long): Long =
        dateOf(atMillis).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).startOfDayMillis()

    private fun startOfMonth(atMillis: Long): Long =
        dateOf(atMillis).withDayOfMonth(1).startOfDayMillis()
}

/**
 * Where [GuessMeaningCostLog] reads and writes its raw text blob. Backed by
 * SharedPreferences in the app; an in-memory string in tests.
 */
interface CostLogStore {
    fun read(): String
    fun write(value: String)
}
