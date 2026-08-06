package org.iutools.morph

import org.apache.logging.log4j.LogManager
import java.util.concurrent.Callable
import java.util.concurrent.TimeoutException

/**
 * Class for calling the morphological analyzer in a way that supports timeouts.
 */
class MorphAnalyzerTask(
    private val word: String,
    lenient: Boolean?,
    private val analyzer: MorphologicalAnalyzer
) : Callable<Array<Decomposition>> {

    private val lenient: Boolean = lenient ?: true

    override fun call(): Array<Decomposition> {
        val mLogger = LogManager.getLogger("org.iutools.morph.MorphAnalyzerTask.call")
        mLogger.trace("Calling on word=$word")
        var decomps: Array<Decomposition> = arrayOf()
        try {
            decomps = analyzer.doDecompose(word, lenient)
        } catch (e: TimeoutException) {
            mLogger.trace("For word=$word, caught TimeoutException e=${e.message}")
            throw e
        }
        mLogger.trace("exiting for word=$word")
        return decomps
    }
}
