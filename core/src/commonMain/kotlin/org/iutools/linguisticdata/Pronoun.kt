package org.iutools.linguisticdata

import org.iutools.linguisticdata.constraints.Conditions
import org.iutools.linguisticdata.constraints.Imacond
import org.iutools.linguisticdata.constraints.ParseException
import java.io.ByteArrayInputStream
import java.util.Hashtable

class Pronoun : Base {

    var person: String? = null

    constructor() : super()

    @Throws(LinguisticDataException::class)
    constructor(v: HashMap<String, String>) : super() {
        getAndSetBaseAttributes(v)
        type = v["type"]
        number = v["number"]
        variant = v["variant"]
        nb = v["nb"]
        if (nb.isNullOrEmpty()) {
            nb = "1"
        }
        nature = v["nature"]
        person = v["per"]
        val comb = v["combination"]
        if (comb != null) {
            setCombiningParts(comb)
        }
        val cs = v["condOnNext"]
        if (!cs.isNullOrEmpty()) {
            try {
                nextCondition = Imacond(ByteArrayInputStream(cs.toByteArray())).ParseCondition() as Conditions
            } catch (e: ParseException) {
            }
        }
        setAttrs()
    }

    override fun addToHash(key: String, obj: Any) {
        hash[key] = obj as Pronoun
    }

    override fun setAttributes() {
        val prAttrs = HashMap<String, Any?>()
        prAttrs["person"] = person
        super.setAttributes(prAttrs)
    }

    fun isFirstPerson(): Boolean = person == "1"

    fun isSecondPerson(): Boolean = person == "2"

    fun isThirdPerson(): Boolean = person == "3"

    override fun showData(): String {
        val sb = StringBuilder()
        sb.append("[Pronoun: morpheme= ").append(morpheme).append("\n")
        sb.append("id= ").append(id).append("\n")
        sb.append("variant= ").append(variant).append("\n")
        sb.append("nb= ").append(nb).append("\n")
        sb.append("type= ").append(type).append("\n")
        sb.append("nature= ").append(nature).append("\n")
        sb.append("per= ").append(person).append("\n")
        sb.append("number= ").append(number).append("\n")
        sb.append("englishMeaning= ").append(englishMeaning).append("\n")
        sb.append("frenchMeaning= ").append(frenchMeaning).append("\n")
        sb.append("dbName= ").append(dbName).append("\n")
        sb.append("tableName= ").append(tableName).append("\n")
        sb.append("]")
        return sb.toString()
    }

    companion object {
        @JvmField
        val hash = Hashtable<String, Pronoun>()
    }
}
