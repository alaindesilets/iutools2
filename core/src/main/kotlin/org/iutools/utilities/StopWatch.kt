package org.iutools.utilities

import org.apache.logging.log4j.LogManager
import org.iutools.lib.Debug
import java.io.File
import java.io.FileWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Monitors the running time of a long operation and raises a TimeoutException
 * if it exceeds a threshold ("standalone mode" — the only mode actually used
 * by MorphologicalAnalyzer's default STOPWATCH timeout strategy).
 */
class StopWatch {

    private var taskName: String = ""
    var timeoutMSecs: Long = Long.MAX_VALUE
    private var deactivated = false
    private val updateClockEveryNTimes = 100

    private var startTimeMSecs: Long = -1
    private var lapStartTimeMSecs: Long = -1
    private var clockNotForcedSince = 0
    private var checksSoFar: Long = 0

    constructor()
    constructor(timeoutMSecs: Long, taskName: String = "") {
        this.timeoutMSecs = timeoutMSecs
        this.taskName = taskName
    }

    fun start(): StopWatch {
        reset()
        return this
    }

    fun reset() {
        startTimeMSecs = nowMSecs()
        lapStartTimeMSecs = startTimeMSecs
    }

    @Throws(TimeoutException::class)
    fun check(message: String) {
        val mLogger = LogManager.getLogger("org.iutools.utilities.StopWatch.check")

        if (deactivated) {
            return
        }
        if (startTimeMSecs == -1L) {
            startTimeMSecs = nowMSecs()
        }

        checksSoFar++

        val traceThisCall = (checksSoFar % 10000) == 0L

        if (traceThisCall) {
            mLogger.trace("Checking for task=$taskName (#checks=$checksSoFar)")
        }

        checkForInterruption()
        forceClockUpdate()
        checkElapsedTime(traceThisCall)
    }

    private fun startTime(unit: TimeUnit): Long {
        return unit.convert(startTimeMSecs, TimeUnit.MILLISECONDS)
    }

    @Throws(StopWatchException::class)
    fun totalTime(): Long = totalTime(TimeUnit.MILLISECONDS)

    @Throws(StopWatchException::class)
    fun totalTime(unit: TimeUnit): Long {
        val start = startTime(unit)
        val now = now(unit)
        return now - start
    }

    @Throws(TimeoutException::class)
    private fun checkElapsedTime(traceThisCall: Boolean) {
        val mLogger = LogManager.getLogger("org.iutools.utilities.StopWatch.checkElapsedTime")
        val elapsed = nowMSecs() - startTimeMSecs
        if (traceThisCall) {
            mLogger.trace("Task $taskName elapsed = ${elapsed / 1000} secs (max: ${timeoutMSecs / 1000} secs)")
            mLogger.trace("Stack call is:\n${Debug.printCallStack()}")
        }

        if (elapsed > timeoutMSecs) {
            mLogger.trace("Task $taskName exceeded its allocated time.\nThrowing a TimeoutException")
            throw TimeoutException("Task $taskName\nTimed out after ${elapsed}msecs")
        }
    }

    @Throws(TimeoutException::class)
    private fun checkForInterruption() {
        val mLogger = LogManager.getLogger("org.iutools.utilities.StopWatch.checkForInterruption")

        if (Thread.interrupted()) {
            mLogger.trace("Task $taskName was interrupted.\nRaising TimeoutException")
            // Note: The call to interrupted() sets the thread's interrupted
            // status to false. So we invoke interrupt() to reset it true in
            // case someone above us depends on that.
            Thread.currentThread().interrupt()
            throw TimeoutException("Analyzer task was interrupted")
        }
    }

    @Throws(StopWatchException::class)
    fun lapTime(): Long = lapTime(TimeUnit.MILLISECONDS)

    @Throws(StopWatchException::class)
    fun lapTime(targetUnit: TimeUnit): Long {
        val nowMSecs = now(TimeUnit.MILLISECONDS)
        val timeMSecs = nowMSecs - lapStartTimeMSecs
        val timeInTargetUnit = targetUnit.convert(timeMSecs, TimeUnit.MILLISECONDS)
        lapStartTimeMSecs = nowMSecs
        return timeInTargetUnit
    }

    private enum class ClockUpdateStrategy { NONE, CALL_STACK, WRITE_FILE, CHECK_FILE }

    /**
     * For some reason or another, the StopWatch does not always seem to keep
     * the time or check for interruption correctly unless it does some
     * operation that produces or responds to interrupts.
     *
     * Operations that we have found to work are: checking existence of a
     * file, writing to a file, or getting the stack trace. Not sure what
     * kind of "dark magic" is at play here, but this hack seems to do the
     * trick (ported as-is from the original Java implementation).
     */
    @Throws(MorphTimeoutException::class)
    private fun forceClockUpdate() {
        val mLogger = LogManager.getLogger("org.iutools.utilities.StopWatch.forceClockUpdate")

        val updateStrat = ClockUpdateStrategy.CHECK_FILE

        clockNotForcedSince++
        // Don't force at each iteration as it will slow things down
        if (clockNotForcedSince > updateClockEveryNTimes) {
            mLogger.trace("Forcing clock updated for Task $taskName")
            clockNotForcedSince = 0

            when (updateStrat) {
                ClockUpdateStrategy.CALL_STACK -> Debug.printCallStack()
                ClockUpdateStrategy.WRITE_FILE -> {
                    val file = File("/tmp/stopwatch.txt")
                    try {
                        val fr = FileWriter(file)
                        fr.write("nevermind")
                        fr.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                ClockUpdateStrategy.CHECK_FILE -> {
                    val file = File("/tmp/stopwatch.txt")
                    file.exists()
                }
                ClockUpdateStrategy.NONE -> {}
            }
        }
    }

    fun disactivate() {
        deactivated = true
    }

    companion object {
        @JvmStatic
        fun nowMSecs(): Long = System.nanoTime() / 1000000

        @JvmStatic
        fun elapsedMsecsSince(start: Long): Long = nowMSecs() - start

        @JvmStatic
        @Throws(StopWatchException::class)
        fun now(unit: TimeUnit): Long {
            val timeNanoSecs = System.nanoTime()
            return unit.convert(timeNanoSecs, TimeUnit.NANOSECONDS)
        }

        @JvmStatic
        @Throws(StopWatchException::class)
        fun elapsedSince(start: Long, unit: TimeUnit): Long {
            val end = now(unit)
            return end - start
        }
    }
}
