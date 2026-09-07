package org.iutools.dictionary

import org.iutools.script.Script

/*
 * One hit returned by a word-dictionary lookup: the headword, its meaning,
 * the already-localized name of the dictionary it came from, and the script
 * (Roman or syllabics) the user typed the query in.
 *
 * [title] is a ready-to-show source name, not a source code -- callers only
 * display it, nothing branches on it. [enteredScript] is captured here at
 * lookup time because a dictionary lookup can succeed on its own, with no
 * accompanying decomposition result to read the script off of.
 */
data class DictionaryLookupResult(
    val title: String,
    val word: String,
    val meaning: String,
    val enteredScript: Script,
)

/*
 * A dictionary hit found not for the searched word itself but for a shorter
 * prefix of it. Inuktitut is agglutinative, so the bare stem is often in a
 * dictionary while an inflected form of it is not (see findByLongestPrefix).
 *
 * It is kept as its own type, distinct from a plain [DictionaryLookupResult],
 * because a prefix hit is deliberately weaker than an exact-word hit: on its
 * own it does not count as "the word was found". It still carries real
 * information -- worth showing to the user, and worth handing to a
 * meaning-guessing step as context. [originalWord] is what the user actually
 * searched for; [result] is the hit on its prefix.
 */
data class ShorterWordDictionaryResult(
    val originalWord: String,
    val result: DictionaryLookupResult,
)
