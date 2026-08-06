package org.iutools.linguisticdata.constraints

import org.iutools.linguisticdata.LinguisticDataException
import org.iutools.linguisticdata.Morpheme

/*
 * Condition on some attribute of a morpheme. The condition is about one or
 * several aspects of the morpheme. A condition aspect is about an attribute and
 * its value. The attribute name is one of the attributes in the morpheme
 * classes. The value is either the name of another attribute, which means the
 * real value is the value of that other attribute and the values of the
 * two attributes must be the same, or a real value otherwise.
 */
class AttrValCond(attrValue: String) : Condition(), Conditions {

    private class Aspect(attrValue: String) {
        var attribute: String = ""
        var value: String = ""
        var eq: Boolean = true

        init {
            try {
                val sts = attrValue.split(":")
                attribute = sts[0]
                value = sts[1]
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val aspect = Aspect(attrValue)

    init {
        parameters = attrValue
    }

    /*
     * Check whether this condition is met by the morpheme. Each aspect of the
     * condition must be met.
     */
    @Throws(LinguisticDataException::class)
    override fun isMetBy(morph: Morpheme): Boolean {
        val lm = morph.getLastCombiningMorpheme()
        var res = morph.attrEqualsValue(aspect.attribute, aspect.value, aspect.eq)
        /*
         * Several roots are described in the database as inchoative roots, that
         * is, roots that are the result of adding a 'q' at the end and doubling
         * the last internal consonant (eg. ikummaq- < ikuma-). In the database,
         * this is done in the column 'combination': the last morpheme is
         * #incho#/1vv. This morpheme, as last morpheme, is NOT to be checked.
         */
        if (!res && lm != null && lm.id != "#incho#/1vv") {
            res = lm.attrEqualsValue(aspect.attribute, aspect.value, aspect.eq)
        }
        return (truth == res) // XNOR
    }

    override fun isMetByFullMorphem(morph: Morpheme): Boolean {
        val res = morph.attrEqualsValue(aspect.attribute, aspect.value, aspect.eq)
        return (truth == res) // XNOR
    }

    override fun toString(): String {
        var str = parameters
        if (!truth) str = "!$str"
        return str!!
    }

    override fun toText(lang: String): String {
        var text = if (truth) "" else (if (lang == "e") "NOT " else "PAS ")
        text += getString(aspect.attribute, lang) + "=="
        text += getString(if (aspect.attribute == "id") aspect.value else "${aspect.attribute}_${aspect.value}", lang)
        return text
    }

    companion object {
        private val strings: Map<String, Array<String>> = mapOf(
            "nature_a" to arrayOf("adjectif", "adjective"),
            "type_a" to arrayOf("racine adverbe", "adverb root"),
            "type_v" to arrayOf("racine verbale", "verb root"),
            "cas_abl" to arrayOf("ablatif", "ablative"),
            "antipassive" to arrayOf("infixe antipassif", "antipassive infix"),
            "mode_caus" to arrayOf("causal", "becausative"),
            "mode_cond" to arrayOf("conditionnel", "conditional"),
            "mode_dub" to arrayOf("dubitatif", "dubitative"),
            "number_d" to arrayOf("duel", "dual"),
            "cas_dat" to arrayOf("datif", "dative"),
            "mode_dec" to arrayOf("déclaratif", "declarative"),
            "mode_freq" to arrayOf("fréquentatif", "frequentative"),
            "mode_ger" to arrayOf("gérondif", "gerundive"),
            "id" to arrayOf("morphème", "morpheme"),
            "mode_imp" to arrayOf("impératif", "imperative"),
            "mode_int" to arrayOf("interrogatif", "interrogative"),
            "intransinfix" to arrayOf("infixe pour usage intransitif", "infix for intransitive usage"),
            "function" to arrayOf("fonction", "function"),
            "cas_loc" to arrayOf("locatif", "locative"),
            "mode" to arrayOf("mode verbal", "verb mode"),
            "nb" to arrayOf("nombre", "number"),
            "number" to arrayOf("nombre", "number"),
            "function_nn" to arrayOf("infixe nom à nom", "noun-to-noun infix"),
            "function_nv" to arrayOf("infixe nom à verbe", "noun-to-verb infix"),
            "mode_part" to arrayOf("participe", "participle"),
            "number_p" to arrayOf("pluriel", "plural"),
            "cas_sim" to arrayOf("similaris", "similaris"),
            "transinfix" to arrayOf("infixe pour usage transitif", "infix for transitive usage"),
            "cas_via" to arrayOf("vialis", "vialis"),
            "function_vn" to arrayOf("infixe verbe à nom", "verb-to-noun infix"),
            "function_vv" to arrayOf("infixe verbe à verbe", "verb-to-verb infix")
        )

        @JvmStatic
        fun getString(x: String, lang: String): String {
            val v = strings[x]
            return if (v != null) {
                if (lang == "f") v[0]
                else if (lang == "e") v[1]
                else x
            } else {
                x
            }
        }
    }
}
