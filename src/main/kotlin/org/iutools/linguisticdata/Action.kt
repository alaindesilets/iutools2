package org.iutools.linguisticdata

/*
    Affixes may take different forms and act in different ways on the stems
    in different contexts (end of the stem: Vowel, t, k, q. This information
    about contextual forms and actions is contained in the database. For each
    context, 3 fields: form, action1, action2. 'form' is the basic surface form
    of the affix in that context; 'action1' is the primary action of the affix
    on the stem in that context; 'action2' is the secondary action of the affix
    on the stem when that stem, after 'action1' has been applied, ends in 2 vowels.

    Because the database was initially implemented as a CSV file, and because more
    than 1 set of form/action1/action2 are possible for a given affix in a given context,
    it was necessary to use a special symbol (-) to represent "no action".

    Note on scope: the original Java Action also carried expressionResult()/combine()/
    apply()/resultingFormInContext()/getConstraintOnEndOfStemAfterAction() and a
    reflection-based getType()/toString(lang) pair, used only by the web dictionary's
    "explain this action" HTML display and by the unused l2r/failureanalysis code paths.
    None of that is reachable from decomposeWord() (verified by grep across morph/r2l),
    so it was dropped here; `type` became a plain instance property instead of a
    static field read via reflection.
 */
abstract class Action {

    var strng: String? = null
    abstract val type: Int

    abstract fun surfaceForm(form: String): String?
    abstract fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String>?

    open fun getInsert(): String? = null
    open fun getSuppr(): String? = null
    open fun getAssimA(): String? = null
    open fun getCondition(): String? = null

    override fun toString(): String = strng ?: "null"

    //---------------------NEUTRAL----------------------//
    /*
     * This means in fact that there is no action (no effect)
     * on the stem. Pretty much equivalent to NULLACTION. This
     * NEUTRAL was adopted from Mick Mallon's work.
     */
    class Neutral : Action() {
        override val type = NEUTRAL
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            val endOfStem = if (context == 'V') "V" else "C"
            return arrayOf(form, endOfStem)
        }
    }

    //---------------------DELETION-----------------------//
    class Suppression : Action() {
        override val type = DELETION
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            val endOfStem = if (rankOfAction == 1) "V" else "2V"
            return arrayOf(form, endOfStem)
        }
    }

    //---------------------NULL ACTION----------------------//
    /*
     * This is necessary to account for "no action" in contexts
     * where there are more than 1 possible form/actions in the
     * database for a given affix. It is represented in the database
     * by -. When there are only 1 such form/actions set, there is no
     * need for - and its absence means "no action".
     */
    class NullAction : Action() {
        override val type = NULLACTION
        override fun surfaceForm(form: String): String? = null
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            val endOfStem = if (rankOfAction == 1) (if (context == 'V') "V" else "C") else "2V"
            return arrayOf(form, endOfStem)
        }
    }

    //---------------------UNKNOWN-------------------//
    /*
     * This is to cover that the action(s) for certain affixes
     * could not be determined:
     *   k&i/1nv
     *   liqui/1nv
     *   siri/1nv
     *   ujjuaq/1vv
     */
    class Unknown : Action() {
        override val type = UNKNOWN
        override fun surfaceForm(form: String): String? = null
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String>? = null
    }

    //---------------------ASSIMILATION-----------------------//
    class Assimilation : Action() {
        override val type = ASSIMILATION
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            return arrayOf(form, "C") // since only in 't' context
        }
    }

    //---------------------SPECIFIC ASSIMILATION----------------------//
    /*
     * L'assimilation spécifique n'est définie que pour 3 terminaisons verbales,
     * toutes commençant par -pa: -pat, -patik, -pata, et toutes dans le contexte T.
     */
    class SpecificAssimilation(str: String) : Action() {
        override val type = SPECIFICASSIMILATION
        val assimileA: String = str.substring(str.indexOf('(') + 1, str.indexOf(')'))

        override fun getAssimA(): String = assimileA
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            return arrayOf(form, "C")
        }
    }

    //---------------------FUSION---------------------//
    /*
        Fusion should be replaced in the database by 'suppression' (deletion).
     */
    class Fusion : Action() {
        override val type = FUSION
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            val endOfStem = if (rankOfAction == 1) "V" else "2V"
            return arrayOf(form, endOfStem)
        }
    }

    //---------------------VOICING------------------------//
    class Voicing : Action() {
        override val type = VOICING
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            return arrayOf(form, "C")
        }
    }

    //---------------------NASALIZATION-------------------------//
    class Nasalization : Action() {
        override val type = NASALIZATION
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            return arrayOf(form, "C")
        }
    }

    //---------------------CONDITIONAL NASALIZATION-----------------------//
    /*
     * (see conditions if(cond,actYes,actNo) in .csv data files; "if" conditions
     * generate action objects for actYes and actNo)
     */
    class ConditionalNasalization(str: String) : Action() {
        override val type = CONDITIONALNASALIZATION
        private val cond: String = str

        override fun getCondition(): String = cond
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            return arrayOf(form, "C")
        }
    }

    //---------------------SPECIFIC DELETION------------------------//
    /*
     * Happens only in the Q context for a couple of infixes as a second action
     * when the first action deleted the 'q' final: the last of the two remaining
     * vowels is deleted if it is the specified vowel.
     */
    class SpecificSuppression : Action {
        override val type = SPECIFICDELETION
        var suppressed: String? = null
        private var cond: String? = null

        constructor(str: String) {
            suppressed = str
        }

        constructor(condIn: String, str: String) {
            suppressed = str
            cond = condIn
        }

        override fun getSuppr(): String? = suppressed
        override fun getCondition(): String? = cond
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            val endOfStem = if (rankOfAction == 1) "V" else "2V"
            return arrayOf(form, endOfStem)
        }
    }

    //---------------------CONDITIONAL DELETION--------------------//
    /*
     * L'action conditionnelle est créée lors de la lecture d'actions de forme if()
     * dans les champs d'actions des tables de la base de données. Pour les
     * infixes, cette action ne survient que pour l'antipassif ji lorsqu'il est
     * précédé de uti, donc dans le contexte de voyelle: if(id:uti/1vv,s,n).
     */
    class ConditionalSuppression(str: String) : Action() {
        override val type = CONDITIONALDELETION
        private val cond: String = str.substring(str.indexOf('(') + 1, str.indexOf(')'))

        override fun getCondition(): String = cond
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            val endOfStem = if (rankOfAction == 1) "V" else "2V"
            return arrayOf(form, endOfStem)
        }
    }

    //---------------------INSERTION---------------------//
    class Insertion(str: String) : Action() {
        override val type = INSERTION
        val inserted: String = str.substring(str.indexOf('(') + 1, str.indexOf(')'))

        override fun getInsert(): String = inserted
        override fun surfaceForm(form: String): String = inserted + form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            val endOfStem = if (rankOfAction == 1) context.toString() else "2V"
            return arrayOf(inserted + form, endOfStem)
        }
    }

    //---------------------DELETION AND INSERTION-----------------------//
    /*
     * Très peu fréquente. Seulement en action1.
     */
    class SuppressionAndInsertion(str: String) : Action() {
        override val type = DELETIONINSERTION
        val inserted: String = str.substring(str.indexOf('(') + 1, str.indexOf(')'))

        override fun getInsert(): String = inserted
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            val endOfStem = if (rankOfAction == 1) "V" else "2V"
            return arrayOf(inserted + form, endOfStem)
        }
    }

    //---------------------VOWEL LENGTHENING-----------------------//
    /*
     * This action happens only as a first action in V context in noun endings.
     */
    class VowelLengthening : Action() {
        override val type = VOWELLENGTHENING
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            return arrayOf(form, "V")
        }
    }

    //---------------------CANCELLATION------------------------//
    /*
       This action happens only as action2 with action1 = allV and sallV.
     */
    class Cancellation : Action() {
        override val type = CANCELLATION
        override fun surfaceForm(form: String): String? = null
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String>? = null
    }

    //---------------------SELF-DECAPITATION----------------------//
    /*
     * happens only as second action after deleting a consonant when the
     * resultant stem ends in 2 vowels
     */
    class Selfdecapitation : Action() {
        override val type = SELFDECAPITATION
        override fun surfaceForm(form: String): String = form.substring(1)
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            val endOfStem = if (rankOfAction == 1) "1V" else "2V"
            return arrayOf(form.substring(1), endOfStem)
        }
    }

    //---------------------INSERTION AND VOWEL LENGTHENING-------------------//
    /*
     * This action happens only in T context as first action in (dual) noun endings.
     */
    class InsertionAndVowelLengthening(str: String) : Action() {
        override val type = INSERTIONVOWELLENGTHENING
        val inserted: String = str.substring(str.indexOf('(') + 1, str.indexOf(')'))

        override fun getInsert(): String = inserted
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            return arrayOf(form.substring(1), "t") // because only in 't' context: insertion of 'i' after 't'
        }
    }

    //--------------------DELETION AND VOWEL LENGTHENING--------------------------//
    class SuppressionAndVowelLengthening : Action() {
        override val type = DELETIONVOWELLENGTHENING
        override fun surfaceForm(form: String): String = form
        override fun formAndEndOfStemInContext(form: String, context: Char, rankOfAction: Int): Array<String> {
            return arrayOf(form, "2V")
        }
    }

    companion object {
        const val NULLACTION = 0
        const val INSERTION = 1
        const val SPECIFICDELETION = 2
        const val DELETION = 3
        const val VOICING = 4
        const val NASALIZATION = 5
        const val NEUTRAL = 6
        const val FUSION = 7
        const val ASSIMILATION = 8
        const val SPECIFICASSIMILATION = 9
        const val VOWELLENGTHENING = 11
        const val CANCELLATION = 12
        const val SELFDECAPITATION = 13
        const val INSERTIONVOWELLENGTHENING = 14
        const val DELETIONVOWELLENGTHENING = 15
        const val CONDITIONALDELETION = 16
        const val DELETIONINSERTION = 17
        const val CONDITIONALDELETIONMORPHEME = 18
        const val CONDITIONALNASALIZATION = 19
        const val UNKNOWN = 1000

        private val insidePattern = Regex("^[a-z]+\\((.+)\\)$")

        @JvmStatic
        fun makeAction(): Action? = null

        @JvmStatic
        fun makeAction(strng: String?): Action? {
            var inside: String? = null
            if (strng != null) {
                val m = insidePattern.matchEntire(strng)
                if (m != null) inside = m.groupValues[1]
            }

            val action: Action? = if (strng == null || strng == "-" || strng == "0") {
                NullAction()
            } else if (strng == "?") {
                Unknown()
            } else if (strng.startsWith("i(") || strng.startsWith("ins(")) {
                Insertion(strng)
            } else if (strng == "s" || strng == "suppr") {
                Suppression()
            } else if (strng.startsWith("s(") || strng.startsWith("suppr(")) {
                SpecificSuppression(inside!!)
            } else if (strng.startsWith("ssi(") || strng.startsWith("supprsi(")) {
                val condSupp = inside!!.split(",")
                if (condSupp.size == 1) {
                    ConditionalSuppression(strng)
                } else {
                    SpecificSuppression(condSupp[0], condSupp[1])
                }
            } else if (strng.startsWith("si(") || strng.startsWith("sins(") ||
                strng.startsWith("suppri(") || strng.startsWith("supprins(")
            ) {
                SuppressionAndInsertion(strng)
            } else if (strng == "son" || strng == "sonor") {
                Voicing()
            } else if (strng == "nas" || strng == "nasal") {
                Nasalization()
            } else if (strng.startsWith("nassi(")) {
                ConditionalNasalization(inside!!)
            } else if (strng == "fus" || strng == "fusion") {
                Fusion()
            } else if (strng == "n" || strng == "neutre") {
                Neutral()
            } else if (strng == "a" || strng == "assim") {
                Assimilation()
            } else if (strng.startsWith("a(") || strng.startsWith("assim(")) {
                SpecificAssimilation(strng)
            } else if (strng == "allV") {
                VowelLengthening()
            } else if (strng == "x" || strng == "annul") {
                Cancellation()
            } else if (strng == "decap") {
                Selfdecapitation()
            } else if (strng.startsWith("iallV(") || strng.startsWith("insallV(")) {
                InsertionAndVowelLengthening(strng)
            } else if (strng == "sallV" || strng == "supprallV") {
                SuppressionAndVowelLengthening()
            } else {
                null
            }

            action?.strng = strng
            return action
        }
    }
}
