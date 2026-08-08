package org.iutools.morph.r2l

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Regression coverage for a bug in stopAfterN(): the early-stop counter
 * (_decompsSoFar, a HashSet<DecompositionState>) counts raw search
 * candidates, but DecompositionState has no equals()/hashCode() override,
 * so the HashSet never deduplicates -- every candidate counts as distinct.
 * The final result only gets deduplicated/filtered afterwards, in
 * doDecompose()'s removeCombinedSuffixes()/removeMultiples() pipeline. So a
 * cap reached on raw candidates could collapse to fewer final decompositions
 * than the cap, even fewer than the word's true (uncapped) total.
 */
class MorphologicalAnalyzer_R2L__StopAfterNTest {

    @Test
    fun stopAfterN_withCapAboveTrueTotal_returnsTheFullSet() {
        // "tullia" was the word that first exposed the bug (2026-08-08):
        // capped at 4 (above its true total of 3), it returned only 2.
        val word = "tullia"

        val full = MorphologicalAnalyzer_R2L().decomposeWord(word, false)
        val totalAvailable = full.size
        assertTrue(totalAvailable > 0, "Test assumes '$word' has at least one decomposition")

        val cap = totalAvailable + 1
        val capped = MorphologicalAnalyzer_R2L().stopAfterN(cap).decomposeWord(word, false)

        assertEquals(
            totalAvailable, capped.size,
            "stopAfterN($cap), with cap > true total ($totalAvailable), must return the " +
                "full set -- got ${capped.size} instead."
        )
    }

    @Test
    fun stopAfterN_withCapBelowTrueTotal_returnsAtLeastTheCap() {
        // "aulajjutinut" was the other word that exposed the bug: capped at
        // 4 out of a true total of 8, it returned only 3.
        val word = "aulajjutinut"
        val cap = 4

        val full = MorphologicalAnalyzer_R2L().decomposeWord(word, false)
        assertTrue(full.size > cap, "Test assumes '$word' has more than $cap decompositions")

        val capped = MorphologicalAnalyzer_R2L().stopAfterN(cap).decomposeWord(word, false)

        assertTrue(
            capped.size >= cap,
            "stopAfterN($cap) must return at least $cap final decompositions when more than " +
                "$cap exist -- got ${capped.size}."
        )
        assertTrue(
            capped.size <= full.size,
            "stopAfterN($cap) must not return more decompositions than actually exist " +
                "(${full.size}) -- got ${capped.size}."
        )
    }
}
