package org.iutools.morph.fst

import org.iutools.lib.testing.FrequencyHistogram
import org.iutools.morph.MorphAnalCurrentExpectationsAbstract.OutcomeType
import org.iutools.morph.MorphAnalGoldStandard_Hansard
import org.iutools.morph.MorphologicalAnalyzer
import org.iutools.morph.MorphologicalAnalyzer__AccuracyTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.assertEquals

/*
 * Runs the SAME gold-standard accuracy sweep the R2L analyzer's own
 * MorphologicalAnalyzer_R2L__AccuracyTest runs, but against
 * MorphologicalAnalyzer_FST -- the HFST finite-state analyzer, read in-process
 * by the vendored pure-Java optimized-lookup reader (net.sf.hfst).
 *
 * WHAT THIS VALIDATES: that the Java reader returns, for every gold-standard
 * word, the same SET of decompositions the native `hfst-lookup` tool
 * produces for the same transducer -- i.e. that the vendored reader's known
 * upstream bugs (spurious/missing analyses on some automata) do NOT affect
 * ours. That set-level parity is checked as:
 *   - "correct decomposition present somewhere" (SUCCESS + CORRECT_NOT_FIRST)
 *     == what tools/fst/full_corpus_check.py --fair reports,
 *   - CORRECT_NOT_PRESENT and NO_DECOMPS == the same.
 *
 * The raw SUCCESS count on its own is deliberately NOT asserted: the Java
 * reader walks the automaton in a different order than `hfst-lookup`, so
 * "which analysis comes out first" differs. Neither order is meaningful --
 * ranking is a separate downstream step (as for R2L, which sorts after
 * searching) -- so only the order-independent totals matter here.
 *
 * Skips itself (rather than failing) when tools/fst/lexicon-analyser.hfstol
 * hasn't been built -- this is a dev-time check, not a CI gate.
 *
 * Two adjustments vs the R2L run, via MorphologicalAnalyzer__AccuracyTest
 * extension points:
 *  - normalizeGoldDecompForComparison(): the FST only knows the canonical
 *    morpheme + tag ("atuaq/1v"), not which surface substring it matched, so
 *    the gold "{atua:atuaq/1v}" is compared as "{atuaq/1v}" -- exactly the
 *    (canonical, id)-pair comparison full_corpus_check.py does.
 *  - hasRecordedExpectations = false: there is no committed FST
 *    current-expectations snapshot yet, so the "did it regress" and
 *    runtime-baseline assertions are skipped; this run only reports and pins
 *    the totals below.
 */
class MorphologicalAnalyzer_FST__AccuracyTest : MorphologicalAnalyzer__AccuracyTest() {

    override fun makeAnalyzer(): MorphologicalAnalyzer {
        assumeTrue(
            MorphologicalAnalyzer_FST.isAvailable(),
            "Skipping: tools/fst/lexicon-analyser.hfstol not built " +
                "(see tools/fst/phonology.xfscript's header).",
        )
        return MorphologicalAnalyzer_FST()
    }

    override val hasRecordedExpectations: Boolean = false

    // "{atua:atuaq/1v}{gaq:gaq/1vn}" -> "{atuaq/1v}{gaq/1vn}"
    // (the undecomposable-word gold form "[decomposition:/Hanta(Hanta)/]" has
    // no "{...:...}" component and is left untouched -- those words are
    // skipCase()'d anyway.)
    override fun normalizeGoldDecompForComparison(goldDecomp: String): String =
        goldDecomp.replace(Regex("""\{[^:{}]+:"""), "{")

    /**
     * Order-independent totals from tools/fst/full_corpus_check.py --fair for
     * the current transducer. Update these together with the transducer
     * whenever the .lexc lexicon changes; a mismatch means the Java reader
     * and the native `hfst-lookup` have diverged on our data, which is the
     * whole point of this test.
     */
    override fun assertOutcomeHistogram(gotOutcomeHist: FrequencyHistogram<OutcomeType>) {
        val correctPresent = gotOutcomeHist.frequency(OutcomeType.SUCCESS) +
            gotOutcomeHist.frequency(OutcomeType.CORRECT_NOT_FIRST)
        val notPresent = gotOutcomeHist.frequency(OutcomeType.CORRECT_NOT_PRESENT)
        val noDecomps = gotOutcomeHist.frequency(OutcomeType.NO_DECOMPS)

        val (expCorrectPresent, expNotPresent, expNoDecomps) =
            if (goldStandard is MorphAnalGoldStandard_Hansard) {
                Triple(917L, 2L, 0L) // 919 fair Hansard words
            } else {
                Triple(3L, 0L, 0L) // MorphAnalGoldStandard_WordsThatFailedBefore (3 fair words)
            }

        assertEquals(
            listOf(expCorrectPresent, expNotPresent, expNoDecomps),
            listOf(correctPresent, notPresent, noDecomps),
            "FST (Java reader) coverage differs from the native hfst-lookup path " +
                "(full_corpus_check.py --fair): [correct-present, correct-not-present, no-decomps].",
        )
    }
}
