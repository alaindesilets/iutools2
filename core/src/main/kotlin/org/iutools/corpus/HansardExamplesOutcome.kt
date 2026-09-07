package org.iutools.corpus

/*
 * The result of asking for bilingual examples of one word's use in the
 * Nunavut Hansard corpus.
 *
 * It is a small set of cases the caller renders differently: examples were
 * found for the word, examples were found only for a shorter prefix of it,
 * the word simply isn't in the corpus, or the corpus index itself is
 * unavailable (never installed, or built against an older schema).
 *
 * `word` on the two "found" cases is the form actually matched against the
 * corpus (its Inuktitut side is syllabics-only), carried along so the UI
 * can highlight it inside each example sentence rather than the original
 * as-typed query.
 */
sealed interface HansardExamplesOutcome {
    data class Found(val word: String, val examples: List<BilingualExample>) : HansardExamplesOutcome
    data class FoundForShorterWord(val word: String, val examples: List<BilingualExample>) : HansardExamplesOutcome
    data object NotFound : HansardExamplesOutcome
    data object IndexMissing : HansardExamplesOutcome
    data class IndexVersionMismatch(val found: Int, val expected: Int) : HansardExamplesOutcome
}

/*
 * Where a word-lookup gets its Hansard examples from. The concrete source
 * is platform-specific and injected: the Android app reads a local SQLite
 * index of the corpus; a JVM CLI would read it a different way, or supply a
 * source that always returns NotFound when no corpus is present.
 */
fun interface HansardExampleSource {
    suspend fun examplesFor(word: String): HansardExamplesOutcome
}
