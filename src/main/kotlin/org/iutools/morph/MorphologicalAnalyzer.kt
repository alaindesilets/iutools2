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

    // internal (not protected): MorphAnalyzerTask, in the same package but not
    // a subclass, needs to call this too — Kotlin's `protected` is subclass-only,
    // unlike Java's package+subclass visibility.
    @Throws(MorphologicalAnalyzerException::class, TimeoutException::class)
    internal abstract fun doDecompose(word: String, lenient: Boolean?): Array<Decomposition>

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
}
