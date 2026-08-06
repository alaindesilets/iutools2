package org.iutools.linguisticdata

import java.util.Hashtable

class Demonstrative : Base {

    @JvmField var root: String? = null
    @JvmField var objectType: String? = null

    constructor() : super()

    @Throws(LinguisticDataException::class)
    constructor(v: HashMap<String, String>) : super() {
        type = v["type"]
        demonstrative(v)
    }

    @Throws(LinguisticDataException::class)
    constructor(v: HashMap<String, String>, prefixType: String) : super() {
        type = prefixType + v["type"]
        demonstrative(v)
    }

    @Throws(LinguisticDataException::class)
    private fun demonstrative(v: HashMap<String, String>) {
        getAndSetBaseAttributes(v)
        number = v["number"]
        objectType = v["objectType"]
        root = v["root"]
        setAttrs()
    }

    override fun addToHash(key: String, obj: Any) {
        hash[key] = obj as Demonstrative
    }

    // Signature: <type>-<objectType> pour les adverbes démonstratifs
    // Signature: <type>-<objectType>[-<number>] pour les pronoms démonstratifs
    override fun getSignature(): String {
        val sb = StringBuilder()
        sb.append(type)
        sb.append("-")
        sb.append(objectType)
        var nbr: String? = null
        if (isSingular()) nbr = "s"
        else if (isDual()) nbr = "d"
        else if (isPlural()) nbr = "p"
        if (type!!.endsWith("pd") && !nbr.isNullOrEmpty()) {
            sb.append("-")
            sb.append(nbr)
        }
        return sb.toString()
    }

    fun getRoot(): String? = root

    fun getObjectType(): String? = objectType

    override fun setAttributes() {
        val demAttrs = HashMap<String, Any?>()
        demAttrs["root"] = root
        demAttrs["objectType"] = objectType
        super.setAttributes(demAttrs)
    }

    override fun showData(): String {
        val sb = StringBuilder()
        sb.append("[Demonstrative: ")
        sb.append("morpheme= ").append(morpheme).append("\n")
        sb.append("type= ").append(type).append("\n")
        sb.append("objectType= ").append(objectType).append("\n")
        sb.append("root= ").append(root).append("\n")
        sb.append("number= ").append(number).append("\n")
        sb.append("englishMeaning= ").append(englishMeaning).append("\n")
        sb.append("frenchMeaning= ").append(frenchMeaning).append("\n")
        sb.append("dbName= ").append(dbName).append("\n")
        sb.append("tableName= ").append(tableName).append("\n")
        sb.append("]\n")
        return sb.toString()
    }

    companion object {
        @JvmField
        val hash = Hashtable<String, Demonstrative>()
    }
}
