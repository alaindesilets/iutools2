package org.iutools.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Plain JVM test (no Robolectric): :core uses the real org.json artifact
 * (not Android's stub) and loads spalding.json off the classpath -- no
 * Android Context or framework JSON classes involved.
 *
 * The lookup_* tests run against the real embedded data/lexicon/spalding.json
 * -- Spalding's dedicated "fetcher" test (doc/spike-llm-local-iutools-mobile.md's
 * "un test par fetcher"). Unlike the other fetchers' tests (which need real
 * network access -- see AGENTS.md's "Division of labor"), this one is a
 * normal JVM test: it catches the dictionary silently going empty (a bad
 * regeneration) the same way it would catch a network fetcher breaking.
 */
class SpaldingDictionaryTest {

    @Test
    fun lookup_findsRealKnownWord() {
        val entry = SpaldingDictionary.lookup("igalaaq")

        assertEquals("igalaaq", entry?.word)
        assertTrue(entry?.meaning?.contains("window") == true, entry?.meaning.orEmpty())
    }

    @Test
    fun lookup_unknownWord_returnsNull() {
        assertNull(SpaldingDictionary.lookup("not a real inuktitut word 12345"))
    }

    @Test
    fun lookupLongestPrefix_findsShorterKnownWord() {
        // "igalaaqxyz" itself isn't a real word, but it starts with the real
        // headword "igalaaq" -- lookupLongestPrefix() should find that.
        val result = SpaldingDictionary.lookupLongestPrefix("igalaaqxyz")

        assertEquals("igalaaq", result?.first)
        assertTrue(result?.second?.meaning?.contains("window") == true, result?.second?.meaning.orEmpty())
    }

    @Test
    fun lookupLongestPrefix_noPrefixMatches_returnsNull() {
        assertNull(SpaldingDictionary.lookupLongestPrefix("zzznotarealprefixatall99"))
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
