package org.iutools.dictionary

import org.iutools.script.Script

/*
 * One hit from a word-dictionary lookup: which dictionary it came from, the
 * headword, its meaning, and the script (Roman or syllabics) the query was
 * typed in.
 *
 * [source] is a code, not display text -- the UI turns it into a localized
 * dictionary name at render time; nothing in :core branches on it.
 * [enteredScript] is captured here at lookup time because a dictionary
 * lookup can succeed on its own, with no accompanying decomposition to read
 * the script off of.
 */
enum class DictionarySource { SPALDING, TUSAALANGA }

data class DictionaryHit(
    val source: DictionarySource,
    val word: String,
    val meaning: String,
    val enteredScript: Script,
)

/*
 * A dictionary hit found not for the searched word itself but for a shorter
 * prefix of it. Inuktitut is agglutinative, so the bare stem is often in a
 * dictionary while an inflected form of it is not (see findByLongestPrefix).
 *
 * Kept as its own type, distinct from a plain [DictionaryHit], because a
 * prefix hit is deliberately weaker than an exact-word hit: on its own it
 * does not count as "the word was found". It still carries real information
 * -- worth showing to the user, and worth handing to a meaning-guessing
 * step as context. [originalWord] is what the user actually searched for;
 * [hit] is the match on its prefix.
 */
data class ShorterWordDictionaryHit(
    val originalWord: String,
    val hit: DictionaryHit,
)
