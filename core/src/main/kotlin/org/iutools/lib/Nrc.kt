package org.iutools.lib

import java.io.InputStream

/**
 * Minimal local replacements for the handful of ca.nrc.* helpers the
 * original Java code used (that dependency isn't on Maven Central).
 */
object Debug {
    fun printCallStack(): String {
        return Thread.currentThread().stackTrace.joinToString("\n") { it.toString() }
    }
}

object StringUtils {
    fun join(iterator: Iterator<*>, separator: String): String {
        return iterator.asSequence().joinToString(separator)
    }
}

object ResourceGetter {
    fun getResourceAsStream(path: String): InputStream? {
        return ResourceGetter::class.java.classLoader.getResourceAsStream(path)
    }
}
