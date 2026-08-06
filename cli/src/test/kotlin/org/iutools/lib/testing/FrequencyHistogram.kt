package org.iutools.lib.testing

import java.text.DecimalFormat

/**
 * Port of ca.nrc.dtrc.stats.FrequencyHistogram from
 * https://github.com/nrc-cnrc/java-utils (java-utils-data), trimmed to the
 * members actually used by the accuracy test (updateFreq/frequency/
 * totalOccurences/relativeFrequency).
 */
class FrequencyHistogram<T> {
    private val freq4value = mutableMapOf<T, Long>()

    fun updateFreq(value: T, incr: Int = 1) {
        val newFreq = (freq4value[value] ?: 0L) + incr
        freq4value[value] = newFreq
    }

    fun allValues(): Set<T> = freq4value.keys

    fun totalOccurences(): Long {
        var total = 0L
        for (v in allValues()) {
            total += frequency(v)
        }
        return total
    }

    fun frequency(value: T): Long = freq4value[value] ?: 0L

    fun relativeFrequency(value: T): Double {
        return 1.0 * frequency(value) / totalOccurences()
    }

    fun relativeFrequency(value: T, numDecimals: Int): String {
        val percent = 100 * relativeFrequency(value)
        var format = "#"
        for (ii in 0 until numDecimals) {
            if (ii == 0) format += "."
            format += "#"
        }
        return DecimalFormat(format).format(percent) + "%"
    }
}
