package org.iutools.app

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.iutools.search.findByLongestPrefix
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/*
 * See doc/spike-llm-local-iutools-mobile.md for the overall Guess Meaning
 * design: unlike Spalding, this project has no distribution rights for
 * Tusaalanga's content (Benoît Farley personally cleared rights for
 * Spalding only) -- so this is a genuine live network fetcher, queried
 * against tusaalanga.ca on every lookup, never parsed-once-and-embedded
 * like SpaldingDictionary.
 *
 * Query mechanism (confirmed by inspecting a real fetched page, not guessed):
 * https://tusaalanga.ca/glossary?l=N returns every glossary entry starting
 * with letter N as one HTML table, no per-word search endpoint and no
 * pagination within a letter. Each row looks like:
 *   <div id="romanized" class="visible">aanniajuq</div>
 *   <div id="syllabic" class="hidden">ᐋᓐᓂᐊᔪᖅ</div>
 *   <div id="term">sick; in pain (he/she is...)</div>
 * so a lookup fetches the page for the search word's first letter, then
 * scans its rows for an exact (case-insensitive) match on the romanized
 * form.
 */
data class TusaalangaEntry(val word: String, val syllabic: String, val meaning: String)

sealed interface TusaalangaResult {
    data class Found(val entry: TusaalangaEntry) : TusaalangaResult
    // The exact word wasn't found, but a shorter prefix of it was -- see
    // PrefixFallback.kt.
    data class FoundForShorterWord(val entry: TusaalangaEntry) : TusaalangaResult
    data object NotFound : TusaalangaResult
    data class FetchFailed(val message: String) : TusaalangaResult
}

object TusaalangaFetcher {
    private const val GLOSSARY_URL = "https://tusaalanga.ca/glossary"
    private const val TIMEOUT_MS = 10_000

    private val rowPattern = Regex(
        """<div id="romanized"[^>]*>(.*?)</div>\s*<div id="syllabic"[^>]*>(.*?)</div>\s*<div id="term">(.*?)</div>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    suspend fun fetch(word: String): TusaalangaResult = withContext(Dispatchers.IO) {
        val letter = firstLetterParam(word) ?: return@withContext TusaalangaResult.NotFound
        val html = try {
            downloadGlossaryPage(letter)
        } catch (e: IOException) {
            return@withContext TusaalangaResult.FetchFailed(e.message ?: e.toString())
        }
        matchEntry(parseEntries(html), word)
    }

    // internal (not private): unit-tested directly against a fixture page's
    // parsed entries, without needing a real network call -- fallback
    // candidates share the same first letter as the full word (a prefix of
    // a word starts with that word's own first letter), so they're always
    // already in the same page/entries list, no extra fetch needed. See
    // PrefixFallback.kt.
    internal fun matchEntry(entries: List<TusaalangaEntry>, word: String): TusaalangaResult {
        val match = entries.firstOrNull { it.word.equals(word, ignoreCase = true) }
        if (match != null) return TusaalangaResult.Found(match)

        val shorterMatch = findByLongestPrefix(word) { candidate ->
            entries.firstOrNull { it.word.equals(candidate, ignoreCase = true) }
        }
        return if (shorterMatch != null) TusaalangaResult.FoundForShorterWord(shorterMatch.second) else TusaalangaResult.NotFound
    }

    private fun firstLetterParam(word: String): Char? {
        val firstLetter = word.firstOrNull { it.isLetter() } ?: return null
        return if (firstLetter in 'a'..'z' || firstLetter in 'A'..'Z') firstLetter.uppercaseChar() else null
    }

    private fun downloadGlossaryPage(letter: Char): String {
        val connection = URL("$GLOSSARY_URL?l=$letter").openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} from $GLOSSARY_URL?l=$letter")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    // internal (not private): unit-tested directly in TusaalangaFetcherTest.kt
    // against a saved fixture page, without needing a real network call.
    internal fun parseEntries(html: String): List<TusaalangaEntry> =
        rowPattern.findAll(html).map { match ->
            TusaalangaEntry(
                word = decodeHtml(match.groupValues[1]),
                syllabic = decodeHtml(match.groupValues[2]),
                meaning = decodeHtml(match.groupValues[3]),
            )
        }.toList()

    private fun decodeHtml(fragment: String): String =
        Html.fromHtml(fragment.trim(), Html.FROM_HTML_MODE_LEGACY).toString().trim()
}
