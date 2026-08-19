package org.iutools.app

import android.content.Context
import java.util.Calendar

/*
 * Estimated USD cost of Guess Meaning's online-model calls, per Alain's
 * request to see roughly how much this is costing him: the cost of the
 * current word's conversation, plus running totals for today/this week/this
 * month. "Estimated" (his own word) because it's derived from
 * response.usage() token counts and the price table below, not read back
 * from Anthropic's own billing. Local-model calls are never priced --
 * estimatedCostUsd() returns null for any model not in
 * PRICING_PER_MILLION_TOKENS_USD, which GuessMeaningScreen.kt takes as
 * "don't show a cost for this reply".
 *
 * Broken down **per model**, not one lumped total: Alain is planning to try
 * other online models (GPT-4, Qwen, Gemini, ...) alongside Claude to compare
 * them, at per-token prices that can differ from Haiku's by 3-10x, so a
 * single combined total would hide which model is actually driving the
 * spend. A local model simply never appears in these totals (no entries are
 * ever recorded for one), which is the same as saying it costs $0.
 *
 * Day/week/month totals need to survive app restarts to mean anything (a
 * "today's total" that resets on every launch isn't useful), so each real
 * call's cost is appended to a small persisted log (SharedPreferences, same
 * approach as AppSettings.kt) rather than kept only in memory -- pruned back
 * to the last month's worth on every write, since that's the widest window
 * ever queried.
 */

private data class ModelPricing(val inputPerMillionUsd: Double, val outputPerMillionUsd: Double)

// $/1M tokens, from Anthropic's published pricing at the time this was
// written -- update here if MODEL (GuessMeaningScreen.kt) changes model, or
// Anthropic changes its prices. Add an entry here for each new online model
// as it's wired up (see this file's header comment) -- a model with no entry
// is simply never priced (estimatedCostUsd() returns null for it).
private val PRICING_PER_MILLION_TOKENS_USD = mapOf(
    "claude-haiku-4-5" to ModelPricing(inputPerMillionUsd = 1.0, outputPerMillionUsd = 5.0),
)

fun estimatedCostUsd(model: String, inputTokens: Long, outputTokens: Long): Double? {
    val pricing = PRICING_PER_MILLION_TOKENS_USD[model] ?: return null
    return (inputTokens / 1_000_000.0) * pricing.inputPerMillionUsd +
        (outputTokens / 1_000_000.0) * pricing.outputPerMillionUsd
}

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
