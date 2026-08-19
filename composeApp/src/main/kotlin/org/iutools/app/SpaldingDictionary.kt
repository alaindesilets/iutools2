package org.iutools.app

import android.content.Context
import org.json.JSONArray

/*
 * See doc/spike-llm-local-iutools-mobile.md for the overall Guess Meaning
 * design: the Spalding dictionary (inuktitutcomputing.ca) is a single
 * static HTML page, not a per-word search -- so rather than fetching it over
 * the network on every lookup, it's parsed once (tools/parse_spalding_dictionary.py,
 * not part of the app itself) into res/raw/spalding.json (8373 headwords),
 * and a lookup here is a local, instant, network-free operation with no
 * runtime failure mode of its own. Re-run that script (and review the
 * diff) if the source page is ever updated -- see its header comment for
 * the exact steps; this sandbox can't fetch the page itself.
 *
 * One row per headword: a single dictionary entry on the source page often
 * bundles a primary word with several related derived/variant forms, all
 * sharing the same block of prose -- rather than slicing that prose apart
 * per headword (unreliable, it's free-flowing scholarly text with inline
 * cross-references), every headword in a block maps to that block's full
 * text. A lookup on a minor variant surfaces the whole entry's context,
 * which is more useful for a "guess meaning" tool anyway.
 */
data class SpaldingEntry(val word: String, val meaning: String)

object SpaldingDictionary {
    private var entriesByWord: Map<String, SpaldingEntry>? = null

    /** Exact-match lookup only -- see the "Court-circuit" section of the plan doc. */
    fun lookup(context: Context, word: String): SpaldingEntry? = entries(context)[word]

    /**
     * Longest prefix of [word] (see PrefixFallback.kt) that has an exact
     * entry, paired with that entry -- or null if none matched. Callers are
     * expected to have already tried [lookup] themselves; this doesn't
     * retry the exact word.
     */
    fun lookupLongestPrefix(context: Context, word: String): Pair<String, SpaldingEntry>? {
        val map = entries(context)
        return findByLongestPrefix(word) { candidate -> map[candidate] }
    }

    private fun entries(context: Context): Map<String, SpaldingEntry> {
        return entriesByWord ?: loadEntries(context).also { entriesByWord = it }
    }

    private fun loadEntries(context: Context): Map<String, SpaldingEntry> {
        val json = context.resources.openRawResource(R.raw.spalding).bufferedReader().use { it.readText() }
        return parseEntries(json).associateBy { it.word }
    }

    // internal (not private): unit-tested directly in SpaldingDictionaryTest.kt
    // against fixture JSON, without needing an Android Context.
    internal fun parseEntries(json: String): List<SpaldingEntry> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val entry = array.getJSONObject(i)
            SpaldingEntry(word = entry.getString("word"), meaning = entry.getString("meaning"))
        }
    }
}
