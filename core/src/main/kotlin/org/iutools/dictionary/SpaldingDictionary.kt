package org.iutools.dictionary

import org.iutools.lib.ResourceGetter
import org.iutools.search.findByLongestPrefix
import org.json.JSONArray

/*
 * The Spalding Inuktitut dictionary (inuktitutcomputing.ca), as an offline
 * exact-word lookup.
 *
 * The source is one big static HTML page, not a per-word search service, so
 * it is parsed once ahead of time (data/lexicon/parse_spalding_dictionary.py,
 * not part of any app) into data/lexicon/spalding.json -- 8373 headwords --
 * which ships on the classpath and is read here in one shot. A lookup is
 * then local, instant, and has no runtime failure mode of its own.
 *
 * One entry per headword: a single source entry often groups a primary word
 * with derived/variant forms that share one block of prose. Rather than
 * splitting that prose per headword (unreliable -- it is free-flowing text
 * with inline cross-references), every headword in a group maps to the
 * group's whole text, so a lookup on a minor variant still surfaces the
 * full entry -- which suits a "guess the meaning" use anyway.
 */
data class SpaldingEntry(val word: String, val meaning: String)

object SpaldingDictionary {

    /** Exact-match lookup. */
    fun lookup(word: String): SpaldingEntry? = entriesByWord[word]

    /**
     * The longest prefix of [word] (see [findByLongestPrefix]) that has an
     * exact entry, paired with that entry, or null if none matched. [word]
     * itself is not retried -- callers are expected to have tried [lookup]
     * first.
     */
    fun lookupLongestPrefix(word: String): Pair<String, SpaldingEntry>? =
        findByLongestPrefix(word) { candidate -> entriesByWord[candidate] }

    /** Parses the Spalding JSON (an array of `{word, meaning}` objects).
     *  Public so it can be unit-tested directly against fixture strings. */
    fun parseEntries(json: String): List<SpaldingEntry> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val entry = array.getJSONObject(i)
            SpaldingEntry(word = entry.getString("word"), meaning = entry.getString("meaning"))
        }
    }

    private const val RESOURCE_NAME = "spalding.json"

    private val entriesByWord: Map<String, SpaldingEntry> by lazy {
        val json = ResourceGetter.getResourceAsStream(RESOURCE_NAME)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Classpath resource not found: $RESOURCE_NAME")
        parseEntries(json).associateBy { it.word }
    }
}
