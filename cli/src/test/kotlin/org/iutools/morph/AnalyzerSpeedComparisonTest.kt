package org.iutools.morph

import org.iutools.morph.fst.MorphologicalAnalyzer_FST
import org.iutools.morph.r2l.MorphologicalAnalyzer_R2L
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeoutException
import kotlin.system.measureNanoTime

/*
 * Wall-clock comparison of the two in-process analyzers -- Benoit Farley's
 * ported MorphologicalAnalyzer_R2L and the HFST MorphologicalAnalyzer_FST
 * (read via the vendored net.sf.hfst Java reader) -- over the same
 * population, called through the same public entry point
 * (MorphologicalAnalyzer.decomposeWord()).
 *
 * This is NOT part of the regression gate: it is slow, its numbers are
 * machine-specific, and it asserts nothing about speed (only that neither
 * analyzer throws on the sample). It stays dormant unless explicitly asked
 * for:
 *
 *   ./gradlew :cli:test --tests '*AnalyzerSpeedComparisonTest' \
 *       -Diutools.benchmark=true [-Diutools.benchmark.words=2000]
 *
 * Population: the N most frequent Nunavut-Hansard word forms
 * (data/grammar/fst/hansard-cache/top10k_words.txt, syllabics), N =
 * iutools.benchmark.words (default 1000). The SAME syllabics strings are
 * fed to both analyzers; each transcodes to Roman internally, so the
 * transcoding cost is counted on both sides.
 *
 * For the volume/coverage side of the same comparison (decomposition
 * counts, zero-decomp words) over the full 10k population, see
 * data/grammar/fst/hansard_volume_speed.py -- that one drives the native
 * `hfst-lookup` binary, not this Kotlin class.
 */
class AnalyzerSpeedComparisonTest {

    private data class Run(
        val label: String,
        val constructionMillis: Long,
        val analysisMillis: Long,
        val wordCount: Int,
        val totalDecomps: Long,
        val zeroDecompWords: Int,
        val errorWords: Int,
        val slowestWord: String,
        val slowestWordMillis: Long,
    ) {
        val millisPerWord get() = analysisMillis.toDouble() / wordCount
        val wordsPerSec get() = wordCount * 1000.0 / analysisMillis
    }

    @Test
    fun compare_R2L_and_FST_wall_clock() {
        assumeTrue(
            System.getProperty("iutools.benchmark") == "true",
            "Dormant benchmark. Re-run with -Diutools.benchmark=true to measure.",
        )
        assumeTrue(
            MorphologicalAnalyzer_FST.isAvailable(),
            "Skipping: data/grammar/fst/lexicon-analyser.hfstol not built.",
        )

        val wordCount = System.getProperty("iutools.benchmark.words")?.toInt() ?: 1000
        val words = loadHansardWords(wordCount)
        val warmupWords = words.take(minOf(200, words.size))

        println()
        println("Speed comparison over ${words.size} most-frequent Hansard word forms")
        println("(warm-up: ${warmupWords.size} words, discarded)")

        val r2l = timeAndRun("R2L (Benoit)", warmupWords, words) { MorphologicalAnalyzer_R2L() }
        val fst = timeAndRun("FST (HFST)", warmupWords, words) { MorphologicalAnalyzer_FST() }

        report(r2l, fst)
    }

    private fun timeAndRun(
        label: String,
        warmupWords: List<String>,
        words: List<String>,
        makeAnalyzer: () -> MorphologicalAnalyzer,
    ): Run {
        var analyzer: MorphologicalAnalyzer
        val constructionMillis = measureNanoTime { analyzer = makeAnalyzer() } / 1_000_000

        analyzer.use { a ->
            for (w in warmupWords) decomposeQuietly(a, w)

            var totalDecomps = 0L
            var zeroDecompWords = 0
            var errorWords = 0
            var slowestWord = ""
            var slowestWordNanos = 0L

            val analysisMillis = measureNanoTime {
                for (w in words) {
                    val nanos = measureNanoTime {
                        val decomps = decomposeQuietly(a, w)
                        when {
                            decomps == null -> errorWords++
                            decomps.isEmpty() -> zeroDecompWords++
                            else -> totalDecomps += decomps.size
                        }
                    }
                    if (nanos > slowestWordNanos) {
                        slowestWordNanos = nanos
                        slowestWord = w
                    }
                }
            } / 1_000_000

            return Run(
                label = label,
                constructionMillis = constructionMillis,
                analysisMillis = analysisMillis,
                wordCount = words.size,
                totalDecomps = totalDecomps,
                zeroDecompWords = zeroDecompWords,
                errorWords = errorWords,
                slowestWord = slowestWord,
                slowestWordMillis = slowestWordNanos / 1_000_000,
            )
        }
    }

    private fun decomposeQuietly(a: MorphologicalAnalyzer, word: String): Array<Decomposition>? =
        try {
            a.decomposeWord(word)
        } catch (e: TimeoutException) {
            null
        } catch (e: MorphologicalAnalyzerException) {
            null
        }

    private fun report(r2l: Run, fst: Run) {
        val col = 16
        fun row(label: String, a: String, b: String) =
            println(label.padEnd(34) + a.padStart(col) + b.padStart(col))

        println()
        row("", r2l.label, fst.label)
        row("construction (ms)", "${r2l.constructionMillis}", "${fst.constructionMillis}")
        row("analysis wall-clock (ms)", "${r2l.analysisMillis}", "${fst.analysisMillis}")
        row("ms per word", "%.3f".format(r2l.millisPerWord), "%.3f".format(fst.millisPerWord))
        row("words per second", "%.0f".format(r2l.wordsPerSec), "%.0f".format(fst.wordsPerSec))
        row("total decompositions", "${r2l.totalDecomps}", "${fst.totalDecomps}")
        row("words with zero decomps", "${r2l.zeroDecompWords}", "${fst.zeroDecompWords}")
        row("words that errored/timed out", "${r2l.errorWords}", "${fst.errorWords}")
        row("slowest single word (ms)", "${r2l.slowestWordMillis}", "${fst.slowestWordMillis}")
        row("  (which word)", r2l.slowestWord, fst.slowestWord)
        println()
        val ratio = r2l.analysisMillis.toDouble() / fst.analysisMillis
        if (ratio >= 1.0) {
            println("FST is %.1fx faster than R2L on this sample.".format(ratio))
        } else {
            println("R2L is %.1fx faster than FST on this sample.".format(1.0 / ratio))
        }
    }

    private fun loadHansardWords(count: Int): List<String> {
        val file = findRepoFile("data/grammar/fst/hansard-cache/top10k_words.txt")
        return file.readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(count)
            .toList()
    }

    // Same walk-up-from-cwd strategy MorphologicalAnalyzer_FST uses to find
    // its transducer: the working directory when Gradle runs the tests is
    // the module dir (cli/) or the repo root depending on invocation.
    private fun findRepoFile(relPath: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relPath)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        throw IllegalStateException("Could not locate $relPath by walking up from ${File(".").absolutePath}")
    }
}
