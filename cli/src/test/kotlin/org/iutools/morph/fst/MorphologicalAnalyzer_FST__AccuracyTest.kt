package org.iutools.morph.fst

import org.iutools.morph.MorphAnalCurrentExpectations_FST_Hansard
import org.iutools.morph.MorphAnalCurrentExpectations_FST_WordsThatFailedBefore
import org.iutools.morph.MorphAnalCurrentExpectationsAbstract
import org.iutools.morph.MorphologicalAnalyzer
import org.iutools.morph.MorphologicalAnalyzer__AccuracyTest
import org.junit.jupiter.api.Assumptions.assumeTrue

/*
 * Runs the SAME gold-standard accuracy sweep MorphologicalAnalyzer_R2L__AccuracyTest
 * runs, but against MorphologicalAnalyzer_FST -- the HFST finite-state analyzer,
 * read in-process by the vendored pure-Java optimized-lookup reader (net.sf.hfst).
 *
 * It goes through the base class exactly the way the R2L run does: outcomes are
 * checked against a committed current-expectations snapshot
 * (MorphAnalCurrentExpectations_FST_*, the FST counterpart of the R2L
 * MorphAnalCurrentExpectations_*), and the per-machine runtime baseline is
 * enforced. The FST snapshot is a different set of words -- the FST covers the
 * same words as R2L (917/919 present somewhere) but ranks them with the shared
 * MorphologicalAnalyzer.sortDecompositions (morpheme-frequency tie-break on),
 * so a different set lands off the top spot (666/919 first-correct).
 *
 * Skips itself (rather than failing) when data/grammar/fst/lexicon-analyser.hfstol
 * hasn't been built -- this is a dev-time check, not a CI gate.
 *
 * One adjustment vs the R2L run, via a MorphologicalAnalyzer__AccuracyTest
 * extension point: normalizeGoldDecompForComparison() strips the "surface:"
 * part of each gold component, because the FST only knows the canonical
 * morpheme + id ("atuaq/1v"), not which surface substring it matched -- so the
 * gold "{atua:atuaq/1v}" is compared as "{atuaq/1v}".
 */
class MorphologicalAnalyzer_FST__AccuracyTest : MorphologicalAnalyzer__AccuracyTest() {

    override fun makeAnalyzer(): MorphologicalAnalyzer {
        assumeTrue(
            MorphologicalAnalyzer_FST.isAvailable(),
            "Skipping: data/grammar/fst/lexicon-analyser.hfstol not built " +
                "(see data/grammar/fst/phonology.xfscript's header).",
        )
        return MorphologicalAnalyzer_FST()
    }

    // The FST decomposes the whole gold standard in ~1.5 s; on a shared/busy
    // box that swings by well over 30% run to run (measured 1.2-2.0 s this
    // session), so the base 30% drift band flakes. 75% still fails a real
    // 2x+ regression on a ~1.5 s workload.
    override val runtimeToleranceFraction: Double = 0.75

    override fun makeHansardExpectations(): MorphAnalCurrentExpectationsAbstract =
        MorphAnalCurrentExpectations_FST_Hansard()

    override fun makeWordsThatFailedBeforeExpectations(): MorphAnalCurrentExpectationsAbstract =
        MorphAnalCurrentExpectations_FST_WordsThatFailedBefore()

    // "{atua:atuaq/1v}{gaq:gaq/1vn}" -> "{atuaq/1v}{gaq/1vn}"
    // (the undecomposable-word gold form "[decomposition:/Hanta(Hanta)/]" has
    // no "{...:...}" component and is left untouched -- those words are
    // skipCase()'d anyway.)
    override fun normalizeGoldDecompForComparison(goldDecomp: String): String =
        goldDecomp.replace(Regex("""\{[^:{}]+:"""), "{")
}
