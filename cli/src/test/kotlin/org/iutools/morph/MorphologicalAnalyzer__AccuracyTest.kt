package org.iutools.morph

import org.iutools.lib.testing.AssertNumber
import org.iutools.lib.testing.AssertRuntime
import org.iutools.lib.testing.FrequencyHistogram
import org.iutools.morph.MorphAnalCurrentExpectationsAbstract.OutcomeType
import org.junit.jupiter.api.TestInfo
import java.util.concurrent.TimeoutException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

abstract class MorphologicalAnalyzer__AccuracyTest {

    var verbose = false

    var morphAnalyzer: MorphologicalAnalyzer? = null

    lateinit var goldStandard: MorphAnalGoldStandardAbstract
    lateinit var expectations: MorphAnalCurrentExpectationsAbstract

    var gotOutcomeHist = FrequencyHistogram<OutcomeType>()
    var expOutcomeHist = FrequencyHistogram<OutcomeType>()

    protected abstract fun makeAnalyzer(): MorphologicalAnalyzer

    // Extension points for a second analyzer whose output/expectations differ
    // from R2L's (see MorphologicalAnalyzer_FST__AccuracyTest). Defaults keep
    // the R2L subclass behaving exactly as before.

    // Applied to every gold-standard decomposition string before it is
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
        gotOutcomeHist = FrequencyHistogram()
        expOutcomeHist = FrequencyHistogram()
    }

    @Test
    fun test_accuracy_with_GoldStandard_Hansard(testInfo: TestInfo) {
        println("Running test_accuracy_with_GoldStandard_Hansard.")

        goldStandard = MorphAnalGoldStandard_Hansard()
        expectations = makeHansardExpectations()

        // If you want to only evaluate one word, uncomment and modify the
        // next line.
        // expectations.focusOnWord = "someword"

        evaluateAccuracy(testInfo)
    }

    @Test
    fun test_accuracy_with_GoldStandard_WordsThatFailedBefore() {
        println("Running test_accuracy_with_GoldStandard_WordsThatFailedBefore.")

        goldStandard = MorphAnalGoldStandard_WordsThatFailedBefore()
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

        val outcomeDifferences = mutableMapOf<String, String>()

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

            checkOutcome(wordToBeAnalyzed, outcome, expectations, goldStandard, outcomeDifferences)
        }

        val elapsed = System.currentTimeMillis() - start

        println()
        println("Analysis of all words: Time in milliseconds: $elapsed")

        printPerformanceStats()

        assertOutcomesHaveNotChangedSignificantly(outcomeDifferences)

        // Fail if the time to decompose the whole gold standard has drifted
        // by more than 30% -- in EITHER direction -- from the baseline last
        // recorded on THIS machine. A big speed-up trips it too, on purpose:
        // that means the baseline is stale and should be re-recorded lower so
        // a later slow-down is still caught. The baseline lives in an
        // uncommitted JSON file under the build tree (see AssertRuntime); the
        // first run after a checkout or `./gradlew clean` just records it and
        // passes. This does not touch the accuracy histogram above.
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

    private fun printPerformanceStats() {
        println()
        printCorrectFoundStats()
        printOutcomeHistogram("Histogram of EXPECTED outcome types", expOutcomeHist)
        printOutcomeHistogram("Histogram of ACTUAL outcome types", gotOutcomeHist)
    }

    private fun printCorrectFoundStats() {
        val totalWords = gotOutcomeHist.totalOccurences()

        // Words where decomps were produced, but they were all incorrect
        val totalNotPresent = gotOutcomeHist.frequency(OutcomeType.CORRECT_NOT_PRESENT)

        // Words where no decomps were produced at all
        val totalNoDecomps = gotOutcomeHist.frequency(OutcomeType.NO_DECOMPS)

        // Words where decomps were produced, and one of them was correct
        val totalCorrectPresent = totalWords - (totalNoDecomps + totalNotPresent)
        val correctRate = 1.0 * totalCorrectPresent / totalWords

        println(
            "\nWords with correct decomp found: $totalCorrectPresent/$totalWords (rate: $correctRate)"
        )
    }

    private fun printOutcomeHistogram(title: String, outcomeHist: FrequencyHistogram<OutcomeType>) {
        echo("\n== $title ==\n")

        echo("Cases with:")
        echo(
            "  First decomposition is correct            : " +
                "${outcomeHist.frequency(OutcomeType.SUCCESS)} (${outcomeHist.relativeFrequency(OutcomeType.SUCCESS, 1)})"
        )
        echo(
            "  Corr. decomp. not in 1st place            : " +
                "${outcomeHist.frequency(OutcomeType.CORRECT_NOT_FIRST)} (${outcomeHist.relativeFrequency(OutcomeType.CORRECT_NOT_FIRST, 1)})"
        )
        echo(
            "  Some decomps produced but not correct one : " +
                "${outcomeHist.frequency(OutcomeType.CORRECT_NOT_PRESENT)} (${outcomeHist.relativeFrequency(OutcomeType.CORRECT_NOT_PRESENT, 1)})"
        )
        echo(
            "  No decomps produced at all                : " +
                "${outcomeHist.frequency(OutcomeType.NO_DECOMPS)} (${outcomeHist.relativeFrequency(OutcomeType.NO_DECOMPS, 1)})"
        )
    }

    private fun assertOutcomesHaveNotChangedSignificantly(outcomeDifferences: Map<String, String>) {
        var failMess = significantChangesMessage()

        if (failMess.isNotEmpty() && outcomeDifferences.isNotEmpty()) {
            val nDiff = outcomeDifferences.keys.size

            val failingWords = outcomeDifferences.keys.sorted()

            failMess += "Below are the $nDiff words for which there were differences between the expected and achieved outcome.\n"
            for (word in failingWords) {
                failMess +=
                    "\n---------------------------------------\n\n" +
                        "Word: $word\n" +
                        "    " +
                        outcomeDifferences.getValue(word).replace("\n", "\n    ")
            }
        }
        if (failMess.isNotEmpty()) {
            fail(failMess)
        }
    }

    private fun significantChangesMessage(): String {
        var mess = ""

        try {
            val gotValue = 1.0 * gotOutcomeHist.frequency(OutcomeType.NO_DECOMPS)
            val expValue = 1.0 * expOutcomeHist.frequency(OutcomeType.NO_DECOMPS)
            val tolerance = expValue * expectations.tolerance_NO_DECOMPS
            AssertNumber.performanceHasNotChanged(
                "Words that do not produce any decomps",
                gotValue, expValue, tolerance, false
            )
        } catch (e: AssertionError) {
            mess += "\n" + e.message
        }

        try {
            val gotValue = 1.0 * gotOutcomeHist.frequency(OutcomeType.CORRECT_NOT_PRESENT)
            val expValue = 1.0 * expOutcomeHist.frequency(OutcomeType.CORRECT_NOT_PRESENT)
            val tolerance = expValue * expectations.tolerance_CORRECT_NOT_PRESENT
            AssertNumber.performanceHasNotChanged(
                "Words that do not produce any decomps",
                gotValue, expValue, tolerance, false
            )
        } catch (e: AssertionError) {
            mess += "\n" + e.message
        }

        try {
            val gotValue = 1.0 * gotOutcomeHist.frequency(OutcomeType.CORRECT_NOT_FIRST)
            val expValue = 1.0 * expOutcomeHist.frequency(OutcomeType.CORRECT_NOT_FIRST)
            val tolerance = expValue * expectations.tolerance_CORRECT_NOT_FIRST
            AssertNumber.performanceHasNotChanged(
                "Words where the first decomp is not correct",
                gotValue, expValue, tolerance, false
            )
        } catch (e: AssertionError) {
            mess += "\n" + e.message
        }

        return mess
    }

    private fun checkOutcome(
        word: String,
        gotOutcome: AnalysisOutcome,
        expectations: MorphAnalCurrentExpectationsAbstract,
        goldStandard: MorphAnalGoldStandardAbstract,
        outcomeDiffs: MutableMap<String, String>,
    ) {
        val expOutcomeType = expectations.expectedOutcome(word)
        expOutcomeHist.updateFreq(expOutcomeType)

        val correctDecomps = goldStandard.correctDecomps(word)
            ?.map { normalizeGoldDecompForComparison(it) }
            ?.toTypedArray()
        val gotOutcomeType = expectations.type4outcome(gotOutcome, correctDecomps)
        gotOutcomeHist.updateFreq(gotOutcomeType)

        if (gotOutcomeType != expOutcomeType) {
            val diffMess = diffMessage(expOutcomeType, gotOutcomeType, gotOutcome, correctDecomps)
            logOutcomeDifference(word, diffMess, outcomeDiffs)
        }
    }

    private fun diffMessage(
        expOutcomeType: OutcomeType,
        gotOutcomeType: OutcomeType,
        gotOutcome: AnalysisOutcome,
        correctDecomps: Array<String>?,
    ): String {
        var mess = ""
        val improved = gotOutcomeType.compareTo(expOutcomeType) > 0

        if (improved) {
            mess += "GOOD NEWS\n"
            mess += improvementMessage(expOutcomeType)
        } else {
            mess += "BAD NEWS\n"
            mess += worseningMessage(expOutcomeType)
            mess += currentStateMessage(gotOutcome, correctDecomps)
        }

        return mess
    }

    private fun currentStateMessage(gotOutcome: AnalysisOutcome, correctDecomps: Array<String>?): String {
        return "\n" +
            "Correct decomps :\n  ${correctDecomps?.joinToString("\n  ")}\n" +
            "Got decomps:\n${gotOutcome.joinDecomps()}"
    }

    private fun worseningMessage(expOutcomeType: OutcomeType): String {
        return when (expOutcomeType) {
            OutcomeType.SUCCESS -> "First decomposition used to be correct"
            OutcomeType.CORRECT_NOT_FIRST -> "Correct decompositions used to be somewhere in the list"
            else -> ""
        }
    }

    private fun improvementMessage(expOutcomeType: OutcomeType): String {
        return when (expOutcomeType) {
            OutcomeType.CORRECT_NOT_FIRST -> "Correct decomposition is now first in the list."
            else -> ""
        }
    }

    private fun logOutcomeDifference(word: String, diffMess: String, outcomeDifferences: MutableMap<String, String>) {
        outcomeDifferences[word] = diffMess
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

    private fun echo(mess: String) {
        println(mess)
    }
}
