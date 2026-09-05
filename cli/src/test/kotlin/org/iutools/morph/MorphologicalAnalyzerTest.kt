package org.iutools.morph

import org.iutools.morph.r2l.MorphologicalAnalyzer_R2L
import org.iutools.utilities.StopWatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/*
 * Port of org.iutools.morph.MorphologicalAnalyzerTest (Java): the general
 * behavioral/word-decomposition test suite for decomposeWord(), as opposed
 * to MorphologicalAnalyzer__AccuracyTest which checks the Hansard gold
 * standard in bulk. One concrete subclass per analyzer implementation --
 * currently just MorphologicalAnalyzer_R2LTest, since the L2R/L2RAlain
 * variants were pruned as unreachable from decomposeWord().
 */
abstract class MorphologicalAnalyzerTest {

    protected lateinit var analyzer: MorphologicalAnalyzer

    abstract fun makeAnalyzer(): MorphologicalAnalyzer

    @BeforeTest
    fun setUp() {
        analyzer = makeAnalyzer()
    }

    ////////////////////////////////////
    // DOCUMENTATION TESTS
    ////////////////////////////////////

    @Test
    fun synopsis_documentationExample() {
        // By default, the analysis times out after 10 seconds; the timeout
        // can be set to a different value.
        analyzer.setTimeout(15000) // in milliseconds

        // By default, timing out is active; it can be deactivated and
        // reactivated.
        analyzer.deactivateTimeout()
        analyzer.activateTimeout()

        // The purpose of the analyzer is to decompose an Inuktitut word into
        // its morphemes.
        val word = "iglumik"
        try {
            analyzer.decomposeWord(word)
        } catch (e: TimeoutException) {
            // This happens if the decomposer timed out before it could
            // complete the analysis.
        }

        // If you don't care to have all the possible decompositions, you can
        // provide the analyzer with a maximum number of decomps. In
        // particular, you can tell it to stop after it finds the very first
        // decomp.
        analyzer.stopAfterN(1)
    }

    ////////////////////////////////////
    // VERIFICATION TESTS
    ////////////////////////////////////

    @Test
    fun decomposeWord_withStopAfterN_returnsFirstOfFullList() {
        analyzer.deactivateTimeout()

        val word = "iglumik"
        val allAnalyses = analyzer.decomposeWord(word)
        assertTrue(allAnalyses.size > 1, "Should have returned more than one analysis")

        analyzer.stopAfterN(1)
        val singleAnalysis = analyzer.decomposeWord(word)
        assertEquals(1, singleAnalysis.size, "Should have returned just one analysis")

        assertEquals(
            allAnalyses[0].toString(), singleAnalysis[0].toString(),
            "Single decomp should have been the first decomp of the full list"
        )
    }

    /**
     * Compare speed of decomposition of the first 100 words in the
     * GoldStandard with stopAfterN=1 versus stopAfterN=null. It should be
     * MUCH faster with stopAfterN=1.
     */
    @Test
    fun decomposeWord_withStopAfterN_isMuchFaster() {
        analyzer.deactivateTimeout()

        // Only use the first 100 words from the GS
        val firstNWords = 100
        val goldStandard = MorphAnalGoldStandard_Hansard_FromCsv()
        val words = goldStandard.allWords().sorted().subList(0, firstNWords)

        // The analyzer keeps a process-wide decomposition cache whose key
        // includes the stopAfterN value. Without the eviction below, a word
        // already decomposed by an earlier test (or by the warm-up pass)
        // turns one of the two timed passes into a pure cache hit and makes
        // the comparison meaningless -- we once measured 2ms "for all
        // decomps" against 281ms "for one". Evict both keys for every word
        // right before each timed pass.
        fun evictCacheFor(theWords: List<String>) {
            for (word in theWords) {
                MorphologicalAnalyzer_R2L.removeFromCache(word, null, true)
                MorphologicalAnalyzer_R2L.removeFromCache(word, 1, true)
            }
        }

        // Warm the JIT up on this workload before taking any measurement, so
        // the first timed pass isn't penalised for compiling hot code.
        for (word in words) {
            analyzer.decomposeWord(word)
        }

        // First, time how long it takes, asking for all decomps. Measure in
        // milliseconds, not whole seconds: on a fast machine the whole run is
        // barely over a second, and second-resolution rounding turned the
        // speedup ratio into pure noise (2.0 one run, Infinity the next).
        evictCacheFor(words)
        var sw = StopWatch().start()
        for (word in words) {
            analyzer.decomposeWord(word)
        }
        val msecsAllDecomps = sw.totalTime(TimeUnit.MILLISECONDS)

        // Then, time how long it takes, asking only for one decomp
        evictCacheFor(words)
        sw = StopWatch().start()
        analyzer.stopAfterN(1)
        for (word in words) {
            analyzer.decomposeWord(word)
        }
        val msecsSingleDecomp = sw.totalTime(TimeUnit.MILLISECONDS)

        val gotSpeedup = 1.0 * msecsAllDecomps / msecsSingleDecomp
        println("stopAfterN(1) speedup: $gotSpeedup " +
            "(${msecsAllDecomps}ms for all decomps vs ${msecsSingleDecomp}ms for one)")

        // This is a two-sided expectation on purpose. The lower bound is the
        // real point of the test: stopping after the first decomp must save a
        // substantial amount of work. The upper bound is a deliberate
        // "you improved something" tripwire -- if the speedup comes in well
        // above what we recorded, the analyzer (or the machine) got markedly
        // faster at this, and BOTH bounds below should be ratcheted up so the
        // test can still catch a future slowdown from the new, better level.
        // Observed on this dev container (Aug 2026), warmed up and with the
        // cache evicted: ~6.8-7.1x (all decomps ~1.7s, one decomp ~0.24s).
        // Widen the band, don't delete it, if this proves flaky on CI.
        val minSpeedup = 5.0
        val maxSpeedup = 10.0
        assertTrue(
            gotSpeedup >= minSpeedup,
            "Asking for just one decomp should have been at least ${minSpeedup}x faster, " +
                "but got a speedup of only $gotSpeedup -- stopAfterN(1) may have regressed."
        )
        assertTrue(
            gotSpeedup <= maxSpeedup,
            "stopAfterN(1) speedup was $gotSpeedup, above the expected ceiling of ${maxSpeedup}x. " +
                "That's good news, not a bug: something got faster. Raise minSpeedup (and this " +
                "ceiling) to lock in the gain so a later slowdown still trips this test."
        )
    }

    @Test
    fun decomposeWord_timeout2s_throwsTimeoutException() {
        analyzer.setTimeout(2000)
        val word = "ilisaqsitittijunnaqsisimannginnama"
        assertFailsWith<TimeoutException> { analyzer.decomposeWord(word) }
    }

    @Test
    fun decomposeWord_timeout10s_throwsTimeoutException() {
        val word = "ilisaqsitittijunnaqsisimannginnama"
        assertFailsWith<TimeoutException> { analyzer.decomposeWord(word) }
    }

    @Test
    fun decomposeWord_maligatigut() {
        val word = "maligatigut"
        analyzer.deactivateTimeout()
        val decs = analyzer.decomposeWord(word)
        AssertDecompositionList(decs)
            // No decomposition produced for this word
            .includesAtLeastOneOfDecomps()
    }

    @Test
    fun decomposeWord_uqaqtiup() {
        val word = "uqaqtiup"
        analyzer.deactivateTimeout()

        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple)
            .includesAtLeastOneOfDecomps(
                "{uqaq:uqaq/1v}{ti:ji/1vn}{up:up/tn-gen-s}",
                "{uqaq:uqaq/1v}{ti:tiq/1vn}{up:ut/2nn}",
            )
    }

    @Test
    fun decomposeWord_sivuliuqtii() {
        val word = "sivuliuqtii"
        analyzer.deactivateTimeout()

        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple)
            .includesAtLeastOneOfDecomps(
                "{sivuliuqti:sivuliuqti/1n}{i:k/tn-nom-d}",
                "{sivuliuqti:sivuliuqti/1n}{i:it/3nv}",
            )
    }

    @Test
    fun decomposeWord_ammalu() {
        val word = "ammalu"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple)
            .includesAtLeastOneOfDecomps("{ammalu:ammalu/1c}")
    }

    /*
     * This test verifies that analyses containing morpheme sequences for
     * which a combined morpheme exists are removed. E.g.: the first analysis
     * is removed since, in the second, there is the morpheme juksaq which is
     * the combination of juq and ksaq.
     * {apiq:apiq/1v}{suq:suq/1vv}{ta:jaq/1vn}{u:u/1nv}{ju:juq/1vn}{ksaq:ksaq/1nn}
     * {apiq:apiq/1v}{suq:suq/1vv}{ta:jaq/1vn}{u:u/1nv}{juksaq:juksaq/1vn}
     */
    @Test
    fun decomposeWord_apiqsuqtaujuksaq() {
        val word = "apiqsuqtaujuksaq"
        analyzer.deactivateTimeout()

        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple)
            .allDecompsContain(
                "(ju:ut/1vn ksaq:ksaq/1nn|juksaq:juksaq/1vn|ju:juq/1vn ksaq:ksaq/1nn|ju:jjut/1vn ksaq:ksaq/1nn)"
            )
    }

    @Test
    fun decomposeWord_immagaa() {
        val word = "immagaa"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple)
            // No decomp for this word
            .includesAtLeastOneOfDecomps()
    }

    @Test
    fun decomposeWord_avunngaExtensions() {
        var word = "avunngaqtuq"
        analyzer.deactivateTimeout()
        var decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps("{avunngaq:avunngaq/1v}{tuq:juq/1vn}")

        word = "avunngaujjijuq"
        decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps("{avunnga:avunngaq/1v}{ujji:ujji/1vv}{juq:juq/1vn}")

        word = "avunngautijuq"
        decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps("{avunnga:avunngaq/1v}{uti:uti/1vv}{juq:juq/1vn}")
    }

    @Test
    fun decomposeWord_atuagaq() {
        val word = "atuagaq"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps("{atua:atuaq/1v}{gaq:gaq/1vn}")
    }

    @Test
    fun decomposeWord_maligaliuqti() {
        val string = "maligaliuqti"
        val analyses = analyzer.decomposeWord(string)
        AssertDecompositionList(analyses, "Decompositions for word $string")
            .producesAtLeastNDecomps(7)
            .includesDecomps("{maliga:maligaq/1n}{liuq:liuq/1nv}{ti:ji/1vn}")
    }

    @Test
    fun decomposeWord_maligaliuqtinik() {
        val word = "maligaliuqtinik"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps(
                "{maliga:maligaq/1n}{liuq:liuq/1nv}{ti:ji/1vn}{nik:nik/tn-acc-p}",
                "{maliga:maligaq/1n}{liuq:liuq/1nv}{tin:tit/1vv}{ik:it/1vv}",
            )
    }

    @Test
    fun decomposeWord_sivungujuq() {
        val word = "sivungujuq"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps("{sivu:sivu/1n}{ngu:ngu/1nv}{juq:juq/1vn}")
    }

    @Test
    fun decomposeWord_withExtendedAnalysis() {
        val word = "makpiga"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .atLeastOneDecompContains("ga:gaq/1vn")
    }

    @Test
    fun decomposeWord_withoutExtendedAnalysis() {
        val word = "makpiga"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word, false)
        AssertDecompositionList(decSimple, "word=$word, NO extended analysis")
            .includesAtLeastOneOfDecomps()
    }

    @Test
    fun decomposeWord_nounRootAlone() {
        val word = "angut"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps("{angut:angut/1n}")
    }

    @Test
    fun decomposeWord_inungmut() {
        val word = "inungmut"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps("{inung:inuk/1n}{mut:mut/tn-dat-s}")
    }

    @Test
    fun decomposeWord_siniktitsijuq() {
        val word = "siniktitsijuq"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps(
                "{sinik:sinik/1v}{tit:tit/1vv}{si:si/1vv}{juq:juq/1vn}",
                "{sinik:sinik/1n}{titsi:gipsi/tv-imp-2p}{juq:juq/tv-ger-3s}",
            )
    }

    @Test
    fun decomposeWord_siniktittijuq() {
        val word = "siniktittijuq"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps(
                "{sinik:sinik/1v}{tit:tiq/1vn}{ti:si/4nv}{juq:juq/1vn}",
                "{sinik:sinik/1v}{titti:gissik/tv-imp-2d}{juq:juq/tv-ger-3s}",
                "{sinik:sinik/1n}{titti:gissik/tv-imp-2d}{juq:juq/tv-ger-3s}",
            )
    }

    @Test
    fun decomposeWord_pivalliatittinirmut() {
        val word = "pivalliatittinirmut"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps(
                "{pi:pi/1v}{vallia:vallia/1vv}{tit:tiq/1vn}{ti:si/4nv}{nir:niq/2vn}{mut:mut/tn-dat-s}",
                "{piv:vit/tv-int-2s}{allia:alliaq/1n}{tit:tik/tn-nom-p-2d}{t:t/tn-nom-p}{inir:iniq/1v}{mut:mut/tn-dat-s}",
            )
    }

    @Test
    fun decomposeWord_siniktittiniq() {
        val word = "siniktittiniq"
        analyzer.deactivateTimeout()
        val decSimple = analyzer.decomposeWord(word)
        AssertDecompositionList(decSimple, "word=$word")
            .includesAtLeastOneOfDecomps(
                "{sinik:sinik/1v}{tit:tiq/1vn}{ti:si/4nv}{niq:niq/2vn}",
                "{sinik:sinik/1n}{tit:tik/tn-nom-p-2d}{t:t/tn-nom-p}{iniq:iniq/1v}",
            )
    }

    @Test
    fun decomposeWord_niruarut() {
        val string = "niruarut"
        val analyses = analyzer.decomposeWord(string)
        AssertDecompositionList(analyses)
            .includesAtLeastOneOfDecomps(
                "{nirua:niruaq/1v}{rut:ut/1vn}",
                "{niruar:niruaq/1v}{ut:ut/1vn}",
            )
            .producesAtLeastNDecomps(4)
    }
}
