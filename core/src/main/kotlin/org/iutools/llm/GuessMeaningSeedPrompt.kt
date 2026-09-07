package org.iutools.llm

import org.iutools.corpus.BilingualExample
import org.iutools.dictionary.ShorterWordDictionaryResult
import org.iutools.script.Script
import org.iutools.script.TransCoder

/**
 * Builds the seed message sent to the LLM for "Guess Meaning": it opens
 * with the question, then, for each kind of information actually available
 * for this specific word, a plain-language sentence saying what it is and
 * how (or whether) to use it, followed by that data -- the morphological
 * decomposition(s), a dictionary definition found for a shorter/related
 * word (a [ShorterWordDictionaryResult] -- never an exact-word definition,
 * since Guess Meaning isn't offered at all when one of those exists),
 * and/or bilingual sentence pairs. The idea (per Alain): rather than one
 * system prompt trying to describe every case a seed *might* contain, each
 * seed describes exactly what *this* one contains and why, conversationally
 * -- the system prompt then only carries the few rules true of every
 * attempt (output format, response language, brief-then-detailed). Both the
 * seed and the system prompt follow the app's UI language, so the LLM's
 * replies do too.
 *
 * Every Inuktitut word included here (the word itself, the decomposition
 * morphemes, the shorter-word dictionary headword) is always written in
 * syllabics, regardless of how it was typed or displayed -- Roman-script
 * Inuktitut looked enough like gibberish to a small local model that it
 * fell back to its default language instead of English. Hansard examples
 * don't need converting, their Inuktitut side is already syllabics.
 *
 * [hansardExamplesAreExactMatch] chooses which of
 * [GuessMeaningSeedLabels.hansardExamplesExactIntroTemplate] /
 * [GuessMeaningSeedLabels.hansardExamplesShorterWordIntroTemplate]
 * introduces [hansardExamples]; ignored when [hansardExamples] is empty.
 *
 * Takes the four data lists directly, not a screen/analyzer state object --
 * it only reads these fields, and this keeps it a plain, testable function.
 * [hansardExamples] is expected to be already deduplicated and capped to
 * however many should be sent; this function doesn't cap it.
 */
fun guessMeaningSeedPrompt(
    word: String,
    decompositions: List<List<MorphemeRow>>,
    hansardExamples: List<BilingualExample>,
    hansardExamplesAreExactMatch: Boolean,
    shorterWordDictionaryResults: List<ShorterWordDictionaryResult>,
    preferFrench: Boolean,
    labels: GuessMeaningSeedLabels,
): String {
    val syllabicWord = TransCoder.ensureScript(Script.SYLLABIC, word)
    val question = fillPlaceholder(labels.questionTemplate, syllabicWord)

    // Omitted entirely (not just an empty section) when there are no
    // decompositions -- e.g. the analyzer found none for this word.
    val decompositionSection = if (decompositions.isEmpty()) {
        ""
    } else {
        val intro = if (decompositions.size > 1) labels.decompositionMultipleIntro else labels.decompositionSingleIntro
        val decompositionsText = decompositions.mapIndexed { index, rows ->
            val header = if (decompositions.size > 1) {
                fillPlaceholder(labels.decompositionNumberTemplate, index + 1) + "\n"
            } else {
                ""
            }
            val morphemes = rows.joinToString("\n") { row ->
                val meaning = row.fullRecord?.preferredMeaning(preferFrench) ?: labels.unknownMorpheme
                val surfaceForm = TransCoder.ensureScript(Script.SYLLABIC, row.surfaceForm)
                "- $surfaceForm (${row.morphemeId}): $meaning"
            }
            header + morphemes
        }.joinToString("\n\n")
        "\n\n$intro\n\n$decompositionsText"
    }

    // Same omit-when-empty treatment as decompositionSection. Comes before
    // hansardSection, matching Alain's own example of the desired wording.
    val shorterWordSection = if (shorterWordDictionaryResults.isEmpty()) {
        ""
    } else {
        val intro = fillPlaceholder(labels.shorterWordDictionaryIntroTemplate, syllabicWord)
        val definitionsText = shorterWordDictionaryResults.joinToString("\n") {
            val headword = TransCoder.ensureScript(Script.SYLLABIC, it.result.word)
            "- ${it.result.title}, \"$headword\": ${it.result.meaning}"
        }
        "\n\n$intro\n\n$definitionsText"
    }

    // Omitted entirely (not just an empty section) when there are no
    // examples -- e.g. the Hansard search hasn't finished yet, or found
    // nothing, by the time this is called.
    val hansardSection = if (hansardExamples.isEmpty()) {
        ""
    } else {
        val introTemplate = if (hansardExamplesAreExactMatch) labels.hansardExamplesExactIntroTemplate else labels.hansardExamplesShorterWordIntroTemplate
        val intro = fillPlaceholder(introTemplate, syllabicWord)
        val examplesText = hansardExamples.joinToString("\n") { "- ${it.inuktitut} — ${it.english}" }
        "\n\n$intro\n\n$examplesText"
    }

    return question + decompositionSection + shorterWordSection + hansardSection
}
