package org.iutools.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.iutools.script.Script
import org.iutools.script.TransCoder
import java.io.File

/*
 * Bilingual examples of a word's use, drawn from the Nunavut Hansard
 * Inuktitut-English Parallel Corpus 3.0.1 (NRC Digital Repository, DOI
 * 10.4224/40001819, CC BY 4.0) -- see tools/build_hansard_index.py for how
 * the corpus is turned into the SQLite file this class reads, and
 * tools/README-hansard.md for why it's a *local* index rather than a live
 * fetch against inuktitutcomputing.ca (this class's earlier design):
 * broader year coverage (1999-2017 vs. ~2005), an explicit license to build
 * a derivative work from, and no dependency on a third party's undocumented
 * internal API.
 *
 * The DB (~550 MiB) deliberately is NOT a bundled app asset -- every
 * `installDebug`/Run would then push that much data to the device on every
 * redeploy, multiple times an hour during normal work. Instead it lands in
 * this app's external-files directory (a location that survives ordinary
 * reinstalls and needs no runtime storage permission) one of two ways: an
 * end user downloads it in-app (see NunavutHansardDownloader.kt, from a
 * compressed copy on this repo's GitHub Releases), or a developer pushes it
 * once per device/emulator with tools/push_hansard_db.sh. If it's missing
 * (neither has happened yet) or its schema is out of date (SCHEMA_VERSION
 * bumped since it was generated/downloaded), the feature just reports that
 * rather than crashing -- see NunavutHansardResult.
 */
data class BilingualExample(val inuktitut: String, val english: String, val date: String)

sealed interface NunavutHansardResult {
    // word is the syllabic form actually matched against the corpus (see
    // fetch() below) -- carried along so the UI can highlight it within
    // each example sentence, not just the original as-typed query.
    data class Found(val word: String, val examples: List<BilingualExample>) : NunavutHansardResult
    // The exact word wasn't found, but a shorter prefix of it was -- see
    // PrefixFallback.kt and fetch() below. word is that shorter prefix
    // (already guaranteed non-empty examples, same as Found).
    data class FoundForShorterWord(val word: String, val examples: List<BilingualExample>) : NunavutHansardResult
    data object NotFound : NunavutHansardResult
    data object IndexMissing : NunavutHansardResult
    data class IndexVersionMismatch(val found: Int, val expected: Int) : NunavutHansardResult
}

private sealed interface OpenResult {
    data class Ready(val database: SQLiteDatabase, val generatedAt: String, val pairCount: Int) : OpenResult
    data object Missing : OpenResult
    data class VersionMismatch(val found: Int) : OpenResult
}

object NunavutHansardLocalIndex {
    const val DB_FILE_NAME = "hansard.db"
    const val SCHEMA_VERSION = 1

    // Caps how many pair ids are pulled per lookup -- some words occur in
    // tens of thousands of Hansard sentences, and the UI only ever shows a
    // preview, not the full concordance.
    private const val MAX_EXAMPLES = 20

    private var cached: OpenResult? = null

    // internal (not private): needed both in production, after
    // NunavutHansardDownloader replaces the file on disk (the previously
    // cached Missing/VersionMismatch result would otherwise stick forever),
    // and in tests -- JUnit/Robolectric reuses this object's state across
    // test methods within the same class run (it's a plain Kotlin `object`,
    // not something Robolectric resets per-test the way it does Android
    // framework state), so a fixture db written for one test method would
    // otherwise be masked by the previous method's cached OpenResult. See
    // NunavutHansardLocalIndexTest.kt's @Before.
    internal fun invalidateCache() {
        cached = null
    }

    fun dbFile(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, DB_FILE_NAME) }

    /** Debug-panel text (build date, pair count) once the index is open; null otherwise. */
    fun debugStatus(context: Context): String? =
        (ensureOpen(context) as? OpenResult.Ready)?.let { "${it.pairCount} pairs, generated ${it.generatedAt}" }

    suspend fun fetch(context: Context, word: String): NunavutHansardResult = withContext(Dispatchers.IO) {
        when (val opened = ensureOpen(context)) {
            is OpenResult.Missing -> NunavutHansardResult.IndexMissing
            is OpenResult.VersionMismatch -> NunavutHansardResult.IndexVersionMismatch(opened.found, SCHEMA_VERSION)
            is OpenResult.Ready -> {
                // The corpus's Inuktitut side is syllabics-only (see the
                // corpus README) -- convert regardless of what script the
                // word was typed/analyzed in.
                val syllabicWord = TransCoder.ensureScript(Script.SYLLABIC, word)
                val examples = queryExamples(opened.database, syllabicWord, MAX_EXAMPLES)
                when {
                    examples.isNotEmpty() -> NunavutHansardResult.Found(syllabicWord, examples)
                    else -> {
                        // See PrefixFallback.kt: a specific inflected form
                        // can easily be absent from the corpus while its
                        // stem (a prefix of the word) shows up on its own
                        // elsewhere.
                        val shorterMatch = findByLongestPrefix(syllabicWord) { candidate ->
                            queryWordId(opened.database, candidate)
                        }
                        if (shorterMatch != null) {
                            val (shorterWord, _) = shorterMatch
                            NunavutHansardResult.FoundForShorterWord(
                                shorterWord,
                                queryExamples(opened.database, shorterWord, MAX_EXAMPLES),
                            )
                        } else {
                            NunavutHansardResult.NotFound
                        }
                    }
                }
            }
        }
    }

    private fun ensureOpen(context: Context): OpenResult {
        cached?.let { return it }
        val file = dbFile(context)
        if (file == null || !file.exists()) return OpenResult.Missing.also { cached = it }
        val database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        if (database.version != SCHEMA_VERSION) {
            val mismatch = OpenResult.VersionMismatch(database.version)
            database.close()
            cached = mismatch
            return mismatch
        }
        val ready = OpenResult.Ready(
            database = database,
            generatedAt = readMeta(database, "generated_at") ?: "?",
            pairCount = readMeta(database, "pair_count")?.toIntOrNull() ?: 0,
        )
        cached = ready
        return ready
    }

    // Used both by queryExamples (does this exact word exist) and the
    // findByLongestPrefix fallback in fetch() (does this shorter candidate
    // exist) -- an indexed lookup against `words.word`'s UNIQUE constraint,
    // O(log n) even with ~1.6M distinct words.
    private fun queryWordId(database: SQLiteDatabase, word: String): Long? =
        database.rawQuery(
            "SELECT id FROM words WHERE word = ?",
            arrayOf(word.lowercase()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    // internal (not private): unit-tested directly against a hand-built
    // fixture database, without needing a real device/Context.
    internal fun queryExamples(database: SQLiteDatabase, word: String, maxExamples: Int): List<BilingualExample> {
        // words/word_index are normalized (word_id, not the word text
        // itself, is what's repeated per occurrence) -- see
        // build_hansard_index.py's schema comment for why: with ~1.6M
        // distinct Inuktitut wordforms, storing each occurrence's word as
        // raw text was most of an 818 MiB build's size.
        val wordId = queryWordId(database, word) ?: return emptyList()

        val pairIds = mutableListOf<Long>()
        database.rawQuery(
            "SELECT DISTINCT pair_id FROM word_index WHERE word_id = ? LIMIT ?",
            arrayOf(wordId.toString(), maxExamples.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) pairIds.add(cursor.getLong(0))
        }
        if (pairIds.isEmpty()) return emptyList()

        val placeholders = pairIds.joinToString(",") { "?" }
        val args = pairIds.map { it.toString() }.toTypedArray()
        val examples = mutableListOf<BilingualExample>()
        database.rawQuery(
            "SELECT inuktitut, english, source_file FROM pairs WHERE id IN ($placeholders)",
            args,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                examples.add(
                    BilingualExample(
                        inuktitut = cursor.getString(0),
                        english = cursor.getString(1),
                        date = dateFromSourceFile(cursor.getString(2)),
                    ),
                )
            }
        }
        // The corpus repeats a lot of boilerplate phrasing verbatim across
        // different debate days (opening/closing formulas, standing
        // committee names, etc.) -- dedupe identical (inuktitut, english)
        // pairs so the same sentence doesn't show up twice in the app, or
        // get sent to Claude twice in guessMeaningSeedPrompt(). Applied
        // after the maxExamples cap above (a query-level LIMIT), so a very
        // repetitive word can end up with fewer than maxExamples examples
        // here -- an acceptable tradeoff over re-querying to backfill.
        return examples.distinctBy { it.inuktitut to it.english }
    }

    // internal (not private): unit-tested directly.
    internal fun dateFromSourceFile(sourceFile: String): String =
        Regex("""(\d{8})""").find(sourceFile)?.value ?: sourceFile

    private fun readMeta(database: SQLiteDatabase, key: String): String? =
        database.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
