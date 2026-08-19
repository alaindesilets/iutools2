package org.iutools.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/*
 * estimatedCostUsd() is a pure function -- straightforward to test directly.
 * GuessMeaningCostLog needs a real Context for SharedPreferences, hence
 * Robolectric (see UiStringLocalizationTest.kt/SpaldingDictionaryTest.kt for
 * the same pattern already used elsewhere in this suite).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuessMeaningCostTrackerTest {

    @Test
    fun estimatedCostUsd_knownModel_pricesInputAndOutputTokensSeparately() {
        // claude-haiku-4-5: $1/1M input, $5/1M output (see the price table).
        val cost = estimatedCostUsd("claude-haiku-4-5", inputTokens = 1_000_000, outputTokens = 1_000_000)

        assertEquals(6.0, cost!!, 0.0001)
    }

    @Test
    fun estimatedCostUsd_unknownModel_returnsNull() {
        assertNull(estimatedCostUsd("some-local-model.litertlm", inputTokens = 100, outputTokens = 50))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun accumulatedByModel_bucketsEntriesByDayWeekAndMonth() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Anchor: Friday 2026-08-14, 15:00 -- the week's Monday is 2026-08-10.
        val now = at(2026, Calendar.AUGUST, 14, 15)

        // Same day: counts everywhere.
        GuessMeaningCostLog.record(context, "claude-haiku-4-5", 0.001, at(2026, Calendar.AUGUST, 14, 9))
        // Yesterday, same week: counts in week and month, not today.
        GuessMeaningCostLog.record(context, "claude-haiku-4-5", 0.002, at(2026, Calendar.AUGUST, 13, 10))
        // Earlier this month, previous week (Aug 1 2026 is a Saturday, in the
        // week starting Jul 27): counts in month only.
        GuessMeaningCostLog.record(context, "claude-haiku-4-5", 0.004, at(2026, Calendar.AUGUST, 1, 10))
        // Last month: counts nowhere.
        GuessMeaningCostLog.record(context, "claude-haiku-4-5", 0.008, at(2026, Calendar.JULY, 15, 10))

        val accumulated = GuessMeaningCostLog.accumulatedByModel(context, now).getValue("claude-haiku-4-5")

        assertEquals(0.001, accumulated.today, 0.0001)
        assertEquals(0.003, accumulated.thisWeek, 0.0001)
        assertEquals(0.007, accumulated.thisMonth, 0.0001)
    }

    @Test
    fun accumulatedByModel_keepsDifferentModelsSeparate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = at(2026, Calendar.AUGUST, 14, 15)

        GuessMeaningCostLog.record(context, "claude-haiku-4-5", 0.01, now)
        GuessMeaningCostLog.record(context, "gpt-4", 0.05, now)

        val byModel = GuessMeaningCostLog.accumulatedByModel(context, now)

        assertEquals(0.01, byModel.getValue("claude-haiku-4-5").today, 0.0001)
        assertEquals(0.05, byModel.getValue("gpt-4").today, 0.0001)
        // A model that was never recorded (e.g. a local model, never priced)
        // simply has no entry -- callers default it to zero themselves.
        assertNull(byModel["some-local-model.litertlm"])
    }
}
