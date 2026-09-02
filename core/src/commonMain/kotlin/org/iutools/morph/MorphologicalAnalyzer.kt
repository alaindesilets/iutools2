package org.iutools.morph

import org.apache.logging.log4j.LogManager
import org.iutools.lib.Debug
import org.iutools.utilities.StopWatch
import java.util.concurrent.TimeoutException

/*
 * The original supported 3 timeout strategies (EXECUTOR/STOPWATCH/BOTH,
 * switchable via setTimeoutStrategy()). Confirmed via grep that
 * setTimeoutStrategy() is never called anywhere in the codebase — the
 * analyzer always runs with the default STOPWATCH strategy — so the
 * Executor/Future-based path (invokeThroughExecutor, the executor thread
 * pool, taskFutures, shutdownExecutorPool, traceTasks) was dropped here.
 */
abstract class MorphologicalAnalyzer : AutoCloseable {

    /** If non-null, stop after you have found that number of decompositions. */
    protected var _stopAfterNDecomps: Int? = null

    protected var millisTimeout: Long = 10 * 1000
    protected var timeoutActive: Boolean = true
    protected var stpw: StopWatch? = null
    @JvmField
    protected var decomposeCompositeRoot: Boolean = true

    fun setDecomposeCompositeRoot(value: Boolean) {
        decomposeCompositeRoot = value
    }

    fun stopAfterN(maxDecomps: Int): MorphologicalAnalyzer {
        _stopAfterNDecomps = maxDecomps
        return this
    }

    override fun close() {
        println("--** MorphologicalAnalyzer.close: INVOKED")
    }

    @Throws(MorphologicalAnalyzerException::class)
    fun isDecomposable(word: String): Boolean {
        var answer = false
        try {
            val decomps = decomposeWord(word)
            if (decomps.isNotEmpty()) {
                answer = true
            }
        } catch (e: TimeoutException) {
            // If analysis times out, consider that the word is NOT decomposable
        }
        return answer
    }

    @JvmOverloads
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class)
    fun decomposeWord(word: String, lenientIn: Boolean? = null): Array<Decomposition> {
        val lenient = lenientIn ?: true

        val start = System.currentTimeMillis()
        if (timeoutActive) {
            this.stpw = StopWatch(millisTimeout, "Decomposing word=$word").start()
        }

        val tLogger = LogManager.getLogger("org.iutools.morph.decomposeWord")
        tLogger.trace("word=$word, lenient=$lenient")

        tLogger.trace("Decomp of word=$word; STARTS at ${start}msecs")

        val decompositions = invokeDirectly(word, lenient)

        val elapsed = System.currentTimeMillis() - start
        tLogger.trace("Decomposition of word=$word; ENDS with elapsed=${elapsed}msecs")

        return decompositions
    }

    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class)
    private fun invokeDirectly(word: String, lenient: Boolean): Array<Decomposition> {
        val tLogger = LogManager.getLogger("org.iutools.morph.invokeDirectly")

        val start = System.currentTimeMillis()
        var decomps: Array<Decomposition>
        try {
            decomps = doDecompose(word, lenient)
        } catch (e: TimeoutException) {
            tLogger.trace("TimeoutException")
            throw e
        } catch (e: Exception) {
            tLogger.trace("Caught Exception e.class=${e.javaClass}, e.cause=${e.cause}, e=${Debug.printCallStack()}")
            val cause = e.cause ?: e
            if (cause is TimeoutException) {
                throw cause
            } else {
                throw MorphologicalAnalyzerException(cause as? Exception ?: e)
            }
        } finally {
            checkElapsedTime(word, start)
        }

        return decomps
    }

    private fun checkElapsedTime(word: String, start: Long) {
        val tLogger = LogManager.getLogger("org.iutools.morph.MorphologicalAnalyzer.checkElapsedTime")

        if (timeoutActive) {
            val elapsedMSecs = System.currentTimeMillis() - start
            if (elapsedMSecs > 1.1 * millisTimeout) {
                tLogger.trace(
                    "word=$word; Elapsed time was significantly greater than the specified timeout value.\n" +
                        "  Elapsed: ${elapsedMSecs / 1000}secs\n" +
                        "  Timeout: ${millisTimeout / 1000}secs"
                )
            }

            val excess = elapsedMSecs - millisTimeout
            if (excess > 2 * 1000) {
                tLogger.trace(
                    "Word $word exceeded millisTimeout=$millisTimeout by ${excess}msecs " +
                        "(elapsedMSecs=${elapsedMSecs}msecs)\ncallStack=${Debug.printCallStack()}"
                )
            }
        }

        if (tLogger.isTraceEnabled()) {
            tLogger.trace("Upon exit, number of threads = ${Thread.activeCount()}")
        }
    }

    @Throws(MorphologicalAnalyzerException::class, TimeoutException::class)
    internal fun doDecompose(word: String): Array<Decomposition> = doDecompose(word, null)

    // protected (not internal): MorphologicalAnalyzer_FST lives in its own
    // Gradle module (:fst), so it must be able to override this from outside
    // :core. (The only in-module non-subclass caller, MorphAnalyzerTask, was
    // dead code from the original port and has been removed.)
    @Throws(MorphologicalAnalyzerException::class, TimeoutException::class)
    protected abstract fun doDecompose(word: String, lenient: Boolean?): Array<Decomposition>

    fun setTimeout(value: Long?): MorphologicalAnalyzer {
        if (value != null) {
            millisTimeout = value
        }
        return this
    }

    fun deactivateTimeout(): MorphologicalAnalyzer {
        timeoutActive = false
        return this
    }

    fun activateTimeout(): MorphologicalAnalyzer {
        timeoutActive = true
        return this
    }

    /**
     * The decomposition ranking shared by every concrete analyzer, so
     * MorphologicalAnalyzer_R2L (Uqailaut) and MorphologicalAnalyzer_FST order
     * their output the same way. Stable -- decompositions that tie on every
     * applied key keep their input order -- sorting ascending on:
     *
     *   1. weight: strict readings (0) ahead of lenient / guessed-final-
     *      consonant ones (1). Inert for R2L, which has no weights.
     *   2. root canonical length, negated: the longest known root wins.
     *   3. number of non-root morphemes: fewest affixes/endings wins.
     *      Keys 2 and 3 are exactly DecompositionState.compareTo's own two.
     *   4. (only when [breakTiesByMorphemeFrequency]) summed morpheme
     *      frequency, negated: prefers a reading whose morphemes are more
     *      often the analyzer's own top pick across the Nunavut Hansard (see
     *      MorphemeFrequencyPrior). NOT part of Benoit Farley's original
     *      algorithm.
     *   5. (only when [breakTiesByMorphemeFrequency], i.e. the FST) the
     *      decomposition string itself, lexicographically -- a final
     *      deterministic tie-break so the result is a strict total order and
     *      never depends on input order (the FST's optimized-lookup
     *      traversal order is arbitrary, and net.sf.hfst vs native
     *      hfst-lookup enumerate paths differently). NOT applied for R2L:
     *      its search discovery order, which the stable sort otherwise keeps
     *      for tied decompositions, is itself informative -- forcing
     *      lexicographic order there regressed first-place-correct on the
     *      Hansard gold standard from 673 to 471.
     *
     * Each analyzer supplies the one input it alone can measure exactly (the
     * root's canonical-form length, and its weight); keys 3-5 are read
     * back off the Decomposition here so they cannot drift between analyzers.
     *
     * [breakTiesByMorphemeFrequency] is opt-in because it only helps when the
     * input order carries no signal, which is the FST's case (its optimized-
     * lookup traversal order is arbitrary). R2L's search discovery order,
     * which the stable sort otherwise preserves for tied decompositions, is
     * itself informative -- turning the frequency key on for R2L measurably
     * WORSENED first-place-correct on the Hansard gold standard (247 -> 257
     * "correct but not first"), the global-frequency prior over-promoting
     * common endings over the contextually-correct rarer ones.
     */
    protected fun sortDecompositions(
        ranked: List<RankedDecomposition>,
        breakTiesByMorphemeFrequency: Boolean = false,
    ): Array<Decomposition> {
        var order = compareBy<RankedDecomposition> { it.weight }
            .thenByDescending { it.rootCanonicalLength }
            .thenBy { it.decomposition.components().size - 1 }
        if (breakTiesByMorphemeFrequency) {
            order = order
                .thenByDescending { MorphemeFrequencyPrior.score(morphemeIdsOf(it.decomposition)) }
                .thenBy { it.decomposition.toString() }
        }
        val sorted = ranked.sortedWith(order)
        return Array(sorted.size) { sorted[it].decomposition }
    }

    // Each component is "surface:id" (R2L) or bare "id" (FST); no morpheme id
    // itself contains ':'.
    private fun morphemeIdsOf(decomposition: Decomposition): List<String> =
        decomposition.components().map { it.substringAfterLast(':', it) }
}

/**
 * A decomposition paired with the ranking inputs its producing analyzer
 * measures itself (see MorphologicalAnalyzer.sortDecompositions): the length
 * of the root's canonical/citation form, and -- for the FST, whose LENIENT
 * rule marks guessed-final-consonant readings -- a weight (0 = strict,
 * 1 = lenient). R2L has no weights and leaves it 0.
 */
class RankedDecomposition(
    val decomposition: Decomposition,
    val rootCanonicalLength: Int,
    val weight: Float = 0f,
)
