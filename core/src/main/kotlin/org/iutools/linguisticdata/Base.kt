package org.iutools.linguisticdata

import org.iutools.linguisticdata.constraints.Conditions
import org.iutools.linguisticdata.constraints.Imacond
import org.iutools.linguisticdata.constraints.ParseException
import java.io.ByteArrayInputStream
import java.util.Hashtable
import java.util.StringTokenizer
import java.util.Vector

open class Base : Morpheme {

    @JvmField var variant: String? = null
    // originalMorpheme:
    //  If a morpheme has various spellings, these are contained in
    //  the field 'variante' of the database table. For each variant,
    //  another object will be created with that variant for its 'morpheme',
    //  'originalMorpheme' will be set to the original (this) morpheme.
    @JvmField var originalMorpheme: String? = null
    @JvmField var nature: String? = null
    var known: Boolean = true
    var transitivity: String? = null // for verbs only
    var transinfix: String? = null // for verbs only
    var intransinfix: String? = null // for verbs only
    @JvmField var antipassive: String? = null // for verbs only
    var number: String? = null
    var subtype: String? = null
    var source: String? = null
    @JvmField var compositionRoot: String? = null

    private var idsOfCompositesWithThisRoot: Vector<String>? = null

    @Throws(LinguisticDataException::class)
    constructor(v: HashMap<String, String>) {
        makeRoot(v)
    }

    constructor()

    @Throws(LinguisticDataException::class)
    private fun makeRoot(v: HashMap<String, String>) {
        getAndSetBaseAttributes(v)
        variant = v["variant"]
        originalMorpheme = v["originalMorpheme"]
        nb = v["nb"]
        if (nb.isNullOrEmpty()) {
            nb = "1"
        }
        num = nb!!.toInt()
        type = v["type"]
        number = v["number"]
        if (number.isNullOrEmpty()) {
            number = "s"
        }
        antipassive = v["antipassive"]
        transinfix = v["transSuffix"]
        intransinfix = v["intransSuffix"]
        transitivity = v["transitivity"]
        cf = v["cf"]
        if (!cf.isNullOrEmpty()) cfs = cf!!.split(" ").toTypedArray()
        dialect = v["dialect"]
        nature = v["nature"]
        source = v["source"]
        if (!source.isNullOrEmpty()) {
            sources = source!!.split(" ").toTypedArray()
        }
        val cs = v["condOnNext"]
        if (!cs.isNullOrEmpty()) {
            try {
                nextCondition = Imacond(ByteArrayInputStream(cs.toByteArray())).ParseCondition() as Conditions
            } catch (e: ParseException) {
            }
        }

        // Racine de composition pour les racines duelles et plurielles
        compositionRoot = v["compositionRoot"]
        subtype = v["subtype"]

        val comb = v["combination"]
        if (!comb.isNullOrEmpty()) {
            combinedMorphemes = comb.split(Regex("[+]")).toTypedArray()
            if (combinedMorphemes!!.size < 2) {
                combinedMorphemes = null
            }
        }
        setAttrs()
    }

    open fun addToHash(key: String, obj: Any) {
        hash[key] = obj as Morpheme
    }

    override fun getSignature(): String {
        return if (originalMorpheme != null) {
            Morpheme.Id(originalMorpheme!!).signature!!
        } else {
            nb + type
        }
    }

    override fun getOriginalMorpheme(): String {
        return if (originalMorpheme != null) {
            Morpheme.Id(originalMorpheme!!).morphemeName!!
        } else {
            morpheme!!
        }
    }

    fun isGiVerb(): Boolean {
        return type == "v" && transinfix != null && transinfix!!.startsWith("gi")
    }

    fun isSingular(): Boolean = number != null && number == "s"

    fun isDual(): Boolean = number != null && number == "d"

    fun isPlural(): Boolean = number != null && number == "p"

    fun isTransitiveVerb(): Boolean = type == "v" && transitivity != null && transitivity == "t"

    fun isIntransitiveVerb(): Boolean = type == "v" && transitivity != null && transitivity == "i"

    fun getAntipassive(): String? = antipassive

    fun getVariant(): String? = variant

    /*
     * Roots' transitivity is defined as follows:
     * t: transitive
     *   If the value of 'antipassive' is not null, the root may be used
     *   intransitively (reflexive or passive transitiveMeaning)
     * i: intransitive
     *   If the value of 'transinfix' is "nil", the root may also be transitive
     */
    override fun agreeWithTransitivity(trans: String?): Boolean {
        return if (trans == null) {
            true
        } else if (transitivity == null) {
            false
        } else if (transitivity == "t" && trans == "t") {
            true
        } else if (transitivity == "i" &&
            (trans == "i" || (trans == "t" && transinfix != null && transinfix == "nil"))
        ) {
            true
        } else if (transitivity == "t" && antipassive != null && trans == "i") {
            true
        } else {
            false
        }
    }

    fun getVariants(): Array<String> = variant!!.split(" ").toTypedArray()

    fun getCompositionRoot(): String? = compositionRoot

    fun getAndSetBaseAttributes(v: HashMap<String, String>) {
        morpheme = v["morpheme"]
        englishMeaning = v["engMean"]
        frenchMeaning = v["freMean"]
        dbName = v["dbName"]
        tableName = v["tableName"]
    }

    fun setCombiningParts(comb: String) {
        combinedMorphemes = comb.split(Regex("[+]")).toTypedArray()
        if (combinedMorphemes!!.size < 2) {
            combinedMorphemes = null
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

    /*
     * The following 4 "meaning" accessors originally parsed a rich markup
     * language embedded in the CSV's englishMeaning/frenchMeaning fields
     * (transitive/passive/reflexive/result forms of verb meanings, for the
     * dictionary display feature). That parsing logic is not on the
     * decomposeWord() call path (verified: unused by morph/r2l), so it was
     * dropped here in favour of returning the raw meaning fields.
     */
    fun getTransitiveMeaning(lang: String): String {
        return if (type == "v" && transitivity == "t") (if (lang == "en") englishMeaning else frenchMeaning) ?: "" else ""
    }

    fun getPassiveMeaning(lang: String): String? {
        return if (type == "v" && transitivity == "t") (if (lang == "en") englishMeaning else frenchMeaning) else null
    }

    fun getResultMeaning(lang: String): String {
        return if (type == "v" && transitivity == "t") (if (lang == "en") englishMeaning else frenchMeaning) ?: "" else ""
    }

    fun getReflexiveMeaning(lang: String): String? {
        return if (type == "v" && transitivity == "t") (if (lang == "en") englishMeaning else frenchMeaning) else null
    }

    fun isKnown(): Boolean = known

    override fun setAttrs() {
        setAttributes()
        setId()
    }

    open fun setAttributes() {
        setAttributes(HashMap())
    }

    override fun setAttributes(attrs: HashMap<String, Any?>) {
        val baseAttrs = HashMap<String, Any?>()
        baseAttrs["variant"] = variant
        baseAttrs["originalMorpheme"] = originalMorpheme
        baseAttrs["nature"] = nature
        baseAttrs["nb"] = nb
        baseAttrs["known"] = known
        baseAttrs["transitivity"] = transitivity
        baseAttrs["transinfix"] = transinfix
        baseAttrs["intransinfix"] = intransinfix
        baseAttrs["antipassive"] = antipassive
        baseAttrs["number"] = number
        baseAttrs["subtype"] = subtype
        baseAttrs["source"] = source
        baseAttrs.putAll(attrs)
        super.setAttributes(baseAttrs)
    }

    fun getNature(): String? = nature

    private fun setIdsOfCompositesWithThisRoot(v: Vector<String>) {
        idsOfCompositesWithThisRoot = v
    }

    override fun showData(): String {
        val sb = StringBuilder()
        sb.append("[Base: morpheme= ").append(morpheme).append("\n")
        sb.append("id= ").append(id).append("\n")
        sb.append("variant= ").append(variant).append("\n")
        sb.append("nb= ").append(nb).append("\n")
        sb.append("type= ").append(type).append("\n")
        sb.append("nature= ").append(nature).append("\n")
        sb.append("number= ").append(number).append("\n")
        sb.append("compositionRoot= ").append(compositionRoot).append("\n")
        if (type == "v") {
            sb.append("antipassive= ").append(antipassive).append("\n")
        }
        if (nextCondition != null) {
            sb.append("followingSpecificConditions= ").append(nextCondition.toString()).append("\n")
        }
        sb.append("englishMeaning= ").append(englishMeaning).append("\n")
        sb.append("frenchMeaning= ").append(frenchMeaning).append("\n")
        sb.append("dbName= ").append(dbName).append("\n")
        sb.append("tableName= ").append(tableName).append("\n")
        sb.append("dialect= ").append(dialect).append("\n")
        sb.append("cf= ").append(cf).append("\n")
        sb.append("]")
        return sb.toString()
    }

    companion object {
        @JvmField
        val hash = Hashtable<String, Morpheme>()
    }
}
