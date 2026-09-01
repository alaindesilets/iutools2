package org.iutools.app

/*
 * Pulls the "Candidate meanings:" bullet list, and the reasoning text before
 * it, back out of a chat reply's raw text -- Claude's replies follow this
 * system prompt contract (see GuessMeaningEngine.kt), asking for this
 * section verbatim, so this parser can extract it. (The now-disabled
 * on-device backend shared the same contract too -- see
 * composeApp/disabled-features/local-llm/README.md.)
 * extractCandidateMeanings() feeds the "AI best guesses" section;
 * extractExplanation() feeds the "Explain" button next to it, added per
 * Alain's request so a normal user only sees the model's reasoning if they
 * ask for it, rather than it being part of the main answer.
 *
 * Internal (not private): unit-tested directly, no Compose/Android
 * dependency -- pure string parsing.
 */
internal const val CANDIDATE_MEANINGS_HEADER = "Candidate meanings:"

internal fun extractCandidateMeanings(text: String): List<String> {
    // lastIndexOf, not indexOf: the header is supposed to appear once, right
    // at the end (see the system prompt), but this stays robust if the
    // model's own reasoning text happens to mention the phrase earlier.
    val headerIndex = text.lastIndexOf(CANDIDATE_MEANINGS_HEADER)
    if (headerIndex < 0) return emptyList()

    val afterHeader = text.substring(headerIndex + CANDIDATE_MEANINGS_HEADER.length)
    return afterHeader.lineSequence()
        .map { it.trim() }
        .drop(1) // the (empty, until generation catches up) remainder of the header line itself
        .takeWhile { it.isEmpty() || it.startsWith("-") || it.startsWith("•") }
        .filter { it.isNotEmpty() }
        .map { it.removePrefix("-").removePrefix("•").trim() }
        .toList()
}

internal fun extractExplanation(text: String): String {
    val headerIndex = text.lastIndexOf(CANDIDATE_MEANINGS_HEADER)
    val explanation = if (headerIndex >= 0) text.substring(0, headerIndex) else text
    return explanation.trim()
}
