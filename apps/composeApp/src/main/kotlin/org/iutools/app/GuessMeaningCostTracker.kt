package org.iutools.app

import android.content.Context
import java.util.Calendar

/*
 * Persisted running totals of Guess Meaning's estimated LLM spend, per
 * model, for today / this week / this month -- they need to survive app
 * restarts to mean anything (a "today's total" that resets on every launch
 * isn't useful). Each real call's cost is appended to a small
 * SharedPreferences-backed log (same approach as AppSettings.kt), pruned to
 * the last month's worth on every write.
 *
 * Per model, not one lumped total: different models' per-token prices can
 * differ severalfold, so a combined figure would hide which one is driving
 * the spend. A model with no recorded calls simply has no entry, which
 * reads as $0.
 *
 * The per-call cost figure comes from estimatedCostUsd() (org.iutools.llm).
 */

object GuessMeaningCostLog {
    private const val PREFS_NAME = "guess_meaning_cost_log"
    private const val KEY_ENTRIES = "entries"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun Calendar.atStartOfDay(): Calendar = apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun startOfDay(atMillis: Long): Long =
        Calendar.getInstance().apply { timeInMillis = atMillis }.atStartOfDay().timeInMillis

    // Monday-based, regardless of the device's locale -- avoids "this week"
    // silently meaning different things depending where the app runs.
    private fun startOfWeek(atMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = atMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }.atStartOfDay().timeInMillis

    private fun startOfMonth(atMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = atMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }.atStartOfDay().timeInMillis

    private data class Entry(val atMillis: Long, val costUsd: Double, val model: String)

    // "timestampMillis:costUsd:model" per line, model last and un-escaped --
    // it's the only field that can contain arbitrary characters (a local
    // model's exact .litertlm filename, in principle), so parsing splits on
    // the first two colons only and leaves the rest of the line intact as
    // the model field. Plain text instead of SharedPreferences' own
    // putStringSet(), which dedupes entries and would silently drop a call
    // that happened to match an earlier one's exact timestamp/cost/model.
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

    /** Records one real (non-cached) online-model call's estimated cost. */
    fun record(context: Context, model: String, costUsd: Double, atMillis: Long = System.currentTimeMillis()) {
        val existing = parseEntries(prefs(context).getString(KEY_ENTRIES, "") ?: "")
        val cutoff = startOfMonth(atMillis)
        val pruned = existing.filter { it.atMillis >= cutoff }
        prefs(context).edit().putString(KEY_ENTRIES, serializeEntries(pruned + Entry(atMillis, costUsd, model))).apply()
    }

    data class AccumulatedCosts(val today: Double = 0.0, val thisWeek: Double = 0.0, val thisMonth: Double = 0.0)

    /**
     * Accumulated cost per model. A model with no recorded calls (never
     * priced, e.g. a local model, or an online model not yet tried) simply
     * has no entry in the returned map -- callers should treat a missing key
     * as `AccumulatedCosts()` (all zero), not as an error.
     */
    fun accumulatedByModel(context: Context, atMillis: Long = System.currentTimeMillis()): Map<String, AccumulatedCosts> {
        val entries = parseEntries(prefs(context).getString(KEY_ENTRIES, "") ?: "")
        val dayStart = startOfDay(atMillis)
        val weekStart = startOfWeek(atMillis)
        val monthStart = startOfMonth(atMillis)
        return entries.groupBy { it.model }.mapValues { (_, modelEntries) ->
            AccumulatedCosts(
                today = modelEntries.filter { it.atMillis >= dayStart }.sumOf { it.costUsd },
                thisWeek = modelEntries.filter { it.atMillis >= weekStart }.sumOf { it.costUsd },
                thisMonth = modelEntries.filter { it.atMillis >= monthStart }.sumOf { it.costUsd },
            )
        }
    }
}
