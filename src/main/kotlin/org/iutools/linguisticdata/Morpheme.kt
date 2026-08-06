package org.iutools.linguisticdata

import org.apache.logging.log4j.LogManager
import org.iutools.linguisticdata.constraints.Conditions
import org.iutools.morph.r2l.AffixPartOfComposition
import java.util.StringTokenizer
import java.util.Vector

abstract class Morpheme : Cloneable {

    enum class MorphFormat { WITH_BRACES, NO_BRACES }

    @JvmField var id: String? = null
    @JvmField var type: String? = null
    @JvmField var morpheme: String? = null
    @JvmField var englishMeaning: String? = null
    @JvmField var frenchMeaning: String? = null
    @JvmField var nb: String? = null
    @JvmField var sources: Array<String>? = null
    @JvmField var num: Int? = null
    @JvmField var dbName: String? = null
    @JvmField var tableName: String? = null
    @JvmField var idObj: Id? = null
    @JvmField var dialect: String? = null
    @JvmField var combinedMorphemes: Array<String>? = null

    @JvmField var cf: String? = null // référence à d'autres morphèmes
    @JvmField var cfs: Array<String>? = null // tableau de références à d'autres morphèmes

    @JvmField var preCondition: Conditions? = null
    @JvmField var nextCondition: Conditions? = null

    @JvmField var attributes: HashMap<String, Any?>? = null

    abstract fun agreeWithTransitivity(trans: String?): Boolean
    abstract fun showData(): String
    abstract fun setAttrs()
    abstract fun getSignature(): String
    abstract fun getOriginalMorpheme(): String

    fun getTableName(): String? = tableName

    open fun getCombiningParts(): Array<String>? = combinedMorphemes

    public override fun clone(): Any = super.clone()

    fun copyOf(): Morpheme = this.clone() as Morpheme

    fun getPrecCond(): Conditions? = preCondition

    fun getNextCond(): Conditions? = nextCondition

    fun meetsTransitivityCondition(transitivity: String?): Boolean {
        return agreeWithTransitivity(transitivity)
    }

    @Throws(LinguisticDataException::class)
    fun meetsConditions(conds: Conditions?): Boolean {
        var res = true
        if (conds != null) {
            res = conds.isMetBy(this)
        }
        return res
    }

    @Throws(LinguisticDataException::class)
    fun meetsConditions(conds: Conditions?, followingMorphemes: Vector<AffixPartOfComposition>): Boolean {
        var res = true
        if (conds != null) {
            res = conds.isMetBy(this)
        }
        if (res) {
            if (getNextCond() != null) {
                if (followingMorphemes.size != 0) {
                    val affPrec = followingMorphemes.elementAt(0).getAffix()
                    res = getNextCond()!!.isMetBy(affPrec!!)
                }
            }
        }
        return res
    }

    @Throws(LinguisticDataException::class)
    fun getLastCombiningMorpheme(): Morpheme? {
        val parts = getCombiningParts()
        var lastMorpheme: Morpheme? = null
        if (parts != null) {
            val lastPart = parts[parts.size - 1]
            if (lastPart != "?") {
                lastMorpheme = getMorpheme(lastPart)
            }
        }
        return lastMorpheme
    }

    fun attrEqualsValue(attr: String, value: String, eq: Boolean): Boolean {
        val logger = LogManager.getLogger("Morpheme.attrEqualsValue")
        logger.debug("morpheme's id: $id")
        logger.debug("attr= $attr; val= $value")
        var res: Boolean
        val valAttr = getAttr(attr)
        var valAspect: String? = value
        logger.debug("valAspect= $valAspect")
        if (valAspect!!.startsWith("X") && attributes!!.containsKey(valAspect.substring(1))) {
            logger.debug("valAspect $valAspect contained in attributes")
            valAspect = getAttr(valAspect.substring(1))
        }
        logger.debug("valAspect= $valAspect")
        if (valAspect == null || valAspect == "null") {
            res = if (eq) (valAttr == null) else (valAttr != null)
        } else if (valAttr != null) {
            val valAttrs = valAttr.split(" ")
            if (eq) {
                res = false
                for (v in valAttrs) {
                    res = v == valAspect
                    if (res) break
                }
            } else {
                res = false
                for (v in valAttrs) {
                    res = valAttr != valAspect
                }
            }
        } else {
            res = false
        }
        return res
    }

    fun getNb(): String? = nb

    fun setId() {
        val logger = LogManager.getLogger("Morpheme.setId")
        val canonicalForm = getOriginalMorpheme()
        val signature = getSignature()
        idObj = Id(canonicalForm, signature)
        id = idObj!!.id
        logger.debug("$id -- $canonicalForm; $signature -> $id")
        attributes!!["id"] = id
    }

    fun getAttr(attr: String): String? = attributes?.get(attr) as String?

    open fun setAttributes(attrs: HashMap<String, Any?>) {
        attributes = HashMap()
        attributes!!.putAll(attrs)
        attributes!!["type"] = type
        attributes!!["nb"] = nb
        attributes!!["morpheme"] = morpheme
        attributes!!["englishMeaning"] = englishMeaning
        attributes!!["frenchMeaning"] = frenchMeaning
        attributes!!["sources"] = sources
        attributes!!["num"] = num
        attributes!!["dbName"] = dbName
        attributes!!["tableName"] = tableName
        attributes!!["idObj"] = idObj
        attributes!!["dialect"] = dialect
        attributes!!["cf"] = cf
        attributes!!["cfs"] = cfs
        attributes!!["preCondition"] = preCondition
        attributes!!["nextCondition"] = nextCondition
        attributes!!["combinedMorphemes"] = combinedMorphemes
    }

    class Id {
        var morphemeName: String? = null
        var signature: String? = null
        var id: String? = null

        constructor(morphId: String) {
            val st = StringTokenizer(morphId, delimiter)
            if (st.countTokens() == 2) {
                morphemeName = st.nextToken()
                signature = st.nextToken()
                id = morphId
            }
        }

        constructor(morphName: String, sign: String) {
            morphemeName = morphName
            signature = sign
            id = morphemeName + delimiter + signature
        }

        fun toHTML(): String = "$morphemeName<sup>$signature</sup>"

        companion object {
            const val delimiter = "/"

            @JvmStatic
            fun toHTML(morphemeId: String): String {
                val morphName = morphemeId.substring(0, morphemeId.indexOf(delimiter))
                val sign = morphemeId.substring(morphemeId.indexOf(delimiter) + 1)
                return "$morphName<sub>$sign</sub>"
            }
        }
    }

    override fun toString(): String = id ?: "null"

    companion object {
        private val pattMorphID = Regex("^([^/]+)/\\d*(.*)$")

        @JvmStatic
        @Throws(LinguisticDataException::class)
        fun getMorpheme(morphemeId: String): Morpheme? {
            var morph: Morpheme? = LinguisticData.getInstance().getAffixWithId(morphemeId)
            if (morph == null) {
                morph = LinguisticData.getInstance().getBaseWithId(morphemeId)
            }
            return morph
        }

        @JvmStatic
        @Throws(MorphemeException::class)
        fun splitMorphID(morphID: String?): Pair<String, String> {
            var canonicalForm = ""
            var specs = ""
            if (!morphID.isNullOrEmpty()) {
                val noBrackets = morphID.replace(Regex("[{}]"), "")
                val match = pattMorphID.matchEntire(noBrackets)
                    ?: throw MorphemeException("Invalid morpheme ID '$morphID'")
                canonicalForm = match.groupValues[1]
                specs = match.groupValues[2]
            }
            return Pair(canonicalForm, specs)
        }

        @JvmStatic
        @Throws(MorphemeException::class)
        fun hasCanonicalForm(morpID: String, canonicalForm: String): Boolean {
            return canonicalForm == canonicalForm(morpID)
        }

        @JvmStatic
        @Throws(MorphemeException::class)
        fun canonicalForm(morphID: String): String {
            return splitMorphID(morphID).first
        }

        @JvmStatic
        @Throws(MorphemeException::class)
        fun typeConstraints(morphID: String?): Pair<String, String> {
            var attachesTo = "X"
            var resultsIn = "%"
            if (morphID != null) {
                val specs = morphemeSpecs(morphID)
                if (specs.length == 1) {
                    attachesTo = "%"
                    resultsIn = specs
                } else {
                    if (specs.startsWith("t")) {
                        attachesTo = "X"
                        resultsIn = "%"
                    } else {
                        attachesTo = specs.substring(0, 1)
                        resultsIn = specs.substring(1, 2)
                    }
                }
            }
            return Pair(attachesTo, resultsIn)
        }

        @Throws(MorphemeException::class)
        private fun morphemeSpecs(morphID: String): String {
            return splitMorphID(morphID).second
        }

        @JvmStatic
        fun removeIDBraces(morphId: String): String {
            return morphId.replace("{", "").replace("}", "")
        }

        @JvmStatic
        fun removeIDBraces(morphIds: Array<String>): Array<String> {
            return Array(morphIds.size) { ii ->
                var morphId = morphIds[ii]
                morphId = morphId.replace(Regex("\\s+"), "")
                morphId = morphId.replace("{", "").replace("}", "")
                morphId
            }
        }

        @JvmStatic
        fun withBraces(morphemes: Array<String>): Array<String> {
            return Array(morphemes.size) { ii -> withBraces(morphemes[ii]) }
        }

        @JvmStatic
        fun withBraces(morph: String): String {
            var m = morph.replace(Regex("\\s+"), "")
            if (!m.startsWith("{")) {
                m = "{$m"
            }
            if (!m.endsWith("}")) {
                m += "}"
            }
            return m
        }

        @JvmStatic
        fun format(origMorphemes: Array<String>?, format: MorphFormat?): Array<String>? {
            val fmt = format ?: MorphFormat.WITH_BRACES
            if (origMorphemes == null) {
                return null
            }
            return Array(origMorphemes.size) { ii ->
                var aFormatted = origMorphemes[ii]
                if (fmt == MorphFormat.NO_BRACES) {
                    aFormatted = aFormatted.replace(Regex("[{}]"), "")
                } else {
                    if (!aFormatted.startsWith("{")) {
                        aFormatted = "{$aFormatted"
                    }
                    if (!aFormatted.endsWith("}")) {
                        aFormatted = "$aFormatted}"
                    }
                }
                aFormatted
            }
        }
    }

    /**
     * Indicates if this morpheme is a composite (i.e. made up of two smaller
     * morphemes).
     */
    fun isComposite(): Boolean {
        return tableName!!.matches(Regex("(CommonCompositeWords|UndecomposableCompositeWords|WordsRelatedToRoots)"))
    }
}
