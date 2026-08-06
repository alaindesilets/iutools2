package org.iutools.linguisticdata

import org.apache.logging.log4j.LogManager

/*
 * This class represents a surface form of a morpheme, which will constitute
 * the "dictionary" of forms for the new morphological analyzer.
 *
 * For roots, the forms will be the basic forms with the end consonant deleted
 * or replaced with all possible consonants that could happen due to the actions
 * of a following affix. For example, for the verb root malik, we will have the
 * forms malik, mali (deletion), malig (voicing), maling (nasalization). To those
 * will be added forms where the end consonant sees itself assimilated by the
 * consonant of the next affix. So for example, malik will produce malit; malig
 * will produce maliv; maling will produce malin, malim...
 *
 * For roots, the attributes endOfStem and context have null values since a root
 * cannot be preceded by a stem, obviously.
 *
 * For affixes, we have the same as for roots, but additionally, for each of those
 * forms with a different ending, we will also have variations due to some actions
 * of the affix. For example, the NN suffix arjuk will have the variants raarjuk and
 * gaarjuk.
 *
 *  surfaceForm – string that represents the surface form of the morpheme as a result
 *                of what is described above
 *  endOfStem – string that represents the end of the stems to which this form may attach:
 *                          - null: no condition on the end of the stem
 *                          - a: the stem must end with a single 'a'
 *                          - i: the stem must end with a single 'i'
 *                          - u: the stem must end with a single 'u'
 *                          - V: the stem must end with a vowel
 *                          - 1V: the stem must end with any single vowel
 *                          - 2V: the stem must end with 2 vowels
 *                          - C: the stem must end with a consonant
 *                          - lower-case consonant: the stem must end with a specific consonant
 *
 *  context – string that represents the final of the stem to which this form may attach
 *            (last character of the canonical form of the morphemes):
 *                          - V, t, k, q
 *  morphemeId – id of the morpheme in the data base for this form
 */
class SurfaceFormInContext(
    var surfaceForm: String,
    var endOfStem: String?,
    var context: Char?,
    var morphemeId: String
) {
    private var canonicalFormCache: String? = null
    private var surfaceFinalIsDifferentThanCanonical: Boolean? = null

    fun canonicalForm(): String {
        if (canonicalFormCache != null) {
            return canonicalFormCache!!
        }
        if (morphemeId != "") {
            val partsOfMorphemeId = morphemeId.split("/")
            canonicalFormCache = partsOfMorphemeId[0]
            return canonicalFormCache!!
        } else {
            return ""
        }
    }

    /*
        For both roots and affixes, for forms with a final different than the final of
        the canonical form, one needs a special attribute to indicate that there has to be
        something following, because any form with a different final means it is a final
        due to the action of a following affix.
     */
    fun finalIsDifferentThanCanonical(): Boolean {
        if (surfaceFinalIsDifferentThanCanonical == null) {
            val finalOfSurfaceForm = surfaceForm[surfaceForm.length - 1]
            val canonicalForm = canonicalForm()
            val finalOfCanonicalForm = canonicalForm[canonicalForm.length - 1]
            surfaceFinalIsDifferentThanCanonical = finalOfSurfaceForm != finalOfCanonicalForm
        }
        return surfaceFinalIsDifferentThanCanonical!!
    }

    fun isValidForStem(stem: String): Boolean {
        val lastChar = stem.substring(stem.length - 1)
        return if (endOfStem == null) {
            true
        } else if (endOfStem == "V") {
            lastChar == "i" || lastChar == "u" || lastChar == "a"
        } else if (endOfStem == "VV") {
            val penultChar = stem.substring(stem.length - 2, stem.length - 1)
            (lastChar == "i" || lastChar == "u" || lastChar == "a") &&
                (penultChar == "i" || penultChar == "u" || penultChar == "a")
        } else if (lastChar == "i" || lastChar == "u" || lastChar == "a") {
            false
        } else {
            true
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is SurfaceFormInContext) {
            if (other.surfaceForm != this.surfaceForm) {
                return false
            }
            if (other.morphemeId != this.morphemeId) {
                return false
            }
            if (other.endOfStem != this.endOfStem) {
                return false
            }
            if (other.context == null && this.context == null) {
                return true
            }
            if ((other.context == null && this.context != null) ||
                (other.context != null && this.context == null) ||
                other.context != this.context
            ) {
                return false
            }
            return true
        }
        return false
    }

    override fun hashCode(): Int = 1

    /**
     * Check whether this type of morpheme may follow the preceding morpheme.
     */
    fun validateAssociativityWithPrecedingMorpheme(precedingMorpheme: SurfaceFormInContext): Boolean {
        return false
    }

    fun validateWithStem(precedingMorpheme: SurfaceFormInContext): Boolean {
        val precCanonical = precedingMorpheme.canonicalForm()
        val finalOfPrecedingMorpheme = precCanonical[precCanonical.length - 1]
        if (this.context == 'V') {
            if (finalOfPrecedingMorpheme != 'i' && finalOfPrecedingMorpheme != 'u' && finalOfPrecedingMorpheme != 'a') {
                return false
            }
        } else if (this.context == 'C') {
            if (finalOfPrecedingMorpheme == 'i' || finalOfPrecedingMorpheme == 'u' || finalOfPrecedingMorpheme == 'a') {
                return false
            }
        } else if (finalOfPrecedingMorpheme != this.context) {
            return false
        }
        val stem = precedingMorpheme.surfaceForm
        val lastCharOfStem = stem.substring(stem.length - 1)
        return if (endOfStem == "V") {
            lastCharOfStem == "i" || lastCharOfStem == "u" || lastCharOfStem == "a"
        } else if (endOfStem == "C") {
            !(lastCharOfStem == "i" || lastCharOfStem == "u" || lastCharOfStem == "a")
        } else { // "VV"
            if (stem.length < 2) {
                true
            } else {
                val penultCharOfStem = stem.substring(stem.length - 2, stem.length - 1)
                (lastCharOfStem == "i" || lastCharOfStem == "u" || lastCharOfStem == "a") &&
                    (penultCharOfStem == "i" || penultCharOfStem == "u" || penultCharOfStem == "a")
            }
        }
    }

    /**
     * Check whether the constraining conditions imposed by this morpheme and the preceding morpheme are met.
     */
    @Throws(LinguisticDataException::class)
    fun validateConstraints(precedingMorpheme: SurfaceFormInContext): Boolean {
        val logger = LogManager.getLogger("SurfaceFormInContext.validateConstraints")
        logger.debug("precedingMorpheme.morphemeId: ${precedingMorpheme.morphemeId}")
        val prec = LinguisticData.getInstance().getMorpheme(precedingMorpheme.morphemeId)
        val cur = LinguisticData.getInstance().getMorpheme(this.morphemeId)
        var condsOnPrecedingMorpheme: org.iutools.linguisticdata.constraints.Conditions? = null
        var condsOnCurrentMorpheme: org.iutools.linguisticdata.constraints.Conditions? = null
        try {
            condsOnPrecedingMorpheme = cur!!.getPrecCond()
            condsOnCurrentMorpheme = prec!!.getNextCond()
        } catch (e: NullPointerException) {
            System.err.println("NullPointerException for $morphemeId")
        }
        var res = prec!!.meetsConditions(condsOnPrecedingMorpheme)
        if (res) {
            res = cur!!.meetsConditions(condsOnCurrentMorpheme)
        }
        return res
    }

    fun validateFinal(): Boolean {
        val logger = LogManager.getLogger("SurfaceFormInContext.validateFinal")
        val morphemeIdThisParts = morphemeId.split("/")
        val idThis = morphemeIdThisParts[1]
        val res: Boolean
        if (idThis.matches(Regex("^\\d+q$"))) {
            res = true
        } else if (idThis.matches(Regex("^\\d+n$")) || idThis.matches(Regex("^tn.+"))) {
            res = true
        } else {
            res = false
        }
        return res
    }

    override fun toString(): String {
        return "SurfaceFormInContext[$surfaceForm; $endOfStem; $context; $morphemeId]"
    }

    fun isZeroLength(): Boolean = false
}
