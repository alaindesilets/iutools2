package org.iutools.linguisticdata

import org.iutools.linguisticdata.constraints.Conditions
import org.iutools.linguisticdata.constraints.Imacond
import org.iutools.linguisticdata.constraints.ParseException
import org.iutools.utilities.Debugging
import java.io.ByteArrayInputStream
import java.util.Hashtable
import java.util.StringTokenizer

class NounEnding : Affix {

    var grammCase: String? = null
    var cas: String? = null
    var number: String? = null
    var possPers: String? = null
    var possNumber: String? = null
    var poss: String? = null

    constructor()

    constructor(v: HashMap<String, String>) {
        morpheme = v["morpheme"]
        Debugging.mess("NounEnding/1", 1, "morpheme= $morpheme")
        type = v["type"]
        grammCase = v["case"]
        cas = v["case"]
        number = v["number"]
        possPers = v["perPoss"]
        possNumber = v["numbPoss"]
        poss = if (possPers != null) "true" else "false"
        dbName = v["dbName"]
        tableName = v["tableName"]

        makeMeanings()

        // Développement des diverses surfaceFormsOfAffixes associées aux 4 contextes
        // voyelle, t, k et q et à leurs actions.
        val vform = v["V-form"]
        val vact1 = v["V-action1"]
        val vact2 = v["V-action2"]

        val tform = v["t-form"]
        val tact1 = v["t-action1"]
        val tact2 = v["t-action2"]

        val kform = v["k-form"]
        val kact1 = v["k-action1"]
        val kact2 = v["k-action2"]

        val qform = v["q-form"]
        val qact1 = v["q-action1"]
        val qact2 = v["q-action2"]

        makeFormsAndActions("V", morpheme!!, vform, vact1!!, vact2)
        makeFormsAndActions("t", morpheme!!, tform, tact1!!, tact2)
        makeFormsAndActions("k", morpheme!!, kform, kact1!!, kact2)
        makeFormsAndActions("q", morpheme!!, qform, qact1!!, qact2)
        makeContextualBehaviours()

        val cs = v["condPrec"]
        if (cs != null) {
            try {
                preCondition = Imacond(ByteArrayInputStream(cs.toByteArray())).ParseCondition() as Conditions
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
        setAttrs()
    }

    override fun addToHash(key: String, obj: Any) {
        hash[key] = obj as NounEnding
    }

    override fun getSignature(): String {
        val sb = StringBuilder()
        sb.append(type)
        sb.append("-")
        sb.append(grammCase)
        sb.append("-")
        sb.append(number)
        if (possPers != null) {
            sb.append("-")
            sb.append(possPers)
            sb.append(possNumber)
        }
        return sb.toString()
    }

    override fun getTransitivityConstraint(): String? = null

    override fun getCombiningParts(): Array<String>? = null

    override fun agreeWithTransitivity(trans: String?): Boolean = true

    // Développer les sens en français et en anglais des terminaisons
    // verbales à partir des données suivantes: mode, spécificité,
    // personne et nombres du sujet et de l'objet.
    private fun makeMeanings() {
        val frenchMeaning1 = StringBuilder()
        val englishMeaning1 = StringBuilder()

        when (grammCase) {
            "nom" -> { frenchMeaning1.append("nominatif: "); englishMeaning1.append("nominative: ") }
            "gen" -> { frenchMeaning1.append("génitif: de "); englishMeaning1.append("genitive: of ") }
            "acc" -> { frenchMeaning1.append("accusatif: "); englishMeaning1.append("accusative: ") }
            "dat" -> { frenchMeaning1.append("datif: à; avec (instrument) "); englishMeaning1.append("dative: to; with (instrument) ") }
            "abl" -> { frenchMeaning1.append("ablatif: de; par (agent); que (comparaison) "); englishMeaning1.append("ablative: from; by (agent); than (comparaison) ") }
            "loc" -> { frenchMeaning1.append("locatif: dans; sur "); englishMeaning1.append("locative: in; on; upon ") }
            "sim" -> { frenchMeaning1.append("similaris: comme "); englishMeaning1.append("similaris: like ") }
            "via" -> {
                frenchMeaning1.append("vialis: au moyen de; à travers; par; pendant ")
                englishMeaning1.append("vialis: through; by; by means of; across; over; for (period of time) ")
            }
        }

        if (possPers == null) {
            when (number) {
                "s" -> { frenchMeaning1.append("un; une; le; la "); englishMeaning1.append("a; the (one)") }
                "d" -> { frenchMeaning1.append("des; les (deux) "); englishMeaning1.append("two; the (two)") }
                "p" -> { frenchMeaning1.append("des; les (plusieurs) "); englishMeaning1.append("many; the (many)") }
            }
        } else when (possPers) {
            "1" -> when (possNumber) {
                "s" -> when (number) {
                    "s" -> { frenchMeaning1.append("mon; ma "); englishMeaning1.append("my (one thing) ") }
                    "d" -> { frenchMeaning1.append("mes (deux choses)"); englishMeaning1.append("my (two things) ") }
                    "p" -> { frenchMeaning1.append("mes (plusieurs choses) "); englishMeaning1.append("my (many things) ") }
                }
                "d" -> when (number) {
                    "s" -> { frenchMeaning1.append("notre (à nous deux) "); englishMeaning1.append("our (one thing to us two) ") }
                    "d" -> { frenchMeaning1.append("nos (deux choses à nous deux) "); englishMeaning1.append("our (two things to us two) ") }
                    "p" -> { frenchMeaning1.append("nos (plusieurs choses à nous deux) "); englishMeaning1.append("our (many things to us two) ") }
                }
                "p" -> when (number) {
                    "s" -> { frenchMeaning1.append("notre (à nous plusieurs) "); englishMeaning1.append("our (one thing to us many) ") }
                    "d" -> { frenchMeaning1.append("nos (deux choses à nous plusieurs) "); englishMeaning1.append("our (two things to us many) ") }
                    "p" -> { frenchMeaning1.append("nos (plusieurs choses à nous plusieurs) "); englishMeaning1.append("our (many things to us many) ") }
                }
            }
            "2" -> when (possNumber) {
                "s" -> when (number) {
                    "s" -> { frenchMeaning1.append("ton; ta "); englishMeaning1.append("your (one thing to one person) ") }
                    "d" -> { frenchMeaning1.append("tes (deux choses)"); englishMeaning1.append("your (two things to one person) ") }
                    "p" -> { frenchMeaning1.append("tes (plusieurs choses) "); englishMeaning1.append("your (many things to one person) ") }
                }
                "d" -> when (number) {
                    "s" -> { frenchMeaning1.append("votre (à vous deux) "); englishMeaning1.append("your (one thing to you two) ") }
                    "d" -> { frenchMeaning1.append("vos (deux choses à vous deux) "); englishMeaning1.append("your (two things to you two) ") }
                    "p" -> { frenchMeaning1.append("vos (plusieurs choses à vous deux) "); englishMeaning1.append("your (many things to you two) ") }
                }
                "p" -> when (number) {
                    "s" -> { frenchMeaning1.append("votre (à vous plusieurs) "); englishMeaning1.append("your (one thing to you many) ") }
                    "d" -> { frenchMeaning1.append("vos (deux choses à vous plusieurs) "); englishMeaning1.append("your (two things to you many) ") }
                    "p" -> { frenchMeaning1.append("vos (plusieurs choses à vous plusieurs) "); englishMeaning1.append("your (many things to you many) ") }
                }
            }
            "3" -> when (possNumber) {
                "s" -> when (number) {
                    "s" -> { frenchMeaning1.append("son; sa (même personne) "); englishMeaning1.append("his;her;its (one thing, same person) ") }
                    "d" -> { frenchMeaning1.append("ses (deux choses, même personne)"); englishMeaning1.append("his;her;its (two things, same person) ") }
                    "p" -> { frenchMeaning1.append("ses (plusieurs choses, même personne) "); englishMeaning1.append("his;her;its (many things, same person) ") }
                }
                "d" -> when (number) {
                    "s" -> { frenchMeaning1.append("leur (à eux deux, mêmes personnes) "); englishMeaning1.append("their (one thing to them two, same persons) ") }
                    "d" -> { frenchMeaning1.append("leurs (deux choses à eux deux, même personnes) "); englishMeaning1.append("their (two things to them two, same persons) ") }
                    "p" -> { frenchMeaning1.append("leurs (plusieurs choses à eux deux, mêmes personnes) "); englishMeaning1.append("their (many things to them two, same persons) ") }
                }
                "p" -> when (number) {
                    "s" -> { frenchMeaning1.append("leur (à eux plusieurs, mêmes personnes) "); englishMeaning1.append("their (one thing to them many, same persons) ") }
                    "d" -> { frenchMeaning1.append("leurs (deux choses à eux plusieurs, mêmes personnes) "); englishMeaning1.append("their (two things to them many, same persons) ") }
                    "p" -> { frenchMeaning1.append("leurs (plusieurs choses à eux plusieurs, mêmes personnes) "); englishMeaning1.append("their (many things to them many, same persons) ") }
                }
            }
            "4" -> when (possNumber) {
                "s" -> when (number) {
                    "s" -> { frenchMeaning1.append("son; sa (autre personne) "); englishMeaning1.append("his;her;its (one thing, different person) ") }
                    "d" -> { frenchMeaning1.append("ses (deux choses, autre personne)"); englishMeaning1.append("his;her;its (two things, different person) ") }
                    "p" -> { frenchMeaning1.append("ses (plusieurs choses, autre personne) "); englishMeaning1.append("his;her;its (many things, different person) ") }
                }
                "d" -> when (number) {
                    "s" -> { frenchMeaning1.append("leur (à eux deux, autres personnes) "); englishMeaning1.append("their (one thing to them two, different persons) ") }
                    "d" -> { frenchMeaning1.append("leurs (deux choses à eux deux, autres personnes) "); englishMeaning1.append("their (two things to them two, different persons) ") }
                    "p" -> { frenchMeaning1.append("leurs (plusieurs choses à eux deux, autres personnes) "); englishMeaning1.append("their (many things to them two, different persons) ") }
                }
                "p" -> when (number) {
                    "s" -> { frenchMeaning1.append("leur (à eux plusieurs, autres personnes) "); englishMeaning1.append("their (one thing to them many, different persons) ") }
                    "d" -> { frenchMeaning1.append("leurs (deux choses à eux plusieurs, autres personnes) "); englishMeaning1.append("their (two things to them many, different persons) ") }
                    "p" -> { frenchMeaning1.append("leurs (plusieurs choses à eux plusieurs, autres personnes) "); englishMeaning1.append("their (many things to them many, different persons) ") }
                }
            }
        }
        frenchMeaning = frenchMeaning1.toString()
        englishMeaning = englishMeaning1.toString()
    }

    override fun setAttrs() {
        setAttributes(HashMap())
        setId()
    }

    override fun setAttributes(attrs: HashMap<String, Any?>) {
        val tnAttrs = HashMap<String, Any?>()
        tnAttrs["grammCase"] = grammCase
        tnAttrs["cas"] = grammCase
        tnAttrs["number"] = number
        tnAttrs["possPers"] = possPers
        tnAttrs["possNumber"] = possNumber
        tnAttrs["poss"] = poss
        tnAttrs.putAll(attrs)
        super.setAttributes(tnAttrs)
    }

    override fun showData(): String {
        val sb = StringBuilder()
        sb.append("\n[NounEnding: morpheme= ").append(morpheme).append("\n")
        sb.append("type= ").append(type).append("\n")
        sb.append("grammCase= ").append(grammCase).append("\n")
        sb.append("number=").append(number).append("\n")
        sb.append("possPers= ").append(possPers).append("\n")
        sb.append("possNumber= ").append(possNumber).append("\n")
        sb.append(super.showData())
        if (preCondition != null) {
            sb.append("precedingSpecificCondition= ").append(preCondition.toString()).append("\n")
        }
        sb.append("dbName= ").append(dbName).append("\n")
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
        val hash = Hashtable<String, NounEnding>()

        @JvmField
        val cases = arrayOf("abl", "acc", "dat", "gen", "loc", "nom", "sim", "via")

        @JvmField
        val numbers = arrayOf("d", "p", "s")

        @JvmField
        val booleans = arrayOf("false", "true")
    }
}
