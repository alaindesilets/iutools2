package org.iutools.morphemedict

import org.iutools.linguisticdata.LinguisticData
import org.iutools.linguisticdata.Morpheme
import org.iutools.linguisticdata.MorphemeHumanReadableDescr
import org.iutools.script.Script
import org.iutools.script.TransCoder

/*
 * The Morpheme Dictionary lookup: given the start of a morpheme's canonical
 * form (and, optionally, words to match in its grammar description or its
 * meaning), return the matching morphemes with a human-readable description
 * of each.
 *
 * Ported from org.iutools.morphemedict.MorphemeDictionary in the original
 * iutools Java project, but only the "which morphemes match" half. The
 * original also attaches real example words pulled from a compiled corpus
 * (Elasticsearch) and ranks/root-balances them; that corpus layer is out of
 * scope for this port (see AGENTS.md) and example words are a later
 * increment. So this returns the morpheme descriptions only.
 *
 * The original matched canonical form / grammar / meaning as exact
 * Elasticsearch field queries against a morpheme index. With no index here we
 * scan every morpheme in the linguistic database once (cached) and match:
 *   - canonicalForm: case-insensitive prefix (the original's "form*" wildcard)
 *   - grammar, meaning: case-insensitive substring -- more useful for a search
 *     box than the original's exact match, and cheap over a few thousand rows.
 *
 * When preferFrench is set, a result's meaning is the morpheme's French gloss,
 * falling back to English when there is no French one -- the same policy as
 * Morpheme.preferredMeaning / the word-lookup screen. ~24% of the Spalding
 * roots have no French gloss in the CSV data, so a French UI shows English for
 * those; that is a data gap, not this code's doing.
 */
class MorphemeDictionary {

    /**
     * @param canonicalFormPrefix start of the canonical form; syllabics are
     *   accepted and transcoded to Roman, matching how the analyzer normalizes
     *   input. Blank matches every morpheme (subject to the other filters).
     * @param grammarSubstring   if non-blank, keep only morphemes whose grammar
     *   description contains it (case-insensitive).
     * @param meaningSubstring   if non-blank, keep only morphemes whose English
     *   or French meaning contains it (case-insensitive).
     * @param includeComposite   composite morphemes (e.g. "ilinniaqtit/1v") are
     *   dropped by default, as the original web service does.
     * @param preferFrench       when true, each result's [MorphemeHumanReadableDescr.meaning]
     *   carries the French gloss instead of the English one.
     */
    fun search(
        canonicalFormPrefix: String,
        grammarSubstring: String? = null,
        meaningSubstring: String? = null,
        includeComposite: Boolean = false,
        preferFrench: Boolean = false,
    ): List<MorphemeHumanReadableDescr> {
        val prefix = TransCoder.ensureScript(Script.ROMAN, canonicalFormPrefix.trim())
            .lowercase()
        val grammarNeedle = grammarSubstring?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val meaningNeedle = meaningSubstring?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        val results = ArrayList<MorphemeHumanReadableDescr>()
        for (row in allMorphemes) {
            if (!includeComposite && row.isComposite) continue
            if (prefix.isNotEmpty() && !row.descr.canonicalForm.lowercase().startsWith(prefix)) continue
            if (grammarNeedle != null &&
                row.descr.grammar?.lowercase()?.contains(grammarNeedle) != true
            ) continue
            if (meaningNeedle != null &&
                !row.englishMeaning.orEmpty().lowercase().contains(meaningNeedle) &&
                !row.frenchMeaning.orEmpty().lowercase().contains(meaningNeedle)
            ) continue

            results.add(descrFor(row, preferFrench))
        }
        results.sortWith(compareBy({ it.canonicalForm.lowercase() }, { it.id }))
        return results
    }

    private fun descrFor(row: MorphemeRow, preferFrench: Boolean): MorphemeHumanReadableDescr =
        MorphemeHumanReadableDescr(
            row.id,
            row.morpheme?.preferredMeaning(preferFrench) ?: row.englishMeaning,
        )

    // One scanned morpheme, with its description parsed once. Holds the
    // Morpheme so meaning selection can defer to Morpheme.preferredMeaning
    // (French with English fallback, blank-aware) rather than reimplement it.
    private class MorphemeRow(val id: String, val morpheme: Morpheme?) {
        val isComposite: Boolean = morpheme?.isComposite() ?: false
        val englishMeaning: String? = morpheme?.englishMeaning
        val frenchMeaning: String? = morpheme?.frenchMeaning
        val descr: MorphemeHumanReadableDescr = MorphemeHumanReadableDescr(id, englishMeaning)
    }

    companion object {
        // Every morpheme in the linguistic database, description parsed once.
        // Built on first use and reused (thread-safe via `by lazy`); the CSV
        // data it is derived from never changes at runtime.
        private val allMorphemes: List<MorphemeRow> by lazy {
            val data = LinguisticData.getInstance()
            data.allMorphemeIDs().map { id -> MorphemeRow(id, data.getMorpheme(id)) }
        }
    }
}
