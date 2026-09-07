package org.iutools.linguisticdata

import org.apache.logging.log4j.LogManager
import org.iutools.linguisticdata.constraints.Condition
import org.iutools.linguisticdata.constraints.Conditions
import java.util.StringTokenizer
import java.util.Vector

abstract class Affix : Morpheme() {

    var function: String? = null
    var position: String? = null
    var vform: Array<String>? = null
    var vaction1: Array<Action?>? = null
    var vaction2: Array<Action?>? = null
    var tform: Array<String>? = null
    var taction1: Array<Action?>? = null
    var taction2: Array<Action?>? = null
    var kform: Array<String>? = null
    var kaction1: Array<Action?>? = null
    var kaction2: Array<Action?>? = null
    var qform: Array<String>? = null
    var qaction1: Array<Action?>? = null
    var qaction2: Array<Action?>? = null
    var contextualBehaviours: MutableMap<Char, MutableList<ContextualBehaviour>> = HashMap()

    abstract fun getTransitivityConstraint(): String?
    abstract fun addToHash(key: String, obj: Any)

    override fun getOriginalMorpheme(): String = morpheme!!

    fun isSuffix(): Boolean = type == "sn" || type == "sv" || type == "q"

    fun isNonMobileSuffix(): Boolean {
        // Matches the original's `getClass() == Suffix.class` (exact class,
        // not e.g. the Inchoative subclass of Suffix).
        return this.javaClass == Suffix::class.java && (this as Suffix).mobility == "nm"
    }

    fun addPrecConstraint(cond: Condition) {
        if (preCondition == null) {
            preCondition = cond as Conditions
        } else {
            preCondition = Condition.And(preCondition as Condition, cond)
        }
    }

    fun getFormsInContext(context: Char): Set<SurfaceFormInContext> {
        val formsInContext = mutableSetOf<SurfaceFormInContext>()
        val listOfBehaviours = contextualBehaviours[context]
        if (listOfBehaviours != null) {
            for (behaviour in listOfBehaviours) {
                val formsAndEndsOfFormInContextFromBehaviour = behaviour.formsInContext()
                for (formAndEndOfFormInContextFromBehaviour in formsAndEndsOfFormInContextFromBehaviour) {
                    val surfaceFormInContextForBehaviour = SurfaceFormInContext(
                        formAndEndOfFormInContextFromBehaviour[0],
                        formAndEndOfFormInContextFromBehaviour[1],
                        context,
                        this.id!!
                    )
                    formsInContext.add(surfaceFormInContextForBehaviour)
                }
            }
        }
        return formsInContext
    }

    override fun setAttributes(attrs: HashMap<String, Any?>) {
        val affAttrs = HashMap<String, Any?>()
        affAttrs["function"] = function
        affAttrs["position"] = position
        affAttrs["vform"] = vform
        affAttrs["vaction1"] = vaction1
        affAttrs["vaction2"] = vaction2
        affAttrs["tform"] = tform
        affAttrs["taction1"] = taction1
        affAttrs["taction2"] = taction2
        affAttrs["kform"] = kform
        affAttrs["kaction1"] = kaction1
        affAttrs["kaction2"] = kaction2
        affAttrs["qform"] = qform
        affAttrs["qaction1"] = qaction1
        affAttrs["qaction2"] = qaction2
        affAttrs.putAll(attrs)
        super.setAttributes(affAttrs)
    }

    fun getForm(context: Char): Array<String>? {
        return when (context) {
            'V', 'a', 'i', 'u' -> vform
            't' -> tform
            'k' -> kform
            'q' -> qform
            else -> null
        }
    }

    fun getAction1(context: Char): Array<Action?>? {
        return when (context) {
            'V', 'a', 'i', 'u' -> vaction1
            't' -> taction1
            'k' -> kaction1
            'q' -> qaction1
            else -> null
        }
    }

    fun getAction2(context: Char): Array<Action?>? {
        return when (context) {
            'V', 'a', 'i', 'u' -> vaction2
            't' -> taction2
            'k' -> kaction2
            'q' -> qaction2
            else -> null
        }
    }

    // Les chaînes 'alternateForms', 'actions1' et 'action2' contiennent le
    // contenu des champs X-form, X-action1 et X-action2 des enregistrements
    // dans la base de données. Ces champs/chaînes peuvent identifier plus
    // d'une forme+action. Ported as-is from the original algorithm.
    fun makeFormsAndActions(
        context: String,
        morpheme: String,
        forms: String?,
        action1: String,
        action2: String?
    ) {
        val logger = LogManager.getLogger("Affix.makeFormsAndActions")
        logger.debug("morpheme=$morpheme; context=$context; forms=$forms")

        var allForms: String
        var act1Str = action1
        var act2Str = action2

        if (act1Str == "-") {
            allForms = ""
            act1Str = ""
            act2Str = ""
        } else {
            allForms = forms ?: ""
        }

        var stf = StringTokenizer(allForms)
        var nbf = stf.countTokens()

        var st1 = StringTokenizer(act1Str)
        var nba1 = st1.countTokens()

        if (act2Str == null) {
            act2Str = ""
        }
        var st2 = StringTokenizer(act2Str)
        var nba2 = st2.countTokens()

        if (nba1 > nbf && nbf == 1) {
            val initialValue = allForms
            for (j in nbf until nba1) {
                allForms = "$allForms $initialValue"
            }
            stf = StringTokenizer(allForms)
            nbf = stf.countTokens()
        } else if (nbf > nba1 && nba1 == 1) {
            val act1 = act1Str
            for (j in nba1 until nbf) {
                act1Str = "$act1Str $act1"
            }
            st1 = StringTokenizer(act1Str)
            nba1 = st1.countTokens()
            if (nba2 == 1) {
                val act2 = act2Str
                for (j in nba2 until nbf) {
                    act2Str = "$act2Str $act2"
                }
                st2 = StringTokenizer(act2Str)
                nba2 = st2.countTokens()
            }
        }

        val listOfForms = Vector<String>()
        val listOfActions1 = Vector<Action?>()
        val listOfActions2 = Vector<Action?>()

        val pif1 = Regex("^if\\((.+),([a-z]+),([a-z]+)\\)$")
        val pif2 = Regex("^if\\((.+),([a-z]+)\\)$")

        while (stf.hasMoreTokens()) {
            val f = stf.nextToken()
            listOfForms.add(f)
            val act1 = st1.nextToken()
            val mif1 = pif1.matchEntire(act1)
            val mif2 = pif2.matchEntire(act1)
            if (mif1 != null) {
                val condition = mif1.groupValues[1].replace("|", " ")
                val actPos = mif1.groupValues[2]
                val actNeg = mif1.groupValues[3]
                listOfActions1.add(Action.makeAction("$actPos" + "si($condition)"))
                if (actNeg.isNotEmpty()) {
                    listOfForms.add(f)
                    listOfActions1.add(Action.makeAction("$actNeg" + "si(!($condition))"))
                }
                if (act2Str != "") {
                    val ac2 = st2.nextToken()
                    listOfActions2.add(Action.makeAction(ac2))
                    listOfActions2.add(Action.makeAction(ac2))
                } else {
                    listOfActions2.add(Action.makeAction(null))
                    listOfActions2.add(Action.makeAction(null))
                }
            } else if (mif2 != null) {
                val condition = mif2.groupValues[1].replace("|", " ")
                val actPos = mif2.groupValues[2]
                listOfActions1.add(Action.makeAction("$actPos" + "si($condition)"))
                if (act2Str != "") {
                    val ac2 = st2.nextToken()
                    listOfActions2.add(Action.makeAction(ac2))
                    listOfActions2.add(Action.makeAction(ac2))
                } else {
                    listOfActions2.add(Action.makeAction(null))
                    listOfActions2.add(Action.makeAction(null))
                }
            } else {
                listOfActions1.add(Action.makeAction(act1))
                if (act2Str != "") {
                    listOfActions2.add(Action.makeAction(st2.nextToken()))
                } else {
                    listOfActions2.add(Action.makeAction(null))
                }
            }
        }

        val arrayOfForms = listOfForms.toTypedArray()
        val arrayOfActions1 = listOfActions1.toTypedArray()
        val arrayOfActions2 = listOfActions2.toTypedArray()

        val contextChar = context[0]
        when (contextChar) {
            'V' -> {
                vform = arrayOfForms
                vaction1 = arrayOfActions1
                vaction2 = arrayOfActions2
            }
            't' -> {
                tform = arrayOfForms
                taction1 = arrayOfActions1
                taction2 = arrayOfActions2
            }
            'k' -> {
                kform = arrayOfForms
                kaction1 = arrayOfActions1
                kaction2 = arrayOfActions2
            }
            'q' -> {
                qform = arrayOfForms
                qaction1 = arrayOfActions1
                qaction2 = arrayOfActions2
            }
        }
    }

    /**
     * Affixes (except demonstrative endings) have sets of form and actions for each of
     * the 4 contexts Vowel, t, k, q ("context" relates to the final character of the
     * stem the affix attaches to). The combination of a form and of its related actions
     * may affect the end of the stem and also the form of the affix itself. The objects
     * ContextualBehaviour represent those sets of form and actions.
     *
     * Normally, a ContextualBehaviour object is created for each set of form and actions
     * for each context. But certain affixes have the same form and the same actions for the 3
     * consonantal contexts (what Mick Mallon calls "consonant alternators". There is no need
     * then for 3 distinct ContextBehaviour objects; 1 general one will suffice.
     */
    open fun makeContextualBehaviours() {
        val logger = LogManager.getLogger("Affix.makeContextualBehaviours")
        logger.debug("$morpheme: ${vform!!.size}; ${tform!!.size}; ${kform!!.size}; ${qform!!.size}")
        _makeContextualBehavioursForVowelContext()
        _makeContextualBehavioursForConsonantalContext()
    }

    protected fun _makeContextualBehavioursForVowelContext() {
        for (i in vform!!.indices) {
            val behaviourV = ContextualBehaviour('V', vform!![i], vaction1!![i]!!, vaction2!![i])
            addToContextualBehaviours('V', behaviourV)
        }
    }

    protected fun _makeContextualBehavioursForConsonantalContext() {
        val behavioursInContextT = _mapBehaviourContexts('t')
        val behavioursInContextK = _mapBehaviourContexts('k')
        val behavioursInContextQ = _mapBehaviourContexts('q')

        var contextualBehavioursToCheckFrom = behavioursInContextT
        var listOfContextualBehavioursToCheckInto =
            arrayOf(behavioursInContextK, behavioursInContextQ)
        var maxLength = tform!!.size
        if (kform!!.size > maxLength) {
            maxLength = kform!!.size
            contextualBehavioursToCheckFrom = behavioursInContextK
            listOfContextualBehavioursToCheckInto = arrayOf(behavioursInContextT, behavioursInContextQ)
        }
        if (qform!!.size > maxLength) {
            maxLength = qform!!.size
            contextualBehavioursToCheckFrom = behavioursInContextQ
            listOfContextualBehavioursToCheckInto = arrayOf(behavioursInContextT, behavioursInContextK)
        }

        _makeContextualBehavioursForSameBehaviourIn3Contexts(contextualBehavioursToCheckFrom, listOfContextualBehavioursToCheckInto)
        _makeContextualBehavioursForDifferentBehavioursIn3Contexts(contextualBehavioursToCheckFrom, listOfContextualBehavioursToCheckInto)
    }

    protected fun _mapBehaviourContexts(context: Char): MutableMap<String, ContextualBehaviour> {
        val map = mutableMapOf<String, ContextualBehaviour>()
        val forms: Array<String>
        val actions1: Array<Action?>
        val actions2: Array<Action?>
        when (context) {
            't' -> { forms = tform!!; actions1 = taction1!!; actions2 = taction2!! }
            'k' -> { forms = kform!!; actions1 = kaction1!!; actions2 = kaction2!! }
            else -> { forms = qform!!; actions1 = qaction1!!; actions2 = qaction2!! }
        }
        for (i in forms.indices) {
            val key = "${forms[i]},${actions1[i]?.strng ?: ""},${actions2[i]?.strng ?: ""}"
            map[key] = ContextualBehaviour(context, forms[i], actions1[i]!!, actions2[i])
        }
        return map
    }

    protected fun _makeContextualBehavioursForSameBehaviourIn3Contexts(
        contextualBehavioursToCheckFrom: MutableMap<String, ContextualBehaviour>,
        listOfContextualBehavioursToCheckInto: Array<MutableMap<String, ContextualBehaviour>>
    ) {
        var formActionsIterator = contextualBehavioursToCheckFrom.keys.iterator()
        while (formActionsIterator.hasNext()) {
            val formActionsToCheck = formActionsIterator.next()
            val setOfFormActionsToCheckIntoOtherContext1 = listOfContextualBehavioursToCheckInto[0].keys
            val setOfFormActionsToCheckIntoOtherContext2 = listOfContextualBehavioursToCheckInto[1].keys
            if (setOfFormActionsToCheckIntoOtherContext1.contains(formActionsToCheck)
                && setOfFormActionsToCheckIntoOtherContext2.contains(formActionsToCheck)
            ) {
                val behaviour = contextualBehavioursToCheckFrom[formActionsToCheck]!!
                behaviour.context = 'C'
                addToContextualBehaviours('C', behaviour)
                listOfContextualBehavioursToCheckInto[0].remove(formActionsToCheck)
                listOfContextualBehavioursToCheckInto[1].remove(formActionsToCheck)
                contextualBehavioursToCheckFrom.remove(formActionsToCheck)
                formActionsIterator = contextualBehavioursToCheckFrom.keys.iterator()
            }
        }
    }

    protected fun _makeContextualBehavioursForDifferentBehavioursIn3Contexts(
        contextualBehavioursToCheckFrom: MutableMap<String, ContextualBehaviour>,
        listOfContextualBehavioursToCheckInto: Array<MutableMap<String, ContextualBehaviour>>
    ) {
        for (key in contextualBehavioursToCheckFrom.keys) {
            val behaviour = contextualBehavioursToCheckFrom[key]!!
            addToContextualBehaviours(behaviour.context, behaviour)
        }
        for (key in listOfContextualBehavioursToCheckInto[0].keys) {
            val behaviour = listOfContextualBehavioursToCheckInto[0][key]!!
            addToContextualBehaviours(behaviour.context, behaviour)
        }
        for (key in listOfContextualBehavioursToCheckInto[1].keys) {
            val behaviour = listOfContextualBehavioursToCheckInto[1][key]!!
            addToContextualBehaviours(behaviour.context, behaviour)
        }
    }

    fun addToContextualBehaviours(context: Char, contextualBehaviour: ContextualBehaviour) {
        val behavioursForContext = contextualBehaviours.getOrPut(context) { mutableListOf() }
        behavioursForContext.add(contextualBehaviour)
    }

    override fun showData(): String {
        val sb = StringBuilder()
        sb.append("vform= [").append(vform!!.joinToString(",")).append("]\n")
        sb.append("vaction1= [").append(vaction1!!.joinToString(",")).append("]\n")
        sb.append("vaction2= [").append(vaction2!!.joinToString(",")).append("]\n")
        sb.append("tform= [").append(tform!!.joinToString(",")).append("]\n")
        sb.append("taction1= [").append(taction1!!.joinToString(",")).append("]\n")
        sb.append("taction2= [").append(taction2!!.joinToString(",")).append("]\n")
        sb.append("kform= [").append(kform!!.joinToString(",")).append("]\n")
        sb.append("kaction1= [").append(kaction1!!.joinToString(",")).append("]\n")
        sb.append("kaction2= [").append(kaction2!!.joinToString(",")).append("]\n")
        sb.append("qform= [").append(qform!!.joinToString(",")).append("]\n")
        sb.append("qaction1= [").append(qaction1!!.joinToString(",")).append("]\n")
        sb.append("qaction2= [").append(qaction2!!.joinToString(",")).append("]\n")
        return sb.toString()
    }
}
