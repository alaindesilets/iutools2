package org.iutools.search

/*
 * Find the longest prefix of a word that produces a hit in some source --
 * a dictionary, a corpus index, a web lookup -- for when the whole word
 * itself isn't found.
 *
 * Why prefixes: Inuktitut is agglutinative, so an inflected form can be
 * missing from a dictionary or corpus while its stem -- a prefix of the
 * word -- is present on its own.
 */
const val PREFIX_FALLBACK_MIN_LENGTH = 3

/**
 * Returns the longest prefix of [word] -- from one character shorter than
 * [word] down to [PREFIX_FALLBACK_MIN_LENGTH] -- for which [lookup] returns
 * a non-null result, paired with that result, or null if none matched.
 * [word] itself is not tried: callers look up the exact word first and
 * fall back to this only when that misses.
 */
fun <T> findByLongestPrefix(word: String, lookup: (String) -> T?): Pair<String, T>? {
    for (length in (word.length - 1) downTo PREFIX_FALLBACK_MIN_LENGTH) {
        val candidate = word.substring(0, length)
        val found = lookup(candidate)
        if (found != null) return candidate to found
    }
    return null
}
