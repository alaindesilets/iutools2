package org.apache.logging.log4j

/**
 * Minimal stand-in for the log4j2 API surface the ported code uses
 * (trace/debug/error, isTraceEnabled/isErrorEnabled), so the ported
 * Kotlin files can keep their original `import org.apache.logging.log4j.*`
 * lines unchanged. Not a dependency on the real log4j jar.
 *
 * Verbosity is controlled by the "iutools.log.level" system property
 * (one of: trace, debug, error, off). Defaults to "error".
 */
object LogManager {
    fun getLogger(name: String): Logger = Logger(name)
}

enum class LogLevel(val rank: Int) { TRACE(0), DEBUG(1), ERROR(2), OFF(3) }

class Logger(private val name: String) {

    fun trace(message: Any?) = log(LogLevel.TRACE, message)
    fun debug(message: Any?) = log(LogLevel.DEBUG, message)
    fun error(message: Any?) = log(LogLevel.ERROR, message)
    fun error(message: Any?, t: Throwable) {
        log(LogLevel.ERROR, message)
        if (currentLevel.rank <= LogLevel.ERROR.rank) t.printStackTrace()
    }

    fun isTraceEnabled(): Boolean = currentLevel.rank <= LogLevel.TRACE.rank
    fun isDebugEnabled(): Boolean = currentLevel.rank <= LogLevel.DEBUG.rank
    fun isErrorEnabled(): Boolean = currentLevel.rank <= LogLevel.ERROR.rank

    private fun log(level: LogLevel, message: Any?) {
        if (level.rank >= currentLevel.rank) {
            System.err.println("${level.name} $name: $message")
        }
    }

    companion object {
        private val currentLevel: LogLevel by lazy {
            when (System.getProperty("iutools.log.level", "error").lowercase()) {
                "trace" -> LogLevel.TRACE
                "debug" -> LogLevel.DEBUG
                "off" -> LogLevel.OFF
                else -> LogLevel.ERROR
            }
        }
    }
}
