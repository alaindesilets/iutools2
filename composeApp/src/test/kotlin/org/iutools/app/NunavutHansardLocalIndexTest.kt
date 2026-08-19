package org.iutools.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.iutools.script.Script
import org.iutools.script.TransCoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/*
 * queryExamples()/dateFromSourceFile() are tested against a hand-built
 * fixture database matching build_hansard_index.py's schema (pairs/words/
 * word_index/meta) -- a tiny stand-in for the real ~550 MiB corpus db, same
 * "fixture instead of the real huge asset" approach as
 * TusaalangaFetcherTest.kt's saved-page fixture. Robolectric (not plain
 * JUnit): android.database.sqlite.SQLiteDatabase needs a real shadow
 * implementation, same reason SpaldingDictionaryTest.kt needs it for
 * org.json.
 *
 * fetch()'s Missing/VersionMismatch/Found/NotFound branches are tested
 * through a real Context, writing (or deliberately not writing) a fixture
 * db at exactly the path NunavutHansardLocalIndex.dbFile() expects -- the
 * point of this class existing at all is to make that path swappable for a
 * one-time `adb push`, so exercising it for real (not a mock) is the point.
 *
 * The fixture's Inuktitut word is derived by running the real word "iglu"
 * through TransCoder itself, not hand-transliterated -- the corpus's
 * Inuktitut side is syllabics-only (see build_hansard_index.py), and
 * guessing at syllabics by hand risks a test that's wrong in a way nobody
 * would notice, same "don't hardcode a guess" reasoning as
 * DisplayScriptSwitchUiTest.kt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NunavutHansardLocalIndexTest {

    private val syllabicIglu = TransCoder.ensureScript(Script.SYLLABIC, "iglu")
    private val syllabicAmma = TransCoder.ensureScript(Script.SYLLABIC, "amma")

    @Before
    fun clearAnyPreviousFixture() {
        NunavutHansardLocalIndex.invalidateCache()
        val context = ApplicationProvider.getApplicationContext<Context>()
        NunavutHansardLocalIndex.dbFile(context)?.delete()
    }

    private fun buildFixtureDb(path: String, schemaVersion: Int = NunavutHansardLocalIndex.SCHEMA_VERSION) {
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        db.execSQL("PRAGMA user_version = $schemaVersion")
        db.execSQL(
            "CREATE TABLE pairs (id INTEGER PRIMARY KEY, source_file TEXT NOT NULL, " +
                "source_line INTEGER NOT NULL, inuktitut TEXT NOT NULL, english TEXT NOT NULL)",
        )
        db.execSQL("CREATE TABLE words (id INTEGER PRIMARY KEY, word TEXT NOT NULL UNIQUE)")
        db.execSQL(
            "CREATE TABLE word_index (word_id INTEGER NOT NULL, pair_id INTEGER NOT NULL, " +
                "PRIMARY KEY (word_id, pair_id)) WITHOUT ROWID",
        )
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")

        db.execSQL(
            "INSERT INTO pairs VALUES (1, 'Hansard_19990401', 65, ?, 'a new building')",
            arrayOf("$syllabicIglu $syllabicAmma"),
        )
        db.execSQL(
            "INSERT INTO pairs VALUES (2, 'Hansard_20050318', 12, ?, 'and the building')",
            arrayOf("$syllabicAmma $syllabicIglu"),
        )
        // Same text as pair 1, on a different day -- the corpus repeats
        // boilerplate verbatim like this often; queryExamples() is expected
        // to dedupe it back out (see queryExamples_duplicatePairs_areDeduped).
        db.execSQL(
            "INSERT INTO pairs VALUES (3, 'Hansard_20080604', 3, ?, 'a new building')",
            arrayOf("$syllabicIglu $syllabicAmma"),
        )
        db.execSQL("INSERT INTO words VALUES (1, ?)", arrayOf(syllabicIglu))
        db.execSQL("INSERT INTO words VALUES (2, ?)", arrayOf(syllabicAmma))
        db.execSQL("INSERT INTO word_index VALUES (1, 1)")
        db.execSQL("INSERT INTO word_index VALUES (2, 1)")
        db.execSQL("INSERT INTO word_index VALUES (1, 2)")
        db.execSQL("INSERT INTO word_index VALUES (2, 2)")
        db.execSQL("INSERT INTO word_index VALUES (1, 3)")
        db.execSQL("INSERT INTO word_index VALUES (2, 3)")
        db.execSQL("INSERT INTO meta VALUES ('schema_version', '$schemaVersion')")
        db.execSQL("INSERT INTO meta VALUES ('corpus_version', '3.0.1')")
        db.execSQL("INSERT INTO meta VALUES ('pair_count', '3')")
        db.execSQL("INSERT INTO meta VALUES ('generated_at', '2026-01-01T00:00:00+00:00')")
        db.close()
    }

    private fun tempDbPath(name: String): String =
        File.createTempFile(name, ".db").apply { deleteOnExit() }.path

    @Test
    fun queryExamples_wordWithTwoOccurrences_returnsBothPairs() {
        val path = tempDbPath("query1")
        buildFixtureDb(path)
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)

        val examples = NunavutHansardLocalIndex.queryExamples(db, syllabicIglu, maxExamples = 20)

        assertEquals(2, examples.size)
        assertTrue(examples.any { it.english == "a new building" })
        assertTrue(examples.any { it.english == "and the building" })
    }

    @Test
    fun queryExamples_duplicatePairs_areDeduped() {
        val path = tempDbPath("query1b")
        buildFixtureDb(path)
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)

        // The fixture has 3 pairs matching "iglu" (see buildFixtureDb), but
        // pairs 1 and 3 share identical (inuktitut, english) text.
        val examples = NunavutHansardLocalIndex.queryExamples(db, syllabicIglu, maxExamples = 20)

        assertEquals(2, examples.size)
    }

    @Test
    fun queryExamples_unknownWord_returnsEmptyList() {
        val path = tempDbPath("query2")
        buildFixtureDb(path)
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)

        assertEquals(
            emptyList<BilingualExample>(),
            NunavutHansardLocalIndex.queryExamples(db, "notarealword", maxExamples = 20),
        )
    }

    @Test
    fun queryExamples_respectsMaxExamples() {
        val path = tempDbPath("query3")
        buildFixtureDb(path)
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)

        val examples = NunavutHansardLocalIndex.queryExamples(db, syllabicIglu, maxExamples = 1)

        assertEquals(1, examples.size)
    }

    @Test
    fun dateFromSourceFile_extractsEightDigitDate() {
        assertEquals("19990401", NunavutHansardLocalIndex.dateFromSourceFile("Hansard_19990401"))
    }

    @Test
    fun dateFromSourceFile_noDateFound_returnsOriginalString() {
        assertEquals("mystery", NunavutHansardLocalIndex.dateFromSourceFile("mystery"))
    }

    @Test
    fun fetch_noDbPushed_returnsIndexMissing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val result = NunavutHansardLocalIndex.fetch(context, "iglu")

        assertEquals(NunavutHansardResult.IndexMissing, result)
    }

    @Test
    fun fetch_wrongSchemaVersion_returnsIndexVersionMismatch() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = requireNotNull(NunavutHansardLocalIndex.dbFile(context))
        dbFile.parentFile?.mkdirs()
        buildFixtureDb(dbFile.path, schemaVersion = 999)

        val result = NunavutHansardLocalIndex.fetch(context, "iglu")

        assertEquals(NunavutHansardResult.IndexVersionMismatch(999, NunavutHansardLocalIndex.SCHEMA_VERSION), result)
    }

    @Test
    fun fetch_romanQuery_matchesSyllabicCorpusEntry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = requireNotNull(NunavutHansardLocalIndex.dbFile(context))
        dbFile.parentFile?.mkdirs()
        buildFixtureDb(dbFile.path)

        // "iglu" is typed in Roman script; the fixture only has the
        // syllabic form -- this only finds it if fetch() converts scripts
        // before querying, which is the behavior under test.
        val result = NunavutHansardLocalIndex.fetch(context, "iglu")

        assertTrue("expected Found, got: $result", result is NunavutHansardResult.Found)
        assertEquals(2, (result as NunavutHansardResult.Found).examples.size)
    }

    @Test
    fun fetch_wordAbsentFromCorpus_returnsNotFound() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = requireNotNull(NunavutHansardLocalIndex.dbFile(context))
        dbFile.parentFile?.mkdirs()
        buildFixtureDb(dbFile.path)

        val result = NunavutHansardLocalIndex.fetch(context, "qanuippit")

        assertEquals(NunavutHansardResult.NotFound, result)
    }

    @Test
    fun fetch_exactWordAbsentButPrefixPresent_returnsFoundForShorterWord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = requireNotNull(NunavutHansardLocalIndex.dbFile(context))
        dbFile.parentFile?.mkdirs()
        buildFixtureDb(dbFile.path)

        // A synthetic word (not itself in the fixture) that starts with the
        // real fixture word "iglu" -- fetch() should fall back to it.
        val result = NunavutHansardLocalIndex.fetch(context, syllabicIglu + syllabicAmma)

        assertTrue("expected FoundForShorterWord, got: $result", result is NunavutHansardResult.FoundForShorterWord)
        val shorterResult = result as NunavutHansardResult.FoundForShorterWord
        assertEquals(syllabicIglu, shorterResult.word)
        assertEquals(2, shorterResult.examples.size)
    }
}
