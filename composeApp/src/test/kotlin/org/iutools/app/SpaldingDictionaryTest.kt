package org.iutools.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * Robolectric, not plain JUnit: org.json.JSONArray/JSONObject are Android
 * framework classes -- under a bare JUnit test they compile fine (the
 * stub android.jar provides the signatures) but every method throws
 * "not mocked" at runtime. Robolectric provides real shadow
 * implementations, same as every other Android-resource-touching test in
 * this project. parseEntries() is still separated from loadEntries()'s
 * Context-dependent resource I/O, though -- see the visibility note on
 * parseEntries() in SpaldingDictionary.kt.
 *
 * lookup_findsRealKnownWord below is Spalding's dedicated fetcher test (per
 * doc/spike-llm-local-iutools-mobile.md's "Un test par fetcher") -- unlike
 * the other fetchers' (which need real network access, see AGENTS.md's
 * "Division of labor"), this one runs against the real embedded
 * res/raw/spalding.json, so it's just a normal JVM test: catches the
 * dictionary silently going empty (a bad regeneration) the same way it
 * would catch a network fetcher breaking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpaldingDictionaryTest {

    @Test
    fun lookup_findsRealKnownWord() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val entry = SpaldingDictionary.lookup(context, "igalaaq")

        assertEquals("igalaaq", entry?.word)
        assertTrue(entry?.meaning.orEmpty(), entry?.meaning?.contains("window") == true)
    }

    @Test
    fun lookup_unknownWord_returnsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertNull(SpaldingDictionary.lookup(context, "not a real inuktitut word 12345"))
    }

    @Test
    fun lookupLongestPrefix_findsShorterKnownWord() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // "igalaaqxyz" itself isn't a real word, but it starts with the real
        // headword "igalaaq" -- lookupLongestPrefix() should find that.
        val result = SpaldingDictionary.lookupLongestPrefix(context, "igalaaqxyz")

        assertEquals("igalaaq", result?.first)
        assertTrue(result?.second?.meaning.orEmpty(), result?.second?.meaning?.contains("window") == true)
    }

    @Test
    fun lookupLongestPrefix_noPrefixMatches_returnsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertNull(SpaldingDictionary.lookupLongestPrefix(context, "zzznotarealprefixatall99"))
    }

    @Test
    fun parseEntries_parsesWordAndMeaning() {
        val json = """[{"word": "atuq", "meaning": "to read; to phone"}]"""

        val entries = SpaldingDictionary.parseEntries(json)

        assertEquals(1, entries.size)
        assertEquals("atuq", entries[0].word)
        assertEquals("to read; to phone", entries[0].meaning)
    }

    @Test
    fun parseEntries_multipleEntries_parsesAllOfThem() {
        val json = """[{"word": "a", "meaning": "one"}, {"word": "b", "meaning": "two"}]"""

        val entries = SpaldingDictionary.parseEntries(json)

        assertEquals(listOf("a", "b"), entries.map { it.word })
        assertEquals(listOf("one", "two"), entries.map { it.meaning })
    }

    @Test
    fun parseEntries_emptyArray_returnsEmptyList() {
        assertEquals(emptyList<SpaldingEntry>(), SpaldingDictionary.parseEntries("[]"))
    }
}
