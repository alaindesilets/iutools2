package org.iutools.morph.cli

import kotlinx.coroutines.runBlocking
import org.iutools.corpus.HansardExamplesOutcome
import org.iutools.dictionary.TusaalangaResult
import org.iutools.lookup.DecompositionOutcome
import org.iutools.lookup.FailureReason
import org.iutools.lookup.WordLookup
import org.iutools.lookup.WordLookupPrefs
import org.iutools.lookup.WordLookupResult
import org.iutools.morph.AnalyzerChoice
import org.iutools.morph.Decomposition
import org.iutools.morph.r2l.MorphologicalAnalyzer_R2L
import java.util.Scanner
import java.util.concurrent.TimeoutException
import kotlin.system.exitProcess

/*
 * Reproduces the argument surface of the original iutools CLI's segment_iu
 * command (org.iutools.cli.CmdSegmentIU / ConsoleCommand), ported from Java.
 * The underlying options framework (Apache Commons CLI + ca.nrc's SubCommand)
 * was NOT ported -- it's shared machinery for iutools' many other
 * subcommands, none of which are in scope here -- only the option names,
 * defaults, and mode-selection logic for this one command were reproduced.
 *
 * Decomposition *display* format intentionally stays this project's own
 * `{surface:morphid}{...}` (Decomposition.toString()), not the original's
 * WordInfo/CompiledCorpus-based rendering, which pulls in scope explicitly
 * excluded from this port (corpus/Elasticsearch layers).
 *
 * --define runs the shared word lookup (:core's WordLookup, the same one the
 * app's screen uses) instead of a bare decomposition: it adds the Spalding
 * dictionary definition. It stays offline -- Spalding is embedded, but the
 * Tusaalanga lookup (network) and the Hansard examples (a large local DB the
 * app downloads) are left out here.
 */

private const val OPT_WORD = "--word"
private const val OPT_DEFINE = "--define"
private const val OPT_LENIENT_DECOMPS = "--lenient-decomps"
private const val OPT_TIMEOUT_SECS = "--timeout-secs"
private const val OPT_PIPELINE = "--pipeline"
private const val OPT_INTERACTIVE = "--interactive"

private enum class Mode { SINGLE_INPUT, DEFINE, INTERACTIVE, PIPELINE }

private class UsageError(message: String) : Exception(message)

private class ParsedArgs(
    val word: String?,
    val lenient: Boolean,
    val timeoutSecs: Long?,
    val mode: Mode,
)

fun main(rawArgs: Array<String>) {
    val args = try {
        parseArgs(rawArgs)
    } catch (e: UsageError) {
        System.err.println("Error: ${e.message}")
        System.err.println()
        System.err.println(usageText())
        exitProcess(1)
    }

    val analyzer = MorphologicalAnalyzer_R2L()
    analyzer.setTimeout(args.timeoutSecs?.let { it * 1000 })

    when (args.mode) {
        Mode.SINGLE_INPUT -> runSingle(analyzer, args.word!!, args.lenient)
        Mode.DEFINE -> runDefine(analyzer, args.word!!, args.lenient)
        Mode.INTERACTIVE -> runInteractive(analyzer, args.lenient)
        Mode.PIPELINE -> runPipeline(analyzer, args.lenient)
    }
}

private fun parseArgs(rawArgs: Array<String>): ParsedArgs {
    var word: String? = null
    var define: String? = null
    var lenient = false
    var timeoutSecs: Long? = null
    var pipeline = false
    var interactive = false

    var i = 0
    while (i < rawArgs.size) {
        when (val a = rawArgs[i]) {
            OPT_WORD -> {
                word = rawArgs.getOrNull(i + 1) ?: throw UsageError("$OPT_WORD requires a value")
                i++
            }
            OPT_DEFINE -> {
                define = rawArgs.getOrNull(i + 1) ?: throw UsageError("$OPT_DEFINE requires a value")
                i++
            }
            OPT_LENIENT_DECOMPS -> lenient = true
            OPT_TIMEOUT_SECS -> {
                val v = rawArgs.getOrNull(i + 1) ?: throw UsageError("$OPT_TIMEOUT_SECS requires a value")
                timeoutSecs = v.toLongOrNull() ?: throw UsageError("$OPT_TIMEOUT_SECS value must be an integer")
                i++
            }
            OPT_PIPELINE -> pipeline = true
            OPT_INTERACTIVE -> interactive = true
            else -> throw UsageError("Unknown option: $a")
        }
        i++
    }

    val modesGiven = listOf(word != null, define != null, pipeline, interactive).count { it }
    if (modesGiven > 1) {
        throw UsageError("$OPT_WORD, $OPT_DEFINE, $OPT_PIPELINE and $OPT_INTERACTIVE are mutually exclusive")
    }

    val mode = when {
        define != null -> Mode.DEFINE
        pipeline -> Mode.PIPELINE
        interactive -> Mode.INTERACTIVE
        else -> Mode.SINGLE_INPUT
    }

    if (mode == Mode.SINGLE_INPUT && word == null) {
        throw UsageError("$OPT_WORD is required unless $OPT_DEFINE, $OPT_PIPELINE or $OPT_INTERACTIVE is given")
    }

    return ParsedArgs(word ?: define, lenient, timeoutSecs, mode)
}

private fun usageText(): String = """
    Usage: segment_iu $OPT_WORD <word> [$OPT_LENIENT_DECOMPS] [$OPT_TIMEOUT_SECS <secs>]
           segment_iu $OPT_DEFINE <word> [$OPT_LENIENT_DECOMPS] [$OPT_TIMEOUT_SECS <secs>]
           segment_iu $OPT_INTERACTIVE [$OPT_LENIENT_DECOMPS] [$OPT_TIMEOUT_SECS <secs>]
           segment_iu $OPT_PIPELINE [$OPT_LENIENT_DECOMPS] [$OPT_TIMEOUT_SECS <secs>]

    Decompose an Inuktut word into its morphemes.

    $OPT_WORD <word>          Decompose a single word and exit.
    $OPT_DEFINE <word>        Look the word up: its Spalding dictionary definition
                              (offline) plus its decomposition. No network.
    $OPT_INTERACTIVE          Prompt for words one at a time (type 'q' to quit).
    $OPT_PIPELINE             Read one word per line from stdin, print one JSON
                              result per line to stdout (for scripting).
    $OPT_LENIENT_DECOMPS      Extend the analysis by allowing a consonant after
                              a final vowel. Off by default.
    $OPT_TIMEOUT_SECS <secs>  Max seconds allowed per word before timing out
                              (default: 10).
""".trimIndent()

private fun decomposeOne(
    analyzer: MorphologicalAnalyzer_R2L,
    word: String,
    lenient: Boolean,
): Result<Array<Decomposition>> {
    return try {
        Result.success(analyzer.decomposeWord(word, lenient))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun runSingle(analyzer: MorphologicalAnalyzer_R2L, word: String, lenient: Boolean) {
    printForUser(word, decomposeOne(analyzer, word, lenient))
}

private fun runDefine(analyzer: MorphologicalAnalyzer_R2L, word: String, lenient: Boolean) {
    val lookup = WordLookup(
        // Offline: no Hansard DB, no Tusaalanga network call. Spalding is
        // embedded in :core, so WordLookup still returns its definition.
        hansardExamples = { HansardExamplesOutcome.NotFound },
        analyzerFor = { analyzer },
        tusaalangaLookup = { TusaalangaResult.NotFound },
    )
    val result = runBlocking {
        lookup.lookupOnce(word, WordLookupPrefs(AnalyzerChoice.UQAILAUT, lenient))
    }
    print(renderDefinition(word, result))
}

// internal (not private): unit-tested directly against fabricated
// WordLookupResults in DefineRenderingTest.kt.
internal fun renderDefinition(word: String, result: WordLookupResult): String = buildString {
    appendLine("=== $word ===")
    when {
        result.dictionaryHits.isNotEmpty() -> {
            appendLine("  Definition:")
            result.dictionaryHits.forEach { hit ->
                appendLine("    [${hit.source}] ${hit.word} -- ${hit.meaning}")
            }
        }
        result.shorterWordHits.isNotEmpty() -> {
            appendLine("  No definition for this word. For a shorter form of it:")
            result.shorterWordHits.forEach { shorter ->
                appendLine("    [${shorter.hit.source}] ${shorter.hit.word} -- ${shorter.hit.meaning}")
            }
        }
        else -> appendLine("  No dictionary definition found.")
    }

    appendLine("  Decomposition:")
    when (val decomposition = result.decomposition) {
        is DecompositionOutcome.Success ->
            if (decomposition.decompositions.isEmpty()) {
                appendLine("    No decompositions found")
            } else {
                decomposition.decompositions.forEach { rows ->
                    appendLine("    " + rows.joinToString("") { "{${it.surfaceForm}:${it.morphemeId}}" })
                }
            }
        is DecompositionOutcome.Failure -> appendLine("    " + describe(decomposition.reason))
        DecompositionOutcome.Idle, DecompositionOutcome.Loading -> appendLine("    (not run)")
    }
}

private fun describe(reason: FailureReason): String = when (reason) {
    FailureReason.Timeout -> "timed out"
    is FailureReason.AnalysisError -> "analysis error: ${reason.detail ?: "unknown"}"
    FailureReason.FstNotAvailable -> "FST analyzer unavailable"
}

private fun runInteractive(analyzer: MorphologicalAnalyzer_R2L, lenient: Boolean) {
    val input = Scanner(System.`in`)
    while (true) {
        print("\nEnter Inuktut word ('q' to quit).\n> ")
        if (!input.hasNextLine()) break
        val line = input.nextLine()
        if (line.isBlank() || line.matches(Regex("^\\s*q\\s*$"))) break
        printForUser(line, decomposeOne(analyzer, line, lenient))
    }
}

private fun runPipeline(analyzer: MorphologicalAnalyzer_R2L, lenient: Boolean) {
    val input = Scanner(System.`in`)
    while (input.hasNextLine()) {
        val word = input.nextLine()
        if (word.isBlank()) continue
        val start = System.currentTimeMillis()
        val result = decomposeOne(analyzer, word, lenient)
        val elapsedMSecs = System.currentTimeMillis() - start
        println(pipelineJson(word, lenient, elapsedMSecs, result))
    }
}

private fun printForUser(word: String, result: Result<Array<Decomposition>>) {
    println("=== $word ===")
    result.fold(
        onSuccess = { decs ->
            if (decs.isEmpty()) {
                println("  No decompositions found")
            } else {
                for (dec in decs) println("  $dec")
            }
        },
        onFailure = { e ->
            if (e is TimeoutException) {
                println("  Command timed out")
            } else {
                println("  ERROR: ${e::class.simpleName}: ${e.message}")
            }
        },
    )
}

private fun jsonEscape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

private fun pipelineJson(
    word: String,
    lenient: Boolean,
    elapsedMSecs: Long,
    result: Result<Array<Decomposition>>,
): String {
    val wordJson = "\"${jsonEscape(word)}\""
    result.fold(
        onSuccess = { decs ->
            val decompsJson = decs.joinToString(",") { "\"${jsonEscape(it.toString())}\"" }
            // Parallel to "decompositions": whether each reading was only
            // found by assuming a dropped final consonant (Decomposition.lenient).
            val lenientJson = decs.joinToString(",") { it.lenient.toString() }
            return "{\"word\":$wordJson,\"lenient\":$lenient,\"elapsedMSecs\":$elapsedMSecs," +
                "\"timedOut\":false,\"exception\":null,\"decompositions\":[$decompsJson]," +
                "\"decompositionsLenient\":[$lenientJson]}"
        },
        onFailure = { e ->
            val timedOut = e is TimeoutException
            val excJson = if (timedOut) "null" else "\"${jsonEscape(e.message ?: e.toString())}\""
            return "{\"word\":$wordJson,\"lenient\":$lenient,\"elapsedMSecs\":$elapsedMSecs," +
                "\"timedOut\":$timedOut,\"exception\":$excJson,\"decompositions\":[]}"
        },
    )
}
