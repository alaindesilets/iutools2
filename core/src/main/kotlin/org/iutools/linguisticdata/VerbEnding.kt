package org.iutools.linguisticdata

import org.iutools.linguisticdata.constraints.Conditions
import org.iutools.linguisticdata.constraints.Imacond
import org.iutools.linguisticdata.constraints.ParseException
import org.iutools.utilities.Debugging
import java.io.ByteArrayInputStream
import java.util.Hashtable
import java.util.StringTokenizer

class VerbEnding : Affix {

    var mode: String? = null
    var spec: String? = null
    var subjPers: String? = null
    var subjNumber: String? = null
    var objPers: String? = null
    var objNumber: String? = null
    var sameSubject: String? = null
    var posneg: String? = null
    var tense: String? = null

    constructor()

    constructor(v: HashMap<String, String>) {
        morpheme = v["morpheme"]
        Debugging.mess("VerbEnding/1", 1, "morpheme= $morpheme")
        type = v["type"]
        mode = v["mode"]
        subjPers = v["perSubject"]
        subjNumber = v["numbSubject"]
        objPers = v["perObject"]
        objNumber = v["numbObject"]
        spec = if (objPers != null) "sp" else "nsp"
        if (mode == "part") {
            sameSubject = v["sameSubject"]
            posneg = v["posneg"]
            tense = v["tense"]
        }
        dbName = v["dbName"]
        tableName = v["tableName"]

        makeMeanings()

        // Développement des diverses surfaceFormsOfAffixes associées aux 4 contextes
        // voyelle, t, k et q et à leurs actions.
        var form = v["V-form"]
        var act1 = v["V-action1"]
        var act2 = v["V-action2"]
        makeFormsAndActions("V", morpheme!!, form, act1!!, act2)

        form = v["t-form"]
        act1 = v["t-action1"]
        act2 = v["t-action2"]
        makeFormsAndActions("t", morpheme!!, form, act1!!, act2)

        form = v["k-form"]
        act1 = v["k-action1"]
        act2 = v["k-action2"]
        makeFormsAndActions("k", morpheme!!, form, act1!!, act2)

        form = v["q-form"]
        act1 = v["q-action1"]
        act2 = v["q-action2"]
        makeFormsAndActions("q", morpheme!!, form, act1!!, act2)

        makeContextualBehaviours()

        val cs = v["condPrecSpecific"]
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
        hash[key] = obj as Morpheme
    }

    // Signature des terminaisons verbales:
    // <type>-<mode>-<subjPers><subjNumber>[-<objPers><objNumber>][-<tense>]
    override fun getSignature(): String {
        val sb = StringBuilder()
        sb.append(type)
        sb.append("-")
        sb.append(mode)
        sb.append("-")
        sb.append(subjPers)
        sb.append(subjNumber)
        if (objPers != null) {
            sb.append("-")
            sb.append(objPers)
            sb.append(objNumber)
        }
        if (mode == "part") {
            if (tense != null) {
                sb.append("-")
                sb.append(tense)
            }
        }
        return sb.toString()
    }

    override fun getTransitivityConstraint(): String {
        return if (spec == "nsp") "i" else "t"
    }

    override fun getCombiningParts(): Array<String>? = null

    override fun agreeWithTransitivity(trans: String?): Boolean = true

    override fun setAttrs() {
        setAttributes(HashMap())
        setId()
    }

    override fun setAttributes(attrs: HashMap<String, Any?>) {
        val tvAttrs = HashMap<String, Any?>()
        tvAttrs["mode"] = mode
        tvAttrs["spec"] = spec
        tvAttrs["subjPers"] = subjPers
        tvAttrs["subjNumber"] = subjNumber
        tvAttrs["objPers"] = objPers
        tvAttrs["objNumber"] = objNumber
        tvAttrs["sameSubject"] = sameSubject
        tvAttrs["posneg"] = posneg
        tvAttrs["tense"] = tense
        tvAttrs.putAll(attrs)
        super.setAttributes(tvAttrs)
    }

    // Développer les sens en français et en anglais des terminaisons
    // verbales à partir des données suivantes: mode, spécificité,
    // personne et nombres du sujet et de l'objet.
    private fun makeMeanings() {
        val frenchMeaning1 = StringBuilder()
        val englishMeaning1 = StringBuilder()

        if (mode == "dec" || mode == "ger") {
            frenchMeaning1.append("déclaration: ")
            englishMeaning1.append("declaration: ")
        } else if (mode == "int") {
            if (subjPers == "3" || subjPers == "4") {
                frenchMeaning1.append("question: est-ce qu'")
                if (subjNumber == "s") englishMeaning1.append("question: does ")
                else englishMeaning1.append("question: do ")
            } else {
                frenchMeaning1.append("question: est-ce que ")
                englishMeaning1.append("question: do ")
            }
        } else if (mode == "imp") {
            frenchMeaning1.append("ordre: ")
            englishMeaning1.append("order: ")
        } else if (mode == "part") {
            if (tense == null) {
                frenchMeaning1.append("part: ")
                englishMeaning1.append("part: ")
            } else if (tense == "prespas") {
                frenchMeaning1.append("part. présent/passé: ")
                englishMeaning1.append("part. present/past: ")
            } else if (tense == "fut") {
                frenchMeaning1.append("part. futur: ")
                englishMeaning1.append("part. future: ")
            }

            if (subjPers == "3" || subjPers == "4") frenchMeaning1.append("alors/pendant qu'")
            else frenchMeaning1.append("alors/pendant que ")
            englishMeaning1.append("while ")
        } else if (mode == "caus") {
            if (subjPers == "3" || subjPers == "4") frenchMeaning1.append("causal: parce qu'")
            else frenchMeaning1.append("causal: parce que ")
            englishMeaning1.append("becausative: because ")
        } else if (mode == "cond") {
            if ((subjPers == "3" || subjPers == "4") && subjNumber == "s") frenchMeaning1.append("conditionnel: s'")
            else frenchMeaning1.append("conditionnel: si ")
            englishMeaning1.append("conditional: if ")
        } else if (mode == "dub") {
            if ((subjPers == "3" || subjPers == "4") && subjNumber == "s") frenchMeaning1.append("dubitatif: s'")
            else frenchMeaning1.append("dubitatif: si ")
            englishMeaning1.append("dubitative: whether ")
        } else if (mode == "freq") {
            if (subjPers == "3" || subjPers == "4") frenchMeaning1.append("fréquentatif: chaque fois qu' / lorsqu'")
            else frenchMeaning1.append("fréquentatif: chaque fois que / lorsque ")
            englishMeaning1.append("frequentative: whenever ")
        }

        if (subjPers == "1") {
            when (subjNumber) {
                "s" -> { frenchMeaning1.append("je "); englishMeaning1.append("I ") }
                "d" -> { frenchMeaning1.append("nous (deux) "); englishMeaning1.append("we (two) ") }
                "p" -> { frenchMeaning1.append("nous (plusieurs) "); englishMeaning1.append("we (many) ") }
            }
        } else if (subjPers == "2") {
            when (subjNumber) {
                "s" -> { frenchMeaning1.append("tu "); englishMeaning1.append("you ") }
                "d" -> { frenchMeaning1.append("vous (deux) "); englishMeaning1.append("you (two) ") }
                "p" -> { frenchMeaning1.append("vous (plusieurs) "); englishMeaning1.append("you (many) ") }
            }
        } else if (subjPers == "3" || subjPers == "4") {
            when (subjNumber) {
                "s" -> { frenchMeaning1.append("il/elle "); englishMeaning1.append("he/she/it ") }
                "d" -> { frenchMeaning1.append("ils/elles (deux) "); englishMeaning1.append("they (two) ") }
                "p" -> { frenchMeaning1.append("ils/elles (plusieurs) "); englishMeaning1.append("they (many) ") }
            }
        }
        if (mode == "part" && posneg == "neg") {
            frenchMeaning1.append("ne ")
            englishMeaning1.append("not ")
        }
        frenchMeaning1.append("...")
        englishMeaning1.append("...")
        if (mode == "part" && posneg == "neg") frenchMeaning1.append(" pas ")

        if (spec == "sp") {
            when (objPers) {
                "1" -> when (objNumber) {
                    "s" -> { frenchMeaning1.append("moi"); englishMeaning1.append("me") }
                    "d" -> { frenchMeaning1.append("nous (deux)"); englishMeaning1.append("us (two)") }
                    "p" -> { frenchMeaning1.append("nous (plusieurs)"); englishMeaning1.append("us (many)") }
                }
                "2" -> when (objNumber) {
                    "s" -> { frenchMeaning1.append("toi"); englishMeaning1.append("you") }
                    "d" -> { frenchMeaning1.append("vous (deux)"); englishMeaning1.append("you (two)") }
                    "p" -> { frenchMeaning1.append("vous (plusieurs)"); englishMeaning1.append("you (many)") }
                }
                "3" -> when (objNumber) {
                    "s" -> { frenchMeaning1.append("lui/elle"); englishMeaning1.append("him/her/it") }
                    "d" -> { frenchMeaning1.append("eux/elles (deux) "); englishMeaning1.append("them (two) ") }
                    "p" -> { frenchMeaning1.append("eux/elles (plusieurs) "); englishMeaning1.append("them (many) ") }
                }
            }
        }

        if (mode == "int") {
            frenchMeaning1.append("?")
            englishMeaning1.append("?")
        }

        frenchMeaning = frenchMeaning1.toString()
        englishMeaning = englishMeaning1.toString()
    }

    override fun showData(): String {
        val sb = StringBuilder()
        sb.append("\n[VerbEnding: morpheme= ").append(morpheme).append("\n")
        sb.append("type= ").append(type).append("\n")
        sb.append("mode= ").append(mode).append("\n")
        if (mode == "part") {
            sb.append("sameSubject= ").append(sameSubject).append("\n")
            sb.append("posneg= ").append(posneg).append("\n")
            sb.append("tense= ").append(tense).append("\n")
        }
        sb.append("spec= ").append(spec).append("\n")
        sb.append("subjPers= ").append(subjPers).append("\n")
        sb.append("subjNumber= ").append(subjNumber).append("\n")
        sb.append("objPers= ").append(objPers).append("\n")
        sb.append("objNumber= ").append(objNumber).append("\n")
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
        val hash = Hashtable<String, Morpheme>()

        @JvmField
        val modes = arrayOf("caus", "cond", "dec", "dub", "freq", "ger", "imp", "int", "part")

        @JvmField
        val numbers = arrayOf("d", "p", "s")

        @JvmField
        val booleans = arrayOf("false", "true")
    }
}
