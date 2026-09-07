package org.iutools.llm

import org.iutools.linguisticdata.LinguisticData
import org.iutools.linguisticdata.Morpheme
import org.iutools.morph.Decomposition

/*
 * When "Guess Meaning" builds a prompt to send to the LLM, it needs to give it
 * the word's morphological decomposition in plain natural-language text.
 *
 * This class gathers the facts about one morpheme of one decomposition, ready
 * to be rendered into that text:
 *
 *   - surfaceForm -- the letters as they actually appeared in the word
 *     being analyzed (e.g. "atua" inside "atuagaq"),
 *   - morphemeId  -- the dictionary key for that morpheme (e.g. "atuaq/1v"),
 *   - fullRecord  -- that morpheme's dictionary entry, with its meaning
 *     and grammatical info, or null when the morpheme isn't in our
 *     dictionary.
 *
 * Build the list of rows for a whole analysis with
 * Decomposition.toMorphemeRows() below.
 */
data class MorphemeRow(
    val surfaceForm: String,
    val morphemeId: String,
    val fullRecord: Morpheme?,
)

/**
 * Turn a raw [Decomposition] into [MorphemeRow]s, looking up each morpheme's
 * dictionary record. A component with no "surface:" prefix -- the FST
 * analyzer emits only the canonical "id/tag", not the matched substring --
 * uses the canonical form as its surface form.
 */
fun Decomposition.toMorphemeRows(): List<MorphemeRow> {
    val linguisticData = LinguisticData.getInstance()
    return components().map { raw ->
        val comp = raw.removeSurrounding("{", "}")
        val colon = comp.indexOf(':')
        val morphemeId = if (colon >= 0) comp.substring(colon + 1) else comp
        val surfaceForm = if (colon >= 0) comp.substring(0, colon) else morphemeId.substringBefore('/')
        MorphemeRow(surfaceForm, morphemeId, linguisticData.getMorpheme(morphemeId))
    }
}
