package org.iutools.lib.testing

import kotlin.test.fail

/**
 * Port of ca.nrc.testing.AssertNumber.performanceHasNotChanged from
 * https://github.com/nrc-cnrc/java-utils (java-utils-core), trimmed to the
 * two overloads this project actually calls:
 *
 *   performanceHasNotChanged(ofWhat, gotPerf, oldPerf, tolerance, highIsGood)
 *       -- the accuracy test's outcome-count checks
 *   performanceHasNotChanged(ofWhat, gotPerf, oldPerf, tolerances, highIsGood, mess)
 *       -- AssertRuntime's per-machine timing baseline check
 *
 * Faithful translation of the real algorithm (not a guess): it computes a
 * signed delta = got - old, classifies it as WORSENED/IMPROVED/SAME
 * depending on the delta's sign and `highIsGood`, and — for a delta that
 * isn't exactly zero — fails if `abs(delta) > tolerance` (tolerance used
 * as-is, NOT abs(tolerance)). A change in EITHER direction (worse or
 * better) can fail the assertion, not just a regression.
 *
 * Note: because the comparison is `absDelta > tolerance` with the raw
 * (possibly negative) tolerance value, a tolerance <= 0 makes any nonzero
 * delta fail (0 <= x is always > a negative or zero number for x > 0),
 * while an exact-zero delta always passes regardless of tolerance (the
 * SAME case is never even checked against tolerance). This reproduces
 * iutools' tolerance_CORRECT_NOT_FIRST = -0.05 behaving like "flag ANY
 * change at all", same as the 0.0 tolerances on the other two metrics.
 */
object AssertNumber {
    private enum class PerfChange { SAME, WORSENED, IMPROVED }

    fun performanceHasNotChanged(
        ofWhat: String,
        gotPerf: Double,
        oldPerf: Double,
        tolerance: Double,
        highIsGood: Boolean,
    ) {
        val delta = gotPerf - oldPerf
        val absDelta = Math.abs(delta)
        val changeType = when {
            delta < 0 -> if (highIsGood) PerfChange.WORSENED else PerfChange.IMPROVED
            delta > 0 -> if (highIsGood) PerfChange.IMPROVED else PerfChange.WORSENED
            else -> PerfChange.SAME
        }

        if (changeType == PerfChange.SAME) return

        if (absDelta > tolerance) {
            val changeTypeStr = if (changeType == PerfChange.WORSENED) "WORSENED" else "IMPROVED"
            val mess = "\nPerformance of '$ofWhat' has significantly $changeTypeStr\n" +
                "New performance : $gotPerf\n" +
                "Old performance : $oldPerf\n" +
                "Delta           : $absDelta\n" +
                "Max tolerance   : $tolerance"
            fail(mess)
        }
    }

    /**
     * Port of the ca.nrc.testing.AssertNumber.performanceHasNotChanged
     * overload that takes SEPARATE absolute tolerances for a worsening
     * (`tolerances.first`) versus an improvement (`tolerances.second`).
     * Either tolerance may be null, meaning "do not flag a change in that
     * direction at all". `mess` is a caller-supplied prefix prepended to the
     * failure message. Used by AssertRuntime.
     *
     * As in the single-tolerance overload above, an exact-zero delta always
     * passes, and the comparison is `absDelta > tolerance` with the raw
     * tolerance value.
     */
    fun performanceHasNotChanged(
        ofWhat: String,
        gotPerf: Double,
        oldPerf: Double,
        tolerances: Pair<Double?, Double?>,
        highIsGood: Boolean,
        mess: String,
    ) {
        val delta = gotPerf - oldPerf
        val absDelta = Math.abs(delta)
        val changeType = when {
            delta < 0 -> if (highIsGood) PerfChange.WORSENED else PerfChange.IMPROVED
            delta > 0 -> if (highIsGood) PerfChange.IMPROVED else PerfChange.WORSENED
            else -> PerfChange.SAME
        }
        if (changeType == PerfChange.SAME) return

        val changeLabel: String? = when (changeType) {
            PerfChange.WORSENED ->
                tolerances.first?.let { if (absDelta > it) "WORSENED" else null }
            else ->
                tolerances.second?.let { if (absDelta > it) "IMPROVED" else null }
        }
        if (changeLabel != null) {
            fail(
                mess +
                    "\nPerformance of '$ofWhat' has significantly $changeLabel\n" +
                    "New performance : $gotPerf\n" +
                    "Old performance : $oldPerf\n" +
                    "Delta           : $absDelta\n" +
                    "Max tolerances  : worsening <= ${tolerances.first}, improv. <= ${tolerances.second}"
            )
        }
    }
}
