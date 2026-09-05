package org.iutools.morph

import org.iutools.lib.testing.AssertRuntime
import org.iutools.morph.MorphAnalCurrentExpectationsAbstract.WordExpectation
import org.junit.jupiter.api.TestInfo
import java.util.concurrent.TimeoutException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

/**
 * Shared accuracy-test driver for any MorphologicalAnalyzer implementation
 * (see MorphologicalAnalyzer_R2L__AccuracyTest / _FST_AccuracyTest). Scores
 * each "fair" gold-standard word (skipCase()) on 4 metrics, all measured
 * against data/grammar/gold-standard/gold-standard.csv's all_correct_decomps
 * (every decomposition R2L itself can produce for that word) and
 * decomp_as_found_in_source (the single "reference decomp" -- the specific
 * reading actually attested in the Hansard occurrence the word was drawn
 * from):
 *
 * - Recall: macro-averaged (per-word %, then mean across words -- "example-
 *   based recall" in multi-label-classification terms, since each word is
 *   an example with its own correct-answer set) fraction of
 *   all_correct_decomps the analyzer actually produced.
 * - Precision: same macro-average, fraction of the analyzer's own produced
 *   decomps that are in all_correct_decomps. Undefined (excluded from the
 *   average, tallied separately) for a word where nothing was produced.
 * - Reference-decomp-present: % of words where the reference decomp is
 *   anywhere in the produced list.
 * - Reference-decomp-in-top-N (N=1..5): % of words where it's within the
 *   top N.
 *
 * A committed per-word snapshot (MorphAnalCurrentExpectations_Hansard.kt
 * and its 3 siblings) still guards against regressions, analyzer-specific
 * as before -- but only fails on an actual regression (a word getting
 * WORSE than its snapshot), not on any drift. Unlike the old OutcomeType
 * histogram, the new per-word data (matched/produced/referenceRank) also
 * tracks precision, so incidental improvements are common; forcing a
 * snapshot-file edit for every one of those would defeat the sparse-file
 * convention the snapshot exists for. Improvements are printed as a
 * non-fatal "snapshot is stale" notice instead.
 */
abstract class MorphologicalAnalyzer__AccuracyTest {

    var verbose = false

    var morphAnalyzer: MorphologicalAnalyzer? = null

    lateinit var goldStandard: MorphAnalGoldStandardAbstract
    lateinit var expectations: MorphAnalCurrentExpectationsAbstract

    protected abstract fun makeAnalyzer(): MorphologicalAnalyzer

    // Extension points for a second analyzer whose output/expectations differ
    // from R2L's (see MorphologicalAnalyzer_FST__AccuracyTest). Defaults keep
    // the R2L subclass behaving exactly as before.

    // Applied to every gold-standard decomposition string (both
    // all_correct_decomps entries and the reference decomp(s)) before it is
    // matched against the analyzer's own output. R2L emits full
    // {surface:canonical/id} components, so the default is identity; the FST
    // only knows canonical/id, so its subclass strips the "surface:" part.
    protected open fun normalizeGoldDecompForComparison(goldDecomp: String): String = goldDecomp

    // The committed "current expectations" snapshot each gold standard is
    // checked against. Every concrete analyzer has its own -- the FST ranks
    // its decompositions differently from R2L, so a different set of words
    // lands off the top spot. Both the "outcomes haven't regressed" check and
    // the per-machine runtime baseline run for every subclass; there is no
    // opt-out.
    protected open fun makeHansardExpectations(): MorphAnalCurrentExpectationsAbstract =
        MorphAnalCurrentExpectations_Hansard()

    protected open fun makeWordsThatFailedBeforeExpectations(): MorphAnalCurrentExpectationsAbstract =
        MorphAnalCurrentExpectations_WordsThatFailedBefore()

    // Which gold standard to run against. Defaults to the hand-written
    // addCase()-built one; MorphologicalAnalyzer_R2L__AccuracyTest overrides
    // this to read data/grammar/gold-standard/gold-standard.csv instead
    // (verified to produce identical AnalyzerCase data by
    // GoldStandardCsvMatchesKotlinTest). Other subclasses (e.g. the FST run)
    // keep the default until they're switched over too.
    protected open fun makeHansardGoldStandard(): MorphAnalGoldStandardAbstract =
        MorphAnalGoldStandard_Hansard()

    protected open fun makeWordsThatFailedBeforeGoldStandard(): MorphAnalGoldStandardAbstract =
        MorphAnalGoldStandard_WordsThatFailedBefore()

    // Tolerance for the per-machine runtime-drift check (fraction, either
    // direction). The FST runs the whole gold standard in ~1.5 s -- 13x
    // faster than R2L -- so the same percentage band is a much smaller
    // absolute window and trips on ordinary machine-load noise; its subclass
    // widens this. The check still catches a real regression, just needs a
    // looser band on such a short measurement.
    protected open val runtimeToleranceFraction: Double = 0.30

    @BeforeTest
    fun setUp() {
        if (morphAnalyzer == null) {
            // check how much time it takes for the analyzer to be created
            // (in fact, this is the time for loading the database)
            val start = System.currentTimeMillis()
            morphAnalyzer = makeAnalyzer()
            val elapsed = System.currentTimeMillis() - start

            println()
            println("creating new ${morphAnalyzer!!::class.simpleName}: Time in milliseconds: $elapsed")
        }
        morphAnalyzer!!.activateTimeout()
    }

    @Test
    fun test_accuracy_with_GoldStandard_Hansard(testInfo: TestInfo) {
        println("Running test_accuracy_with_GoldStandard_Hansard.")

        goldStandard = makeHansardGoldStandard()
        expectations = makeHansardExpectations()

        // If you want to only evaluate one word, uncomment and modify the
        // next line.
        // expectations.focusOnWord = "someword"

        evaluateAccuracy(testInfo)
    }

    @Test
    fun test_accuracy_with_GoldStandard_WordsThatFailedBefore() {
        println("Running test_accuracy_with_GoldStandard_WordsThatFailedBefore.")

        goldStandard = makeWordsThatFailedBeforeGoldStandard()
        expectations = makeWordsThatFailedBeforeExpectations()

        // No runtime baseline check here: this gold standard is only a
        // handful of words, so its total decomposition time is dominated by
        // noise.
        evaluateAccuracy(null)
    }

    private fun evaluateAccuracy(runtimeBaselineTestInfo: TestInfo?) {
        println("This test can take a few minutes to complete.")

        // Uncomment for debugging.
        morphAnalyzer!!.deactivateTimeout()

        val start = System.currentTimeMillis()

        val allCorrectByWord: Map<String, Set<String>> =
            GoldStandardCsvReader.allCorrectDecomps(goldStandard.sourceName() ?: "")
                .mapValues { (_, decomps) -> decomps.map { normalizeGoldDecompForComparison(it) }.toSet() }

        val metrics = AccuracyMetricsAccumulator()
        val regressions = mutableMapOf<String, String>()
        val improvements = mutableListOf<String>()
        val liveByWord = mutableMapOf<String, WordExpectation>()

        var column = 0
        for (wordToBeAnalyzed in goldStandard.allWords()) {
            column++
            val caseData = goldStandard.caseData(wordToBeAnalyzed)!!

            if (verbose) {
                print("> :$wordToBeAnalyzed:")
            } else {
                print(".")
                if (column == 80) {
                    print("\n")
                    column = 0
                }
            }

            if (skipCase(caseData, expectations.focusOnWord)) {
                continue
            }

            val outcome = decompose(wordToBeAnalyzed)

            if (verbose) println(" []")

            checkOutcome(
                wordToBeAnalyzed, outcome, caseData, allCorrectByWord[wordToBeAnalyzed] ?: emptySet(),
                expectations, metrics, regressions, improvements, liveByWord,
            )
        }

        val elapsed = System.currentTimeMillis() - start

        println()
        println("Analysis of all words: Time in milliseconds: $elapsed")

        metrics.print()
        maybeDumpSnapshot(liveByWord, allCorrectByWord, expectations)

        if (improvements.isNotEmpty()) {
            println("\n${improvements.size} word(s) improved beyond their snapshot (not a failure -- " +
                "consider regenerating the snapshot, see maybeDumpSnapshot()'s -D flag):")
            improvements.sorted().forEach { println("  $it") }
        }

        assertNoRegressions(regressions)

        // Fail if the time to decompose the whole gold standard has drifted
        // by more than 30% -- in EITHER direction -- from the baseline last
        // recorded on THIS machine. A big speed-up trips it too, on purpose:
        // that means the baseline is stale and should be re-recorded lower so
        // a later slow-down is still caught. The baseline lives in an
        // uncommitted JSON file under the build tree (see AssertRuntime); the
        // first run after a checkout or `./gradlew clean` just records it and
        // passes. This does not touch the accuracy metrics above.
        //
        // The operation name is analyzer-specific: this test method is
        // declared on the abstract base, so every subclass shares one
        // baseline file -- without the analyzer's name in the key, the fast
        // FST run and the slow R2L run would clobber each other's baseline.
        if (runtimeBaselineTestInfo != null) {
            AssertRuntime.runtimeHasNotChanged(
                elapsed.toDouble(), runtimeToleranceFraction,
                "${morphAnalyzer!!::class.simpleName}: decompose all " +
                    "${goldStandard.allWords().size} gold-standard words",
                runtimeBaselineTestInfo,
            )
        }

        if (expectations.focusOnWord != null) {
            fail(
                "Test was only run on single word ${expectations.focusOnWord}\n" +
                    "Don't forget to reset focusOnWord=null before committing!"
            )
        }
    }

    /** Aggregates the 4 metrics across the whole run for printing. */
    private class AccuracyMetricsAccumulator {
        private var recallSum = 0.0
        private var recallCount = 0
        private var precisionSum = 0.0
        private var precisionCount = 0
        private var noOutputCount = 0
        private var referencePresentCount = 0
        private var totalWords = 0
        private val topNCounts = IntArray(5)

        fun add(matched: Int, produced: Int, allCorrectCount: Int, referenceRank: Int?) {
            totalWords++
            if (allCorrectCount > 0) {
                recallSum += matched.toDouble() / allCorrectCount
                recallCount++
            }
            if (produced > 0) {
                precisionSum += matched.toDouble() / produced
                precisionCount++
            } else {
                noOutputCount++
            }
            if (referenceRank != null) {
                referencePresentCount++
                for (n in 1..5) {
                    if (referenceRank < n) topNCounts[n - 1]++
                }
            }
        }

        fun print() {
            fun pct(x: Double) = "%.1f%%".format(x * 100)

            println()
            println("== Accuracy metrics (macro-averaged where noted) ==\n")
            println("Recall    (macro-avg over $recallCount words) : ${pct(if (recallCount > 0) recallSum / recallCount else 0.0)}")
            println(
                "Precision (macro-avg over $precisionCount words with output; " +
                    "$noOutputCount produced nothing) : ${pct(if (precisionCount > 0) precisionSum / precisionCount else 0.0)}"
            )
            println(
                "Reference decomp present anywhere    : $referencePresentCount/$totalWords " +
                    "(${pct(if (totalWords > 0) referencePresentCount.toDouble() / totalWords else 0.0)})"
            )
            println("Reference decomp in top-N:")
            for (n in 1..5) {
                println("  N=$n: ${topNCounts[n - 1]}/$totalWords (${pct(if (totalWords > 0) topNCounts[n - 1].toDouble() / totalWords else 0.0)})")
            }
        }
    }

    private fun checkOutcome(
        word: String,
        gotOutcome: AnalysisOutcome,
        caseData: AnalyzerCase,
        allCorrectSet: Set<String>,
        expectations: MorphAnalCurrentExpectationsAbstract,
        metrics: AccuracyMetricsAccumulator,
        regressions: MutableMap<String, String>,
        improvements: MutableList<String>,
        liveByWord: MutableMap<String, WordExpectation>,
    ) {
        val referenceDecomps = caseData.correctDecomps
            ?.map { normalizeGoldDecompForComparison(it) }
            ?.toTypedArray()

        val producedSet = gotOutcome.producedSet()
        val matched = producedSet.count { it in allCorrectSet }
        val referenceRank = gotOutcome.decompRank(referenceDecomps)

        metrics.add(matched, producedSet.size, allCorrectSet.size, referenceRank)

        val live = WordExpectation(matched, producedSet.size, referenceRank)
        liveByWord[word] = live
        val expected = expectations.expectedFor(word, allCorrectSet.size)

        if (live != expected) {
            if (expectations.isRegression(live, expected)) {
                regressions[word] = regressionMessage(word, live, expected, gotOutcome, referenceDecomps)
            } else {
                improvements += "$word (snapshot: $expected, now: $live)"
            }
        }
    }

    private fun regressionMessage(
        word: String,
        live: WordExpectation,
        expected: WordExpectation,
        gotOutcome: AnalysisOutcome,
        referenceDecomps: Array<String>?,
    ): String {
        return "Snapshot expected $expected, got $live\n" +
            "Reference decomps :\n  ${referenceDecomps?.joinToString("\n  ")}\n" +
            "Got decomps:\n${gotOutcome.joinDecomps()}"
    }

    /** Dev-time convenience: pass -Diutools.accuracy.dumpSnapshot=true to
     * print ready-to-paste expect(...) calls for every word that deviates
     * from the "perfect" default, for regenerating the snapshot file
     * wholesale after a real, reviewed change in analyzer behavior. Not a
     * standalone tool -- no regeneration automation existed before this, and
     * hand-transcribing 3 integers per word from a failure message is
     * error-prone, hence this. */
    private fun maybeDumpSnapshot(
        liveByWord: Map<String, WordExpectation>,
        allCorrectByWord: Map<String, Set<String>>,
        expectations: MorphAnalCurrentExpectationsAbstract,
    ) {
        if (System.getProperty("iutools.accuracy.dumpSnapshot") == null) return

        println("\n==== GENERATED expect(...) calls " +
            "(paste into ${expectations::class.simpleName}.initMorphAnalCurrentExpectations()) ====")
        var listed = 0
        for (word in liveByWord.keys.sorted()) {
            val live = liveByWord.getValue(word)
            val allCorrectCount = allCorrectByWord[word]?.size ?: 0
            val perfect = WordExpectation(matched = allCorrectCount, produced = allCorrectCount, referenceRank = 0)
            if (live != perfect) {
                println("""expect("$word", matched = ${live.matched}, produced = ${live.produced}, referenceRank = ${live.referenceRank})""")
                listed++
            }
        }
        println("==== end ($listed words listed; ${liveByWord.size - listed} at perfect defaults omitted) ====\n")
    }

    private fun assertNoRegressions(regressions: Map<String, String>) {
        if (regressions.isEmpty()) return

        var failMess = "${regressions.size} word(s) regressed vs. their snapshot expectation.\n"
        for (word in regressions.keys.sorted()) {
            failMess += "\n---------------------------------------\n\n" +
                "Word: $word\n    " + regressions.getValue(word).replace("\n", "\n    ")
        }
        fail(failMess)
    }

    private fun skipCase(caseData: AnalyzerCase, focusOnWord: String?): Boolean {
        if (focusOnWord != null && focusOnWord != caseData.word) {
            return true
        }

        if (caseData.misspelled || caseData.possiblyMisspelled ||
            caseData.properName || caseData.borrowed || caseData.decompUnknown
        ) {
            return true
        }

        return false
    }

    private fun decompose(word: String): AnalysisOutcome {
        val outcome = AnalysisOutcome()

        try {
            outcome.decompositions = morphAnalyzer!!.decomposeWord(word)
        } catch (e: TimeoutException) {
            outcome.timedOut = true
        } catch (e: MorphologicalAnalyzerException) {
            outcome.timedOut = true
        }

        return outcome
    }
}
