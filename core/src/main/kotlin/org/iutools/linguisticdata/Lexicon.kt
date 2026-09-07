package org.iutools.linguisticdata

import org.apache.logging.log4j.LogManager
import org.iutools.script.Syllabics
import java.util.Vector

object Lexicon {

    val consonants = arrayOf("k", "p", "q", "t")

    // Kept as \uXXXX escapes (rather than literal glyphs) to exactly match
    // the original Java source and avoid any risk of mistranscription.
    val consonantsSyl = arrayOf(
        "ᒡ", "ᔾ", "ᒃ", "ᓪ",
        "ᒻ", "ᓐ", "ᑉ", "ᖅ", "ᕐ", "ᔅ",
        "ᑦ", "ᕝ", "ᖦ", "ᖖ"
    )

    @Throws(LinguisticDataException::class)
    @JvmStatic
    fun lookForForms(term: String, syllabic: Boolean): Vector<SurfaceFormOfAffix> {
        return LinguisticData.getInstance().getSurfaceForms(term) ?: Vector()
    }

    /**
     * Returns a Vector of Morpheme (Base and Demonstrative) objects, or null.
     * @param term string in the ICI (Inuit Cultural Institute) standard
     * @param syllabic indicates whether the term is in syllabic (true) or not (false)
     */
    @Throws(LinguisticDataException::class)
    @JvmStatic
    fun lookForBase(term: String, syllabic: Boolean): Vector<Morpheme>? {
        val logger = LogManager.getLogger("Lexicon.lookForBase")
        logger.debug("term: $term")
        val actualTerm = if (syllabic) Syllabics.transcodeToRoman(term) else term
        val basesFound = LinguisticData.getInstance().getBasesForCanonicalForm(actualTerm)
        logger.debug("basesFound: ${basesFound?.size ?: "null"}")
        return basesFound
    }
}
