package org.iutools.dictionary

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Plain JVM test (no Robolectric): :core's decodeHtml() is a small
 * hand-rolled entity expander, not Android's android.text.Html.fromHtml, so
 * parseEntries() no longer needs an Android shadow implementation.
 *
 * parseEntries() is tested against a fixture copied verbatim from a real
 * fetched page (https://tusaalanga.ca/glossary?l=A) -- same
 * copy-don't-retype discipline as SpaldingDictionaryTest.kt. It includes a
 * row with an HTML entity (aaggaqai -> "probably not; I don't think so.",
 * with &#039; for the apostrophe) to confirm decodeHtml() unescapes it.
 *
 * fetch_* are Tusaalanga's dedicated fetcher tests (per
 * doc/spike-llm-local-iutools-mobile.md's "un test par fetcher") -- real
 * network calls against tusaalanga.ca, no mocking. They need real internet
 * access; per AGENTS.md's "Division of labor" they are normally Alain's to
 * run, and will fail wherever tusaalanga.ca is unreachable.
 */
class TusaalangaFetcherTest {

    private val fixtureHtml = """
        <div id="glossary-table"><table data-striping="1">
          <tbody>
                          <tr class="odd">
                          <td>
                <div data-audio="5005" class="field play field--name-field-s-qikiqtaaluk-file field--type-file field--label-hidden field__item">
        <audio  preload="none" data-fid="5005">
              <source  src="/system/files/dialect/uqqurmiut/834.mp3" type="audio/mpeg" />
          </audio>
        </div>
              </td>
                          <td><div id="romanized" class="visible">aaggaqai</div>
        <div id="syllabic" class="hidden">ᐋᒡᒐᖃᐃ</div>
        <div id="term">probably not; I don&#039;t think so.</div>
        </td>
                      </tr>
                          <tr class="even">
                          <td>
                <div data-audio="4857" class="field play field--name-field-s-qikiqtaaluk-file field--type-file field--label-hidden field__item">
        <audio  preload="none" data-fid="4857">
              <source  src="/system/files/dialect/uqqurmiut/70.mp3" type="audio/mpeg" />
          </audio>
        </div>
              </td>
                          <td><div id="romanized" class="visible">aaggiisi</div>
        <div id="syllabic" class="hidden">ᐋᒡᒌᓯ</div>
        <div id="term">August</div>
        </td>
                      </tr>
                          <tr class="odd">
                          <td class="no-audio"></td>
                          <td><div id="romanized" class="visible">aanniasiuqti</div>
        <div id="syllabic" class="hidden">ᐋᓐᓂᐊᓯᐅᖅᑎ</div>
        <div id="term">nurse</div>
        </td>
                      </tr>
              </tbody>
            </table>
        </div>
    """.trimIndent()

    @Test
    fun parseEntries_parsesWordSyllabicAndMeaning() {
        val entries = TusaalangaFetcher.parseEntries(fixtureHtml)

        assertEquals(listOf("aaggaqai", "aaggiisi", "aanniasiuqti"), entries.map { it.word })
        assertEquals("ᐋᒡᒌᓯ", entries[1].syllabic)
        assertEquals("August", entries[1].meaning)
    }

    @Test
    fun parseEntries_decodesHtmlEntities() {
        val entries = TusaalangaFetcher.parseEntries(fixtureHtml)

        assertEquals("probably not; I don't think so.", entries[0].meaning)
    }

    @Test
    fun parseEntries_noAudioRow_stillParsesCorrectly() {
        val entries = TusaalangaFetcher.parseEntries(fixtureHtml)

        assertEquals("nurse", entries[2].meaning)
    }

    @Test
    fun parseEntries_noMatches_returnsEmptyList() {
        assertEquals(emptyList<TusaalangaEntry>(), TusaalangaFetcher.parseEntries("<html><body>nothing here</body></html>"))
    }

    @Test
    fun matchEntry_exactWord_returnsFound() {
        val entries = TusaalangaFetcher.parseEntries(fixtureHtml)

        val result = TusaalangaFetcher.matchEntry(entries, "aaggiisi")

        assertTrue(result is TusaalangaResult.Found, "expected Found, got: $result")
        assertEquals("August", result.entry.meaning)
    }

    @Test
    fun matchEntry_noExactMatch_findsShorterPrefix() {
        val entries = TusaalangaFetcher.parseEntries(fixtureHtml)

        // "aaggiisiqut" isn't a real entry, but it starts with "aaggiisi".
        val result = TusaalangaFetcher.matchEntry(entries, "aaggiisiqut")

        assertTrue(result is TusaalangaResult.FoundForShorterWord, "expected FoundForShorterWord, got: $result")
        assertEquals("aaggiisi", result.entry.word)
    }

    @Test
    fun matchEntry_noMatchAtAll_returnsNotFound() {
        val entries = TusaalangaFetcher.parseEntries(fixtureHtml)

        val result = TusaalangaFetcher.matchEntry(entries, "zzznotarealprefixatall99")

        assertEquals(TusaalangaResult.NotFound, result)
    }

    @Test
    fun fetch_findsRealKnownWord() = runBlocking {
        val result = TusaalangaFetcher.fetch("aaggiisi")

        assertTrue(result is TusaalangaResult.Found, "expected Found, got: $result")
        val entry = result.entry
        assertEquals("aaggiisi", entry.word)
        assertTrue(entry.meaning.contains("August"), entry.meaning)
    }

    @Test
    fun fetch_unknownWord_returnsNotFound() = runBlocking {
        val result = TusaalangaFetcher.fetch("nottarealinuktitutword12345")

        assertEquals(TusaalangaResult.NotFound, result)
    }
}
