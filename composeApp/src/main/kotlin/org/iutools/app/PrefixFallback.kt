package org.iutools.app

/*
 * Shared by SpaldingDictionary, TusaalangaFetcher, and
 * NunavutHansardLocalIndex: when an exact lookup for a word finds nothing,
 * try progressively shorter prefixes of it before giving up entirely.
 * Inuktitut is agglutinative -- suffixes pile onto a stem -- so a specific
 * inflected form can be absent from a dictionary/corpus while its stem (a
 * prefix of the word) is present on its own. One shared MIN_LENGTH keeps
 * every source's fallback stopping at the same point, rather than each
 * picking its own cutoff independently.
 */
internal const val PREFIX_FALLBACK_MIN_LENGTH = 3

/**
 * Tries [word] itself first... no, actually only shorter prefixes -- callers
 * are expected to have already tried the exact word themselves and be
 * calling this only after that failed. Returns the longest prefix (down to
 * [PREFIX_FALLBACK_MIN_LENGTH] characters) for which [lookup] returns a
 * non-null result, paired with that result -- or null if none matched.
 */
internal fun <T> findByLongestPrefix(word: String, lookup: (String) -> T?): Pair<String, T>? {
    for (length in (word.length - 1) downTo PREFIX_FALLBACK_MIN_LENGTH) {
        val candidate = word.substring(0, length)
        val found = lookup(candidate)
        if (found != null) return candidate to found
    }
    return null
}
