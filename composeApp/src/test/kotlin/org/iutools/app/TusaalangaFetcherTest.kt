package org.iutools.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * parseEntries() below is tested against a fixture copied verbatim from a
 * real fetched page (https://tusaalanga.ca/glossary?l=A), same
 * copy-don't-retype discipline as SpaldingDictionaryTest.kt -- includes a
 * row with an HTML entity (aaggaqai -> "probably not; I don't think so.",
 * with &#039; for the apostrophe) to confirm decodeHtml() unescapes it.
 * Robolectric (not plain JUnit): android.text.Html.fromHtml needs a real
 * shadow implementation, same reason SpaldingDictionaryTest.kt needs it for
 * org.json.JSONArray.
 *
 * fetch_findsRealKnownWord is Tusaalanga's dedicated fetcher test (per
 * doc/spike-llm-local-iutools-mobile.md's "Un test par fetcher") -- a real
 * network call against tusaalanga.ca, no mocking. Unlike Spalding's
 * equivalent test, this one needs real internet access; it's normally
 * Alain's to run (see AGENTS.md's "Division of labor"), but this session's
 * sandbox had tusaalanga.ca allowlisted in its firewall specifically to run
 * it here too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
    fun fetch_findsRealKnownWord() = runBlocking {
        val result = TusaalangaFetcher.fetch("aaggiisi")

        assertTrue("expected Found, got: $result", result is TusaalangaResult.Found)
        val entry = (result as TusaalangaResult.Found).entry
        assertEquals("aaggiisi", entry.word)
        assertTrue(entry.meaning, entry.meaning.contains("August"))
    }

    @Test
    fun fetch_unknownWord_returnsNotFound() = runBlocking {
        val result = TusaalangaFetcher.fetch("nottarealinuktitutword12345")

        assertEquals(TusaalangaResult.NotFound, result)
    }
}
