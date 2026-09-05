package org.iutools.morph

import org.iutools.morph.fst.MorphologicalAnalyzer_FST
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail

/*
 * Guards that data/grammar/fst/sort_decomps.py (Python) and
 * MorphologicalAnalyzer_FST.rankRawResults (Kotlin) apply the IDENTICAL
 * dedup + decomposition ranking. Both are fed the same frozen bag of raw
 * transducer outputs -- data/grammar/fst/sort-sync-decomps.txt, ~21k
 * "<analysis>\t<weight>" lines from `hfst-lookup` over the fair Hansard gold
 * words, mixed together across words, with duplicates and weight variants
 * left in on purpose so the min-weight dedup is exercised -- and must
 * produce byte-identical ordered "{canonical/id}{...}" lists.
 *
 * The two rankings are hand-kept in sync (data/grammar/fst/benoit_sort.py is a
 * Python translation of MorphologicalAnalyzer.sortDecompositions); this test
 * is the tripwire for drift. A flat mixed bag also verifies the ranking is a
 * strict total order that's a pure function of decomposition content -- a
 * context-dependent comparator would give nonsense here.
 *
 * Skips itself when python3 / the fixtures are absent -- a dev-time check,
 * like MorphologicalAnalyzer_FST__AccuracyTest. Regenerate the frozen list
 * (rebuild it from `hfst-lookup` over the same words) and re-review whenever
 * the transducer changes.
 */
class SortSyncTest {

    @Test
    fun python_and_kotlin_rank_identically() {
        val frozen = repoFile("data/grammar/fst/sort-sync-decomps.txt")
        val wrapper = repoFile("data/grammar/fst/sort_decomps.py")
        assumeTrue(frozen.isFile && wrapper.isFile, "sort-sync fixtures not found")
        val python = which("python3") ?: which("python")
        assumeTrue(python != null, "python3 not on PATH")

        val rawLines = frozen.readLines().filter { it.isNotBlank() }

        val kotlinOrder = MorphologicalAnalyzer_FST()
            .rankRawResults(rawLines)
            .map { it.toString() }

        val pythonOrder = runPython(python!!, wrapper, rawLines)

        assertEquals(
            pythonOrder.size, kotlinOrder.size,
            "Python and Kotlin kept a different number of decompositions after dedup",
        )
        val firstDiff = kotlinOrder.indices.firstOrNull { kotlinOrder[it] != pythonOrder[it] }
        if (firstDiff != null) {
            val from = (firstDiff - 2).coerceAtLeast(0)
            val to = (firstDiff + 3).coerceAtMost(kotlinOrder.size)
            fail(
                "Python and Kotlin FST ranking diverge at index $firstDiff of ${kotlinOrder.size}:\n" +
                    (from until to).joinToString("\n") { "  [$it] kt=${kotlinOrder[it]}  py=${pythonOrder[it]}" },
            )
        }
    }

    // stdin is written on its own thread: the child fills stdout (~18k lines)
    // while we are still feeding it ~21k lines of stdin, so a single-threaded
    // write-then-read would deadlock once the pipe buffers fill.
    private fun runPython(python: String, wrapper: File, stdinLines: List<String>): List<String> {
        val proc = ProcessBuilder(python, wrapper.absolutePath)
            .redirectErrorStream(true)
            .directory(wrapper.parentFile)
            .start()
        val writer = Thread {
            proc.outputStream.bufferedWriter().use { w -> stdinLines.forEach { w.append(it).append('\n') } }
        }
        writer.start()
        val out = proc.inputStream.bufferedReader().readLines()
        writer.join()
        check(proc.waitFor() == 0) { "sort_decomps.py exited non-zero:\n" + out.joinToString("\n") }
        return out.filter { it.isNotBlank() }
    }

    private fun repoFile(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, rel)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return File(rel)
    }

    private fun which(cmd: String): String? =
        System.getenv("PATH")?.split(File.pathSeparator)
            ?.map { File(it, cmd) }?.firstOrNull { it.canExecute() }?.absolutePath
}
