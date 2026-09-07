package org.iutools.lib.testing

import org.junit.jupiter.api.TestInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Port of ca.nrc.testing.AssertRuntime from
 * https://github.com/nrc-cnrc/java-utils (java-utils-core).
 *
 * Assert that the time an operation takes has not changed relative to a
 * baseline recorded FOR THE CURRENT MACHINE.
 *
 * The first time a given operation is checked (in a given test method),
 * there is no baseline yet: the assertion passes and the measured time is
 * written out as the baseline. Every later run compares against that
 * recorded value and fails if the runtime has moved -- in EITHER direction
 * -- by more than the tolerance. A big speed-up is a failure on purpose:
 * it means the recorded baseline is stale and should be lowered so the
 * test can still catch a future slow-down.
 *
 * The baseline lives in a per-test-method JSON file under the build tree
 * (TestDirs.persistentResourcesFile("expectedRuntimes.json")) and is
 * deliberately NOT committed to git: absolute speed is machine-dependent,
 * so no single number would be right everywhere. `./gradlew clean` drops
 * the file and the next run simply re-records it.
 *
 * The original read/wrote the file with Jackson's ObjectMapper. This port
 * reads and writes the small flat `{"operation name": number, ...}` object
 * by hand, to avoid pulling in a JSON library (same reasoning as
 * SimpleLruCache replacing Caffeine in :core). The on-disk format is
 * unchanged.
 */
object AssertRuntime {

    fun runtimeHasNotChanged(
        gotTime: Double,
        percTolerance: Double?,
        ofWhat: String,
        testInfo: TestInfo,
    ) {
        runtimeHasNotChanged(gotTime, Pair(percTolerance, percTolerance), ofWhat, testInfo, null)
    }

    fun runtimeHasNotChanged(
        gotTime: Double,
        percTolerances: Pair<Double?, Double?>,
        ofWhat: String,
        testInfo: TestInfo,
    ) {
        runtimeHasNotChanged(gotTime, percTolerances, ofWhat, testInfo, null)
    }

    fun runtimeHasNotChanged(
        gotTime: Double,
        percTolerances: Pair<Double?, Double?>,
        ofWhat: String,
        testInfo: TestInfo,
        highIsBad: Boolean?,
    ) {
        runtimeHasNotChanged("", gotTime, percTolerances, ofWhat, testInfo, highIsBad)
    }

    fun runtimeHasNotChanged(
        mess: String,
        gotTime: Double,
        percTolerances: Pair<Double?, Double?>,
        ofWhat: String,
        testInfo: TestInfo,
        highIsBad: Boolean?,
    ) {
        // By default, a high run time is a bad thing.
        val highRuntimeIsBad = highIsBad ?: true
        val percToleranceWorsened = percTolerances.first
        val percToleranceImpr = percTolerances.second

        val expTime = expTimeFor(ofWhat, testInfo)
        if (expTime == null) {
            // No time expectation for the current machine yet -- store the
            // current time so future runs of this test have a point of
            // comparison.
            setExpTimeFor(gotTime, ofWhat, testInfo)
            return
        }

        val toleranceWorsened = percToleranceWorsened?.let { it * expTime }
        val toleranceImpr = percToleranceImpr?.let { it * expTime }

        val tolWorsenedAsStr = percToleranceWorsened?.let { "%.1f".format(it * 100) + "%" }
        val tolImprAsStr = percToleranceImpr?.let { "%.1f".format(it * 100) + "%" }

        val fullMess = mess +
            "\nRuntime for '$ofWhat' has changed by more than the expected tolerances " +
            "(impr. < $tolImprAsStr, worsening < $tolWorsenedAsStr).\n\n" +
            "This can happen intermittently if your computer was more/less busy than usual when you ran the test.\n" +
            "If, on the other hand, the new runtime is the \"new normal\", you should change the " +
            "expectation for that test by changing the '$ofWhat' attribute in file:\n\n" +
            "   ${benchmarksFile(testInfo)}\n"

        AssertNumber.performanceHasNotChanged(
            ofWhat, gotTime, expTime,
            Pair(toleranceWorsened, toleranceImpr),
            !highRuntimeIsBad, fullMess,
        )
    }

    fun benchmarksFile(testInfo: TestInfo): Path {
        val fPath = TestDirs(testInfo).persistentResourcesFile("expectedRuntimes.json")
        val file = fPath.toFile()
        if (!file.exists()) {
            file.createNewFile()
        }
        return fPath
    }

    fun clearAllExpTimes(testInfo: TestInfo) {
        Files.delete(benchmarksFile(testInfo))
    }

    fun clearExpTimeFor(ofWhat: String, testInfo: TestInfo) {
        setExpTimeFor(null, ofWhat, testInfo)
    }

    fun setExpTimeFor(time: Double?, ofWhat: String, testInfo: TestInfo) {
        val expTimes = LinkedHashMap(expTimesHash(testInfo))
        expTimes[ofWhat] = time
        writeExpTimes(expTimes, benchmarksFile(testInfo).toFile())
    }

    private fun expTimeFor(ofWhat: String, testInfo: TestInfo): Double? =
        expTimesHash(testInfo)[ofWhat]

    private fun expTimesHash(testInfo: TestInfo): Map<String, Double?> {
        val file = benchmarksFile(testInfo).toFile()
        if (!file.exists() || file.length() == 0L) {
            return emptyMap()
        }
        return parseExpTimes(file.readText())
    }

    // --- Minimal JSON I/O for a flat {String: Double|null} object ----------
    //
    // The only writer of these files is writeExpTimes() below, so the parser
    // only has to understand that writer's output: an object whose values
    // are either a JSON number or `null`.

    private fun writeExpTimes(map: Map<String, Double?>, file: File) {
        val body = map.entries.joinToString(",") { (key, value) ->
            jsonQuote(key) + ":" + (value?.toString() ?: "null")
        }
        file.writeText("{$body}")
    }

    private fun parseExpTimes(text: String): Map<String, Double?> {
        val result = LinkedHashMap<String, Double?>()
        val s = text.trim()
        if (s.isEmpty()) {
            return result
        }
        require(s.startsWith("{") && s.endsWith("}")) { "Not a JSON object: $s" }

        var i = 1
        fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        skipWhitespace()
        if (i < s.length && s[i] == '}') {
            return result
        }
        while (i < s.length) {
            skipWhitespace()
            require(s[i] == '"') { "Expected a quoted key at offset $i in: $s" }
            val (key, afterKey) = parseJsonString(s, i)
            i = afterKey
            skipWhitespace()
            require(i < s.length && s[i] == ':') { "Expected ':' at offset $i in: $s" }
            i++
            skipWhitespace()
            if (s.startsWith("null", i)) {
                result[key] = null
                i += "null".length
            } else {
                val start = i
                while (i < s.length && s[i] != ',' && s[i] != '}' && !s[i].isWhitespace()) i++
                result[key] = s.substring(start, i).toDouble()
            }
            skipWhitespace()
            if (i < s.length && s[i] == ',') {
                i++
                continue
            }
            break
        }
        return result
    }

    private fun jsonQuote(raw: String): String {
        val sb = StringBuilder("\"")
        for (c in raw) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun parseJsonString(s: String, from: Int): Pair<String, Int> {
        // s[from] is the opening quote.
        val sb = StringBuilder()
        var i = from + 1
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> return Pair(sb.toString(), i + 1)
                c == '\\' -> {
                    i++
                    when (s[i]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            sb.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(s[i])
                    }
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        throw IllegalArgumentException("Unterminated JSON string in: $s")
    }
}
