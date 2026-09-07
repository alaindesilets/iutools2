package org.iutools.llm

/*
 * Parse a "guess meaning" LLM chat reply into its "Candidate meanings:" bullet
 * list and the reasoning that precedes it. The reply is expected to follow the
 * system-prompt contract that asks for that section verbatim (see the
 * Guess Meaning system prompt).
 */
const val CANDIDATE_MEANINGS_HEADER = "Candidate meanings:"

fun extractCandidateMeanings(text: String): List<String> {
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

fun extractExplanation(text: String): String {
    val headerIndex = text.lastIndexOf(CANDIDATE_MEANINGS_HEADER)
    val explanation = if (headerIndex >= 0) text.substring(0, headerIndex) else text
    return explanation.trim()
}
