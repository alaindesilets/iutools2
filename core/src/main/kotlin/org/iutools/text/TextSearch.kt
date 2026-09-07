package org.iutools.text

/*
 * Small plain-text helpers for the word-lookup experience: splitting a
 * typed query into individual words, and locating a word (or several) inside
 * a sentence so a caller can highlight it. No UI, no analyzer -- just string
 * search.
 */

/** Splits [text] on runs of whitespace, dropping empty pieces. */
fun splitIntoWords(text: String): List<String> =
    text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

/**
 * The character range of the first (case-insensitive) occurrence of [word]
 * in [sentence], or null if [word] is blank or does not occur.
 */
fun highlightRange(sentence: String, word: String): IntRange? {
    if (word.isBlank()) return null
    val start = sentence.indexOf(word, ignoreCase = true)
    return if (start < 0) null else start until (start + word.length)
}

/**
 * One [highlightRange] per candidate in [candidates] that actually occurs in
 * [sentence] (first occurrence only), in the order given; candidates that
 * don't occur are dropped.
 */
fun highlightRanges(sentence: String, candidates: List<String>): List<IntRange> =
    candidates.mapNotNull { highlightRange(sentence, it) }
