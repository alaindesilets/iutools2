package org.iutools.script

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/*
 * Port of org.iutools.script.SyllabicsTest (Java). Only
 * `syllabicCharsRatio` has a real test in the original -- the rest of
 * Syllabics.kt's coverage comes from its use inside the R2L analyzer's own
 * test suite.
 */
class SyllabicsTest {

    @Test
    fun syllabicCharsRatio_happyPath() {
        val text = "I need some ᑮᓇᐅᔭᖅ"
        val gotRatio = Syllabics.syllabicCharsRatio(text)
        assertTrue(abs(gotRatio - 0.36) < 0.01, "Ratio of syllabic characters was wrong: $gotRatio")
    }
}
