package org.iutools.linguisticdata

import org.iutools.utilities.Debugging
import java.util.Hashtable

class DemonstrativeEnding : Affix {

    var grammCase: String? = null
    var number: String? = null

    constructor()

    constructor(v: HashMap<String, String>) {
        morpheme = v["morpheme"]
        Debugging.mess("DemonstrativeEnding/1", 1, "morpheme= $morpheme")
        type = v["type"]
        grammCase = v["case"]
        number = v["number"]
        englishMeaning = v["engMean"]
        frenchMeaning = v["freMean"]
        dbName = v["dbName"]
        tableName = v["tableName"]
        setAttrs()
        makeContextualBehaviours()
    }

    override fun addToHash(key: String, obj: Any) {
        hash[key] = obj as DemonstrativeEnding
    }

    override fun getTransitivityConstraint(): String? = null

    override fun getCombiningParts(): Array<String>? = null

    /*
        Demonstrative endings are a special type of affix in that
        they do not have different forms and actions in different
        contexts, like noun endings, verb endings and suffixes.
        They can be considered as having a neutral action in all
        possible contexts. IMPORTANT: adverbial demonstrative radicals
        may also end in 'v', 'n' and 'g', so an additional context
        should be created: 'C' for "any consonant".
     */
    override fun makeContextualBehaviours() {
        val contextualBehaviour = ContextualBehaviour('C', morpheme!!, Action.Neutral(), null)
        contextualBehaviours['C'] = mutableListOf(contextualBehaviour)
    }

    override fun getSignature(): String {
        val sb = StringBuilder()
        sb.append(type)
        sb.append("-")
        sb.append(grammCase)
        if (number != null) {
            sb.append("-")
            sb.append(number)
        }
        return sb.toString()
    }

    override fun agreeWithTransitivity(trans: String?): Boolean = true

    override fun setAttrs() {
        setAttributes(HashMap())
        setId()
    }

    override fun setAttributes(attrs: HashMap<String, Any?>) {
        val endingAttrs = HashMap<String, Any?>()
        endingAttrs["case"] = grammCase
        endingAttrs["number"] = number
        endingAttrs.putAll(attrs)
        super.setAttributes(endingAttrs)
    }

    override fun showData(): String {
        val sb = StringBuilder()
        sb.append("\n[DemonstrativeEnding: morpheme= ").append(morpheme).append("\n")
        sb.append("type= ").append(type).append("\n")
        sb.append("case= ").append(grammCase).append("\n")
        sb.append("number= ").append(number).append("\n")
        sb.append("englishMeaning= ").append(englishMeaning).append("\n")
        sb.append("frenchMeaning= ").append(frenchMeaning).append("]\n")
        return sb.toString()
    }

    companion object {
        @JvmField
        val hash = Hashtable<String, DemonstrativeEnding>()

        @JvmField
        val cases = arrayOf("abl", "acc", "dat", "gen", "loc", "nom", "sim", "via")

        @JvmField
        val types = arrayOf("tad", "tpd")
    }
}
