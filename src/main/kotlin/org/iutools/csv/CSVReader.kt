package org.iutools.csv

import java.io.BufferedReader
import java.io.IOException

class CSVReader(private val reader: BufferedReader) {

    private val fieldNames: Array<String>

    init {
        val firstLine = reader.readLine()
        fieldNames = firstLine.split(",").toTypedArray()
    }

    @Throws(IOException::class)
    fun readNext(): MutableMap<String, String?>? {
        val line = reader.readLine() ?: return null
        return csvToMap(line)
    }

    @Throws(IOException::class)
    fun readAll(): List<MutableMap<String, String?>> {
        val allRows = mutableListOf<MutableMap<String, String?>>()
        var row = readNext()
        while (row != null) {
            allRows.add(row)
            row = readNext()
        }
        return allRows
    }

    private fun csvToMap(line: String): MutableMap<String, String?> {
        val currentRow = mutableMapOf<String, String?>()
        val values = mutableListOf<String>()
        var inString = false
        var pos = 0
        var c = 0.toChar()
        var i = 0
        while (i < line.length) {
            c = line[i]
            if (c == '"') {
                if (inString) {
                    if (i < line.length - 1) {
                        if (line[i + 1] != '"') {
                            inString = false
                        } else {
                            i++
                        }
                    }
                } else {
                    inString = true
                }
            } else if (c == ',' && !inString) {
                values.add(line.substring(pos, i))
                pos = i + 1
            }
            i++
        }
        if (c == ',') {
            values.add("")
        } else {
            values.add(line.substring(pos))
        }
        for (idx in values.indices) {
            try {
                currentRow[fieldNames[idx]] = removeQuotesAndNull(values[idx])
            } catch (e: ArrayIndexOutOfBoundsException) {
                System.err.println("line: $line")
            }
        }
        return currentRow
    }

    companion object {
        @JvmStatic
        fun removeQuotesAndNull(str: String): String? {
            return if (str == "") {
                null
            } else if (str[0] == '"') {
                var newStr = str.replaceFirst("^\"".toRegex(), "")
                newStr = newStr.replaceFirst("\"$".toRegex(), "")
                newStr = newStr.replace("\"\"", "\"")
                newStr
            } else {
                str
            }
        }
    }
}
