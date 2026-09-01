package org.iutools.lib.testing

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

/**
 * Port of ca.nrc.testing.AssertRuntimeTest from
 * https://github.com/nrc-cnrc/java-utils (java-utils-core). Uses the JUnit
 * 5 API directly (TestInfo injection, assertThrows) rather than
 * kotlin.test, since the whole mechanism is built on TestInfo.
 */
class AssertRuntimeTest {

    private lateinit var testInfo: TestInfo

    @BeforeEach
    fun setUp(info: TestInfo) {
        this.testInfo = info
        AssertRuntime.clearAllExpTimes(testInfo)
    }

    //////////////////////////////////////
    // DOCUMENTATION TESTS
    //////////////////////////////////////

    @Test
    fun test__AssertRuntime__Synopsis() {
        // Use this class to check that the runtime of a particular operation
        // has not changed significantly compared with a previously generated
        // baseline.
        //
        // Here we assert that the previously saved benchmark for operation
        // 'some operation' is within 1% of the current runtime of 3.0.
        val operationName = "some operation"
        val currentRuntime = 3.0
        AssertRuntime.runtimeHasNotChanged(currentRuntime, 0.01, operationName, testInfo)

        // The first time you run a runtime assertion for a given operation in
        // a given test, the assertion ALWAYS succeeds: there is no saved
        // benchmark yet, so the system saves the current runtime as the
        // benchmark for this operation in this test. The benchmarks for a
        // given test live in this file:
        AssertRuntime.benchmarksFile(testInfo)

        // Afterwards, asserting the runtime for the same operation in the
        // same test method fails if the new runtime is significantly
        // different from the one previously saved.
        assertThrows(AssertionError::class.java) {
            val muchLowerRuntime = currentRuntime - 1.0
            AssertRuntime.runtimeHasNotChanged(muchLowerRuntime, 0.01, "some operation", testInfo)
        }

        // You can provide a different tolerance for improvements versus
        // worsenings of the runtime. Here we are more tolerant of
        // improvements than of worsenings.
        val toleranceWorsening = 0.01
        val toleranceImprov = 0.05
        val tolerances: Pair<Double?, Double?> = Pair(toleranceWorsening, toleranceImprov)
        AssertRuntime.runtimeHasNotChanged(currentRuntime, tolerances, operationName, testInfo)

        // By default, runtimeHasNotChanged() assumes that a high value is bad
        // and a low value is good. You can override that.
        val highIsBad = false
        AssertRuntime.runtimeHasNotChanged(currentRuntime, tolerances, "time to failure", testInfo, highIsBad)
    }

    //////////////////////////////////////
    // VERIFICATION TESTS
    //////////////////////////////////////

    @Test
    fun test__AssertRuntime__NoExpectedTimeToStartWith() {
        val ofWhat = "some method"
        val gotTime = 1000.0

        AssertRuntime.clearExpTimeFor(ofWhat, testInfo)

        // First time we check runtime, it should be fine: no time
        // expectation has been logged for that operation yet.
        AssertRuntime.runtimeHasNotChanged(gotTime, 0.01, ofWhat, testInfo)

        // If the runtime has increased more than the tolerance, the
        // assertion should fail.
        assertThrows(AssertionError::class.java) {
            AssertRuntime.runtimeHasNotChanged(2000.0, 0.01, ofWhat, testInfo)
        }

        // If the runtime has DECREASED more than the tolerance, the
        // assertion should fail.
        assertThrows(AssertionError::class.java) {
            AssertRuntime.runtimeHasNotChanged(100.0, 0.01, ofWhat, testInfo)
        }

        // The assertion should succeed if the new runtime is within
        // tolerance.
        AssertRuntime.runtimeHasNotChanged(1000.0 + 2, 0.01, ofWhat, testInfo)
        AssertRuntime.runtimeHasNotChanged(1000.0 - 2, 0.01, ofWhat, testInfo)
    }

    @Test
    fun test__runtimeHasNotChanged__SingleTolerance() {
        doRuntimeHasNotChanged(
            "Should NOT fail because current is EQUAL to baseline",
            currentRuntime = 32.0, baselineRuntime = 32.0, percTolerance = 0.1,
        )

        doRuntimeHasNotChanged(
            "Should NOT fail because IMPROVEMENT is NOT significant",
            currentRuntime = 31.0, baselineRuntime = 31.2, percTolerance = 0.1,
        )

        doRuntimeHasNotChanged(
            "Should NOT fail because WORSENING is NOT significant",
            currentRuntime = 32.0, baselineRuntime = 31.2, percTolerance = 0.1,
        )

        assertThrows(AssertionError::class.java) {
            doRuntimeHasNotChanged(
                "SHOULD fail because of significant IMPROVEMENT",
                currentRuntime = 30.0, baselineRuntime = 31.2, percTolerance = 0.01,
            )
        }

        assertThrows(AssertionError::class.java) {
            doRuntimeHasNotChanged(
                "Should fail because of significant WORSENING",
                currentRuntime = 34.0, baselineRuntime = 31.2, percTolerance = 0.01,
            )
        }
    }

    @Test
    fun test__performanceHasNotChanged__ImprAndWorseTolerances() {
        val tolerances: Pair<Double?, Double?> = Pair(0.05, 0.05)

        doRuntimeHasNotChanged(
            "Should NOT fail because current is EQUAL to baseline",
            currentRuntime = 32.0, baselineRuntime = 32.0, percTolerances = tolerances,
        )

        doRuntimeHasNotChanged(
            "Should NOT fail because IMPROVEMENT is NOT significant",
            currentRuntime = 31.1, baselineRuntime = 31.2, percTolerances = tolerances,
        )

        doRuntimeHasNotChanged(
            "Should NOT fail because WORSENING is NOT significant",
            currentRuntime = 31.0, baselineRuntime = 31.2, percTolerances = tolerances,
        )

        assertThrows(AssertionError::class.java) {
            doRuntimeHasNotChanged(
                "SHOULD fail because of significant IMPROVEMENT",
                currentRuntime = 29.0, baselineRuntime = 31.2, percTolerances = Pair(0.05, 0.05),
            )
        }

        assertThrows(AssertionError::class.java) {
            doRuntimeHasNotChanged(
                "Should fail because of significant WORSENING",
                currentRuntime = 34.0, baselineRuntime = 31.2, percTolerances = Pair(0.05, 0.05),
            )
        }
    }

    @Test
    fun test__performanceHasNotChanged__HighIsBad() {
        val tolerances: Pair<Double?, Double?> = Pair(0.05, 0.05)

        doRuntimeHasNotChanged(
            "Should NOT fail because current is EQUAL to baseline",
            currentRuntime = 32.0, baselineRuntime = 32.0, percTolerances = tolerances, highIsBad = false,
        )

        doRuntimeHasNotChanged(
            "Should NOT fail because IMPROVEMENT is NOT significant",
            currentRuntime = 31.2, baselineRuntime = 32.0, percTolerances = tolerances, highIsBad = false,
        )

        doRuntimeHasNotChanged(
            "Should NOT fail because WORSENING is NOT significant",
            currentRuntime = 31.2, baselineRuntime = 31.0, percTolerances = tolerances, highIsBad = false,
        )

        assertThrows(AssertionError::class.java) {
            doRuntimeHasNotChanged(
                "SHOULD fail because of significant IMPROVEMENT",
                currentRuntime = 29.0, baselineRuntime = 32.0, percTolerances = Pair(0.05, 0.05), highIsBad = false,
            )
        }

        assertThrows(AssertionError::class.java) {
            doRuntimeHasNotChanged(
                "Should fail because of significant WORSENING",
                currentRuntime = 33.2, baselineRuntime = 30.0, percTolerances = Pair(0.05, 0.05), highIsBad = false,
            )
        }
    }

    @Test
    fun test__performanceHasNotChanged__NullImprTolerance() {
        val tolerances: Pair<Double?, Double?> = Pair(null, 0.05)
        doRuntimeHasNotChanged(
            "Passing a null impr. tolerance should not cause a crash",
            currentRuntime = 10.0, baselineRuntime = 10.001, percTolerances = tolerances,
        )
    }

    @Test
    fun test__performanceHasNotChanged__NullWorsenedTolerance() {
        val tolerances: Pair<Double?, Double?> = Pair(0.05, null)
        doRuntimeHasNotChanged(
            "Passing a null worsened tolerance should not cause a crash",
            currentRuntime = 10.0, baselineRuntime = 10.001, percTolerances = tolerances,
        )
    }

    // This port replaces the original's Jackson ObjectMapper with a
    // hand-rolled reader/writer, so exercise a baseline round-trip through
    // the JSON file with an operation name that stresses the escaping:
    // quotes, backslash, comma, colon, braces, newline, tab, non-ASCII.
    @Test
    fun test__baseline_round_trips_through_json_with_an_awkward_operation_name() {
        val ofWhat = "decompose {\"a\": 1}, then\tb\\c — étkatik\n"

        AssertRuntime.setExpTimeFor(42.0, ofWhat, testInfo)

        // Re-read from disk: a matching time must be recognised as within
        // tolerance (i.e. the key and value survived the write/read).
        AssertRuntime.runtimeHasNotChanged(42.0, 0.01, ofWhat, testInfo)

        // And a wildly different time for that same key must still be caught
        // -- which only works if the baseline was actually found on re-read,
        // not silently treated as "no baseline yet".
        assertThrows(AssertionError::class.java) {
            AssertRuntime.runtimeHasNotChanged(4200.0, 0.01, ofWhat, testInfo)
        }
    }

    //////////////////////////////////////
    // TEST HELPERS
    //////////////////////////////////////

    // `mess` is intentionally unused: it documents the intent of each call
    // at the call site, exactly as in the original Java test.
    private fun doRuntimeHasNotChanged(
        mess: String,
        currentRuntime: Double,
        baselineRuntime: Double,
        percTolerance: Double,
    ) {
        doRuntimeHasNotChanged(mess, currentRuntime, baselineRuntime, Pair(percTolerance, percTolerance))
    }

    private fun doRuntimeHasNotChanged(
        mess: String,
        currentRuntime: Double,
        baselineRuntime: Double,
        percTolerances: Pair<Double?, Double?>,
        operationName: String = "some operation",
        highIsBad: Boolean? = null,
    ) {
        AssertRuntime.setExpTimeFor(baselineRuntime, operationName, testInfo)
        AssertRuntime.runtimeHasNotChanged(currentRuntime, percTolerances, operationName, testInfo, highIsBad)
    }
}
