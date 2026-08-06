package org.iutools.linguisticdata

import org.iutools.linguisticdata.constraints.Conditions
import org.iutools.linguisticdata.constraints.Imacond
import org.iutools.linguisticdata.constraints.ParseException
import java.io.ByteArrayInputStream
import java.util.Hashtable
import java.util.StringTokenizer

open class Suffix : Affix {

    // Note: the original Java declares its own `nb` field here, which shadows
    // (hides) Morpheme's `nb` field rather than overriding it. Nothing in the
    // codebase ever reads Morpheme.getNb() on a Suffix instance, so reusing
    // the inherited `nb` property from Morpheme directly is behaviourally
    // equivalent here and avoids duplicating state.
    var transitivity: String? = null
    var antipassive: String? = null
    var nature: String? = null
    var constraintOnTransitivity: String? = null
    var pl: String? = null
    var mobility: String? = null

    constructor()

    @Throws(LinguisticDataException::class)
    constructor(v: HashMap<String, String>) {
        morpheme = v["morpheme"]
        nb = v["nb"]
        num = nb!!.toInt()
        transitivity = v["transitivity"]
        nature = v["nature"]
        antipassive = v["antipassive"]
        type = v["type"]
        function = v["function"]
        position = v["position"]
        constraintOnTransitivity = v["condPrecTrans"]
        pl = v["plural"]
        mobility = v["mobility"]

        // Développement des diverses surfaceFormsOfAffixes associées aux 4 contextes
        // voyelle, t, k et q et à leurs actions.

        // Après Voyelle
        var form = v["V-form"]
        var act1 = v["V-action1"]
        var act2 = v["V-action2"]
        makeFormsAndActions("V", morpheme!!, form, act1!!, act2)

        // Après 't'
        form = v["t-form"]
        act1 = v["t-action1"]
        act2 = v["t-action2"]
        makeFormsAndActions("t", morpheme!!, form, act1!!, act2)

        // Après 'k'
        form = v["k-form"]
        act1 = v["k-action1"]
        act2 = v["k-action2"]
        makeFormsAndActions("k", morpheme!!, form, act1!!, act2)

        // Après 'q'
        form = v["q-form"]
        act1 = v["q-action1"]
        act2 = v["q-action2"]
        makeFormsAndActions("q", morpheme!!, form, act1!!, act2)

        makeContextualBehaviours()

        englishMeaning = v["engMean"]
        frenchMeaning = v["freMean"]
        dbName = v["dbName"]
        tableName = v["tableName"]
        var cs = v["condPrec"]
        if (cs.isNullOrEmpty()) {
            /*
             * Pour les NV, si on n'a pas spécifié une condition précédente, on
             * ajoute une condition par défaut correspondant à l'énoncé suivant:
             * "Les NV doivent suivre des radicaux nominaux", i.e. qu'ils ne
             * peuvent suivre une terminaison nominale, sauf dans des cas
             * spéciaux où ce sera indiqué spécifiquement.
             */
            if (type == "sn" && function == "nv") {
                val condStr = "!type:tn,!(type:n,number:d),!(type:n,number:p)"
                try {
                    preCondition = Imacond(ByteArrayInputStream(condStr.toByteArray())).ParseCondition() as Conditions
                } catch (e: ParseException) {
                }
            }
        } else {
            try {
                preCondition = Imacond(ByteArrayInputStream(cs.toByteArray())).ParseCondition() as Conditions
            } catch (e: ParseException) {
            }
        }
        cs = v["condOnNext"]
        if (!cs.isNullOrEmpty()) {
            try {
                nextCondition = Imacond(ByteArrayInputStream(cs.toByteArray())).ParseCondition() as Conditions
            } catch (e: ParseException) {
            }
        }

        val srcs = v["sources"]
        if (srcs != null) {
            val st2 = StringTokenizer(srcs)
            val srcArr = arrayOfNulls<String>(st2.countTokens())
            var n = 0
            while (st2.hasMoreTokens()) {
                srcArr[n++] = st2.nextToken()
            }
            @Suppress("UNCHECKED_CAST")
            sources = srcArr as Array<String>
        }
        val comb = v["combination"]
        if (!comb.isNullOrEmpty()) {
            combinedMorphemes = comb.split(Regex("[+]")).toTypedArray()
            if (combinedMorphemes!!.size < 2) {
                combinedMorphemes = null
            }
        }
        setAttrs()
    }

    override fun addToHash(key: String, obj: Any) {
        hash[key] = obj as Morpheme
    }

    override fun getSignature(): String {
        return nb + (if (type == "q") type else function)
    }

    override fun getTransitivityConstraint(): String? = constraintOnTransitivity

    // Suffixes have transitivity values:
    // t: transitive: the resulting stem is transitive
    //    if antipassive!=null, the resulting stem may be used intransitively; it is
    //    then passive or reflexive.
    // i: intransitive: the resulting stem is intransitive
    //    if nature=t, the resulting stem may also be transitive
    // n: neutral: the resulting stem is the same as the preceding stem
    override fun agreeWithTransitivity(trans: String?): Boolean {
        return if (trans == null) {
            true
        } else if (transitivity == null || transitivity == "n") {
            true
        } else if (transitivity == "t" && trans == "t") {
            true
        } else if (transitivity == "i" &&
            (trans == "i" || (trans == "t" && nature != null && nature == "t"))
        ) {
            true
        } else if (transitivity == "t" && trans == "i" && antipassive != null) {
            true
        } else {
            false
        }
    }

    // Comparaison entre cet affixe et celui donné en argument.
    fun is_the_same_affix(aff: Affix): Boolean {
        return if (morpheme == aff.morpheme && type == aff.type) {
            if (type == "q") true else function == aff.function
        } else {
            false
        }
    }

    fun needsAntipassive(apId: String): Boolean {
        if (antipassive != null) {
            val st = StringTokenizer(antipassive)
            while (st.hasMoreTokens()) {
                if (st.nextToken() == apId) return true
            }
            return false
        } else {
            return false
        }
    }

    override fun setAttrs() {
        setAttributes()
        setId()
    }

    fun setAttributes() {
        setAttributes(HashMap())
    }

    override fun setAttributes(attrs: HashMap<String, Any?>) {
        val suffAttrs = HashMap<String, Any?>()
        suffAttrs["nb"] = nb
        suffAttrs["transitivity"] = transitivity
        suffAttrs["antipassive"] = antipassive
        suffAttrs["nature"] = nature
        suffAttrs["constraintOnTransitivity"] = constraintOnTransitivity
        suffAttrs["pl"] = pl
        suffAttrs.putAll(attrs)
        super.setAttributes(suffAttrs)
    }

    override fun showData(): String {
        val sb = StringBuilder()
        sb.append("[Suffix:\n")
        sb.append("morpheme= ").append(morpheme).append("\n")
        sb.append("nb: ").append(nb).append("\n")
        sb.append("type= ").append(type).append("\n")
        sb.append("function= ").append(function).append("\n")
        sb.append("position= ").append(position).append("\n")
        sb.append("antipassive= ").append(antipassive).append("\n")
        sb.append(super.showData())
        if (preCondition != null) {
            sb.append("precedingSpecificCondition= ").append(preCondition.toString()).append("\n")
        }
        if (nextCondition != null) {
            sb.append("followingSpecificConditions= ").append(nextCondition.toString()).append("\n")
        }
        sb.append("englishMeaning= ").append(englishMeaning).append("\n")
        sb.append("tableName= ").append(tableName).append("\n")
        sb.append("sources= ")
        if (sources == null) {
            sb.append("null")
        } else {
            for (s in sources!!) {
                sb.append(s).append(" ")
            }
        }
        sb.append("]\n")
        return sb.toString()
    }

    companion object {
        @JvmField
        val hash = Hashtable<String, Morpheme>()

        @JvmField
        val functions = arrayOf("nn", "nv", "vn", "vv")
    }
}
