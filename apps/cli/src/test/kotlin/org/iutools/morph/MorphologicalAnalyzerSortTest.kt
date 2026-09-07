package org.iutools.morph

import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Unit tests for MorphologicalAnalyzer.sortDecompositions -- the decomposition
 * ranking shared by MorphologicalAnalyzer_R2L (Uqailaut) and
 * MorphologicalAnalyzer_FST. A tiny stub analyzer is used only to reach the
 * protected method; doDecompose() is never exercised here.
 */
class MorphologicalAnalyzerSortTest {

    private class Stub : MorphologicalAnalyzer() {
        override fun doDecompose(word: String, lenient: Boolean?): Array<Decomposition> =
            throw UnsupportedOperationException("not used by this test")

        fun rank(
            ranked: List<RankedDecomposition>,
            byFrequency: Boolean = false,
        ): List<String> =
            sortDecompositions(ranked, breakTiesByMorphemeFrequency = byFrequency).map { it.toString() }
    }

    private val sorter = Stub()

    private fun d(specs: String) = Decomposition(specs)

    @Test
    fun longestRootWinsFirst() {
        val shortRoot = RankedDecomposition(d("aki/1n nga/tn-nom-s-4s"), rootCanonicalLength = 3)
        val longRoot = RankedDecomposition(d("akinnga/1n"), rootCanonicalLength = 7)
        assertEquals(
            listOf("{akinnga/1n}", "{aki/1n}{nga/tn-nom-s-4s}"),
            sorter.rank(listOf(shortRoot, longRoot)),
        )
    }

    @Test
    fun fewerMorphemesBreaksARootLengthTie() {
        val threeMorphemes = RankedDecomposition(d("qaja/1n qx/xx rx/yy"), rootCanonicalLength = 4)
        val twoMorphemes = RankedDecomposition(d("qaja/1n qqx/zz"), rootCanonicalLength = 4)
        assertEquals(
            listOf("{qaja/1n}{qqx/zz}", "{qaja/1n}{qx/xx}{rx/yy}"),
            sorter.rank(listOf(threeMorphemes, twoMorphemes)),
        )
    }

    @Test
    fun lenientWeightAlwaysRanksAfterStrict() {
        val lenientLongRoot =
            RankedDecomposition(d("verylongroot/1v"), rootCanonicalLength = 12, weight = 1.0f)
        val strictShortRoot =
            RankedDecomposition(d("ax/1n bx/yy cx/zz"), rootCanonicalLength = 1, weight = 0.0f)
        assertEquals(
            listOf("{ax/1n}{bx/yy}{cx/zz}", "{verylongroot/1v}"),
            sorter.rank(listOf(lenientLongRoot, strictShortRoot)),
        )
    }

    @Test
    fun morphemeFrequencyBreaksRemainingTiesWhenRequested() {
        // juq/1vn is one of the most frequent morphemes in the Hansard prior;
        // zzz/1vn is absent from it (score 0). The shared "malik/1v" cancels.
        val common = RankedDecomposition(d("malik/1v juq/1vn"), rootCanonicalLength = 5)
        val rare = RankedDecomposition(d("malik/1v zzz/1vn"), rootCanonicalLength = 5)

        assertEquals(
            listOf("{malik/1v}{juq/1vn}", "{malik/1v}{zzz/1vn}"),
            sorter.rank(listOf(rare, common), byFrequency = true),
        )
        // Off by default: the tie is left to the input (stable) order.
        assertEquals(
            listOf("{malik/1v}{zzz/1vn}", "{malik/1v}{juq/1vn}"),
            sorter.rank(listOf(rare, common), byFrequency = false),
        )
    }

    @Test
    fun sortIsStableWhenEveryKeyTies() {
        val first = RankedDecomposition(d("aaa/1n bbb/2n"), rootCanonicalLength = 3)
        val second = RankedDecomposition(d("ccc/1n ddd/2n"), rootCanonicalLength = 3)
        assertEquals(
            listOf("{aaa/1n}{bbb/2n}", "{ccc/1n}{ddd/2n}"),
            sorter.rank(listOf(first, second)),
        )
        assertEquals(
            listOf("{ccc/1n}{ddd/2n}", "{aaa/1n}{bbb/2n}"),
            sorter.rank(listOf(second, first)),
        )
    }
}
