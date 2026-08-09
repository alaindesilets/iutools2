package org.iutools.script

import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Port of org.iutools.script.OrtographyTest (Java; misspelled -- the
 * original test class is "Ortography" without an 'h', unlike the class
 * under test, "Orthography"). Only 1 of the Java suite's 4 @Test methods is
 * ported:
 *  - test__trimTrailingConsonant__WordEndsWithConsonant and
 *    ...WordDoesNOTEndWithConsonant test Orthography.trimTrailingConsonant(),
 *    which isn't called anywhere else in the original codebase either
 *    (grepped: zero other call sites) -- genuinely dead code, correctly
 *    pruned, not a porting gap.
 *  - test__orthographyICI calls orthographyICI(word, isSyllabic = true),
 *    which routes to the *Syl() variant. Orthography.kt only ported the
 *    Lat variants (the analyzer always runs with USE_SYLLABICS=false), so
 *    the Syl variant throws UnsupportedOperationException by design --
 *    this test can't be ported without reintroducing dead code.
 */
class OrtographyTest {

    @Test
    fun orthographyICILat() {
        val word = "nunavut"
        val wordICI = Orthography.orthographyICILat(word)
        val expectedICI = "nunavut"
        assertEquals(expectedICI, wordICI)
    }
}
