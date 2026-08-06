package org.iutools.morph.r2l

import org.iutools.linguisticdata.Action
import org.iutools.linguisticdata.Affix
import org.iutools.linguisticdata.LinguisticDataException
import org.iutools.linguisticdata.SurfaceFormOfAffix
import org.iutools.linguisticdata.constraints.Condition
import org.iutools.linguisticdata.constraints.Imacond
import org.iutools.linguisticdata.constraints.ParseException
import org.iutools.morph.MorphologicalAnalyzerException
import org.iutools.phonology.Dialect
import org.iutools.script.Orthography
import org.iutools.script.Roman
import org.iutools.utilities.StopWatch
import java.util.Vector
import java.util.concurrent.TimeoutException

object MorphAnalyzerValidation {

    private var stpw: StopWatch? = null

    @JvmStatic
    fun setStopWatch(_stpw: StopWatch) {
        stpw = _stpw
    }

    private typealias Res = Triple<String, String, AffixPartOfComposition>

    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class)
    @JvmStatic
    fun validateContextActions(
        context: String?,
        action1: Action,
        action2: Action,
        stem: String,
        posAffix: Int,
        affix: Affix,
        form: SurfaceFormOfAffix,
        isSyllabic: Boolean,
        checkPossibleDialectalChanges: Boolean,
        affixCandidate: String
    ): Array<ContextualResult>? {

        val action1Type = action1.type
        val action2Type = action2.type

        var res: Vector<Res> = Vector()

        // Morceau qui sera enregistré dans la décomposition.
        val partOfComp = AffixPartOfComposition(posAffix, form)

        /*
         * Si cet affixe est non-mobile et qu'il est accepté, il faudra lui ajouter une
         * contrainte sur le morphème précédent, pour passer cette contrainte à
         * l'extérieur de cette méthode. (Seuls les suffixes ont cette propriété).
         */
        if (affix.isNonMobileSuffix()) {
            val avc = Condition.NonMobilityOfInfix(affix.id!!)
            affix.addPrecConstraint(avc)
        }

        try {
            if (action1Type == Action.NEUTRAL && action2Type == Action.NULLACTION) {
                res = validate_neutral_null(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp)
            } else if (action1Type == Action.NEUTRAL && action2Type == Action.DELETION) {
                res = validate_neutral_deletion(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp)
            } else if (action1Type == Action.NEUTRAL && action2Type == Action.INSERTION) {
                res = validate_neutral_insertion(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp)
            } else if (action1Type == Action.DELETION && action2Type == Action.NULLACTION) {
                res = validate_deletion_null(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp)
            } else if (action1Type == Action.DELETIONINSERTION && action2Type == Action.NULLACTION) {
                res = validate_deletion_insertion_simple(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp)
            } else if (action1Type == Action.CONDITIONALDELETION && action2Type == Action.NULLACTION) {
                res = validate_conditionaldeletion_null(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.VOICING && action2Type == Action.NULLACTION) {
                res = validate_voicing_null(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.NASALIZATION) {
                res = validate_nasalization_null(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.CONDITIONALNASALIZATION) {
                res = validate_conditionalnasalization_null(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.INSERTION && action2Type == Action.NULLACTION) {
                res = validate_insertion_null(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.FUSION && action2Type == Action.NULLACTION) {
                res = validate_fusion_null(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.ASSIMILATION && action2Type == Action.NULLACTION) {
                res = validate_assimilation_null(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.SPECIFICASSIMILATION && action2Type == Action.NULLACTION) {
                res = validate_specificassimilation_null(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.DELETION && action2Type == Action.SPECIFICDELETION) {
                res = validate_deletion_specificdeletion(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.DELETION && action2Type == Action.INSERTION) {
                res = validate_deletion_insertion(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.VOWELLENGTHENING && action2Type == Action.CANCELLATION) {
                res = validate_vowellengthening_cancellation(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.DELETIONVOWELLENGTHENING && action2Type == Action.CANCELLATION) {
                res = validate_deletionvowellengthening_cancellation(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.INSERTIONVOWELLENGTHENING && action2Type == Action.NULLACTION) {
                res = validate_insertionvowellengthening_null(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.NEUTRAL && action2Type == Action.SELFDECAPITATION) {
                res = validate_neutral_selfdecapitation(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.DELETION && action2Type == Action.SELFDECAPITATION) {
                res = validate_deletion_selfdecapitation(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            } else if (action1Type == Action.DELETION && action2Type == Action.DELETION) {
                res = validate_deletion_deletion(context, action1, action2, stem, affixCandidate, form, affix, posAffix, partOfComp, checkPossibleDialectalChanges)
            }
        } catch (e: LinguisticDataException) {
            throw MorphologicalAnalyzerException(e)
        }

        // Avant de retourner 'res', on vérifie certaines choses, entre autres:
        // a. le radical ne peut pas se terminer par 2 consonnes
        //    (Sauf pour les racines démonstratives!!! exemple: tavv-ani)
        if (affix.type != "tad") {
            var i = 0
            while (i < res.size) {
                stpw!!.check("validateContextActions -- checking stem with 2 consonants")
                val stemres = res[i].first
                if (stemres.length > 2 &&
                    Roman.typeOfLetterLat(stemres[stemres.length - 1]) == Roman.C &&
                    Roman.typeOfLetterLat(stemres[stemres.length - 2]) == Roman.C
                ) {
                    res.removeAt(i)
                    i--
                }
                i++
            }
        }
        return if (res.size == 0) {
            null
        } else {
            res.map { ContextualResult(it.first, it.second, it.third) }.toTypedArray()
        }
    }

    //----------------------------------------

    //--------------------- CONTEXT VALIDATION -------------------------------

    /*
     * ------- NEUTRAL + NULL
     *
     * Avec une première action neutre et une deuxième action nulle, le
     * contexte sera respecté si les caractères finaux du radical
     * correspondent au contexte spécifié, i.e. une voyelle pour le contexte
     * V, ou les lettres t, k, q pour les contextes t, k, q. Comme l'action
     * est neutre, le résultat est unique: le radical tel quel et le suffixe
     * tel quel.
     *
     * Note: Il y a un cas où le contexte est nul: les terminaisons
     * démonstratives. On accepte tout simplement.
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class)
    private fun validate_neutral_null(
        context: String?, action1: Action, action2: Action, stemIn: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition
    ): Vector<Res> {
        val res = Vector<Res>()
        var stem = stemIn
        var stemEndChar = stem[stem.length - 1]
        val formFirstChar = affixCandidate[0]

        var typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)
        val typeOfFormFirstChar = Roman.typeOfLetterLat(formFirstChar)

        if (context == null) {
            res.add(Res(stem, stem, partOfComp))
        } else if (context == "V") {
            if (typeOfStemEndChar == Roman.V) {
                res.add(Res(stem, stem, partOfComp))
            }
        } else {
            // Treat 'ita' case first.
            val stemOrig = stem
            if (stemEndChar == 'i' && form.form.length > 1 && form.form.substring(0, 2) == "ta") {
                stem = stem + 't'
                stemEndChar = 't'
                typeOfStemEndChar = Roman.C
            }

            if (stemEndChar == context[0]) {
                res.add(Res(stem, stemOrig, partOfComp))
            }

            /*
             * What precedes makes for the final consonants q, t and k of
             * the stem as unmodified consonants. It is also possible that
             * the final consonant of the stem has been changed due to a
             * dialectal phonological phenomenon. The resulting consonant
             * could happen to be one of the context consonants (t,k,q), or
             * another one (s,n,m,...). What follows is to take care of
             * those possibilities.
             */

            // DIALECTALLY EQUIVALENT CONSONANT CLUSTERS
            if (typeOfStemEndChar == Roman.C && typeOfFormFirstChar == Roman.C) {
                val grs = Dialect.equivalentGroups(stemEndChar, formFirstChar)
                if (grs != null) {
                    for (i in grs.indices) {
                        stpw!!.check("validateContextActions -- NEUTRAL, checking equivalent groups")
                        if (grs[i][0] == context[0] && grs[i][1] == formFirstChar) {
                            res.add(Res(stem.substring(0, stem.length - 1) + context, stemOrig, partOfComp))
                            break
                        }
                    }
                }
            }

            /*
             * If the context is a consonant and if the stem ends with a
             * vowel, it might be that the contextual consonant is missing
             * because of Schneider's law. In that case, let's return the
             * suffixe with the modified stems.
             */
            if (typeOfStemEndChar == Roman.V && typeOfFormFirstChar == Roman.C) {
                val (doubleConsonants, _) = Dialect.schneiderStateAtEnd(stemOrig)
                if (doubleConsonants) {
                    res.add(Res(stemOrig + context, stemOrig, partOfComp))
                }
            }
        }
        /*
         * S'il y a une condition (cas de ji/1vv)
         */
        val cond = action1.getCondition()
        if (res.size != 0 && cond != null && cond.startsWith("id:")) {
            try {
                val avc = Imacond(cond).ParseCondition()
                affix.addPrecConstraint(avc)
            } catch (e: ParseException) {
                throw MorphologicalAnalyzerException(e)
            }
        }
        // else: Aucun suffixe ne mène ici.

        return res
    }

    /*
     * ------- NEUTRAL + DELETION
     */
    private fun validate_neutral_deletion(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val stemPenultEndChar = if (stem.length > 1) stem[stem.length - 2] else (-1).toChar()

        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)
        val typeOfStemPenultEndChar = Roman.typeOfLetterLat(stemPenultEndChar)

        if (context == "V") {
            if (typeOfStemEndChar == Roman.V && (typeOfStemPenultEndChar == -1 || typeOfStemPenultEndChar == Roman.C)) {
                res.add(Res(stem, stem, partOfComp)) // tel quel
                res.add(Res(stem + "a", stem, partOfComp))
                res.add(Res(stem + "i", stem, partOfComp))
                res.add(Res(stem + "u", stem, partOfComp))
            }
        } else {
            if (stem.length > 1) {
                val typeOfCharBeforeStemEndChar = Roman.typeOfLetterLat(stemPenultEndChar)
                if (typeOfStemEndChar == Roman.V && typeOfCharBeforeStemEndChar == Roman.V) {
                    res.add(Res(stem + context, stem, partOfComp))
                }
            }
        }

        return res
    }

    /*
     * ------- NEUTRAL + INSERTION
     */
    private fun validate_neutral_insertion(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)

        val isSyllabic = false
        val insert = Orthography.simplifiedOrthography(action2.getInsert()!!, isSyllabic)
        val linsert = insert.length
        val lstem = stem.length

        if (context == "V") {
            if (stem.endsWith(insert) && lstem > linsert + 2 &&
                Roman.typeOfLetterLat(stem[lstem - linsert - 1]) == Roman.V &&
                Roman.typeOfLetterLat(stem[lstem - linsert - 2]) == Roman.V
            ) {
                val npartOfComp = AffixPartOfComposition(posAffix - linsert, form)
                res.add(Res(stem.substring(0, lstem - linsert), stem.substring(0, lstem - linsert), npartOfComp))
            } else if (typeOfStemEndChar == Roman.V) {
                res.add(Res(stem, stem, partOfComp))
            }
        }

        return res
    }

    /*
     * ------- DELETION
     */
    private fun validate_deletion_null(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)

        if (context == "t" || context == "k" || context == "q") {
            if (typeOfStemEndChar == Roman.V) {
                res.add(Res(stem + context, stem, partOfComp))
            }
        }

        return res
    }

    /*
     * ------- DELETION ET INSERTION (simple overload, no checkPossibleDialectalChanges param)
     */
    private fun validate_deletion_insertion_simple(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition
    ): Vector<Res> {
        val res = Vector<Res>()

        val isSyllabic = false
        val carsInsere = Orthography.simplifiedOrthography(action1.getInsert()!!, isSyllabic)
        val linsert = carsInsere.length
        val lstem = stem.length

        if (stem.endsWith(carsInsere)) {
            val npartOfComp = AffixPartOfComposition(posAffix - linsert, form)
            res.add(Res(stem.substring(0, lstem - linsert) + context, stem.substring(0, lstem - linsert), npartOfComp))
        }

        return res
    }

    /*
     * ----- DELETION CONDITIONNELLE
     */
    @Throws(MorphologicalAnalyzerException::class)
    private fun validate_conditionaldeletion_null(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val formFirstChar = affixCandidate[0]

        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)
        val typeOfFormFirstChar = Roman.typeOfLetterLat(formFirstChar)

        if (((context == "t" || context == "k" || context == "q") && typeOfStemEndChar == Roman.V) ||
            (context == "V" && typeOfStemEndChar == Roman.C)
        ) {
            val cond = action1.getCondition()!!
            if (cond.startsWith("id:")) {
                try {
                    val avc = Imacond(cond).ParseCondition()
                    affix.addPrecConstraint(avc)
                } catch (e: ParseException) {
                    throw MorphologicalAnalyzerException(e)
                }
                if (context != "V") {
                    // Suppression de consonne
                    res.add(Res(stem + context, stem, partOfComp))
                } else {
                    // Suppression de voyelle
                    val formInCond = cond.substring(3, cond.indexOf("/"))
                    // 'form' devrait finir avec une voyelle (ex.: uti)
                    res.add(Res(stem + formInCond[formInCond.length - 1], stem, partOfComp))
                    /*
                     * Comme il y a eu suppression de voyelle, la dernière lettre du radical peut
                     * être une voyelle ou une consonne. Si c'est une consonne, et que le suffixe
                     * commence par une consonne, il est possible qu'il y ait eu un changement
                     * phonologique dialectal dans le groupe de consonnes.
                     */
                    if (typeOfStemEndChar == Roman.C && typeOfFormFirstChar == Roman.C && checkPossibleDialectalChanges) {
                        val grs = Dialect.equivalentGroups(stemEndChar, formFirstChar)
                        if (grs != null) {
                            for (i in grs.indices) {
                                stpw!!.check("validateContextActions -- CONDITIONALDELETION+NULLACTION, checking equivalent groups")
                                if (grs[i][1] == formFirstChar) {
                                    res.add(
                                        Res(
                                            stem.substring(0, stem.length - 1) + grs[i][0] + formInCond[formInCond.length - 1],
                                            stem, partOfComp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val pattern = Regex(action1.getCondition()!!)
                if (pattern.containsMatchIn(stem)) {
                    res.add(Res(stem + context, stem, partOfComp))
                }
            }
        }

        return res
    }

    /*
     * ----- VOICING
     */
    @Throws(MorphologicalAnalyzerException::class)
    private fun validate_voicing_null(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val formFirstChar = affixCandidate[0]

        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)
        val typeOfFormFirstChar = Roman.typeOfLetterLat(formFirstChar)

        if (context != "V") {
            val voicedCorrespondingChar = Roman.voicedOfOcclusiveUnvoicedLat(context!![0])
            if (stemEndChar == voicedCorrespondingChar) {
                res.add(Res(stem.substring(0, stem.length - 1) + context, stem, partOfComp))
            } else if (typeOfStemEndChar == Roman.V) {
                if (typeOfStemEndChar == Roman.V && typeOfFormFirstChar == Roman.C) {
                    val (doubleConsonants, _) = Dialect.schneiderStateAtEnd(stem)
                    if (doubleConsonants) {
                        res.add(Res(stem + context, stem, partOfComp))
                    }
                }
            } else if (checkPossibleDialectalChanges) {
                val grs = Dialect.equivalentGroups(stemEndChar, formFirstChar)
                if (grs != null) {
                    for (i in grs.indices) {
                        stpw!!.check("validateContextActions -- VOICING+NULLACTION, checking equivalent groups")
                        if (grs[i][0] == voicedCorrespondingChar && grs[i][1] == formFirstChar) {
                            res.add(Res(stem.substring(0, stem.length - 1) + context, stem, partOfComp))
                            break
                        }
                    }
                }
            }
        }

        return res
    }

    /*
     * ----- NASALIZATION
     */
    private fun validate_nasalization_null(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val formFirstChar = affixCandidate[0]

        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)
        val typeOfFormFirstChar = Roman.typeOfLetterLat(formFirstChar)

        if (context != "V") {
            val nasalCorrespondingChar = Roman.nasalOfOcclusiveUnvoicedLat(context!![0])
            if (stemEndChar == nasalCorrespondingChar) {
                res.add(Res(stem.substring(0, stem.length - 1) + context, stem, partOfComp))
            } else if (typeOfStemEndChar == Roman.V) {
                if (typeOfStemEndChar == Roman.V && typeOfFormFirstChar == Roman.C) {
                    val (doubleConsonants, _) = Dialect.schneiderStateAtEnd(stem)
                    if (doubleConsonants) {
                        res.add(Res(stem + context, stem, partOfComp))
                    }
                }
            } else if (checkPossibleDialectalChanges) {
                val grs = Dialect.equivalentGroups(stemEndChar, formFirstChar)
                if (grs != null) {
                    for (i in grs.indices) {
                        stpw!!.check("validateContextActions -- NASALISATION, checking equivalent groups")
                        if (grs[i][0] == nasalCorrespondingChar && grs[i][1] == formFirstChar) {
                            res.add(Res(stem.substring(0, stem.length - 1) + context, stem, partOfComp))
                            break
                        }
                    }
                }
            }
        }

        return res
    }

    /*
     * ------- NASALIZATION CONDITIONNELLE
     */
    @Throws(MorphologicalAnalyzerException::class)
    private fun validate_conditionalnasalization_null(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val formFirstChar = affixCandidate[0]

        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)
        val typeOfFormFirstChar = Roman.typeOfLetterLat(formFirstChar)

        val cond = action1.getCondition()!!
        val nasalCorrespondingChar = Roman.nasalOfOcclusiveUnvoicedLat(context!![0])
        if (stemEndChar == nasalCorrespondingChar) {
            try {
                val avc = Imacond(cond).ParseCondition()
                affix.addPrecConstraint(avc)
            } catch (e: ParseException) {
                throw MorphologicalAnalyzerException(e)
            }
            res.add(Res(stem.substring(0, stem.length - 1) + context, stem, partOfComp))
        } else if (typeOfStemEndChar == Roman.V) {
            if (typeOfStemEndChar == Roman.V && typeOfFormFirstChar == Roman.C) {
                val (doubleConsonants, _) = Dialect.schneiderStateAtEnd(stem)
                if (doubleConsonants) {
                    try {
                        val avc = Imacond(cond).ParseCondition()
                        affix.addPrecConstraint(avc)
                    } catch (e: ParseException) {
                        throw MorphologicalAnalyzerException(e)
                    }
                    res.add(Res(stem + context, stem, partOfComp))
                }
            }
        } else if (checkPossibleDialectalChanges) {
            val grs = Dialect.equivalentGroups(stemEndChar, formFirstChar)
            if (grs != null) {
                for (i in grs.indices) {
                    stpw!!.check("validateContextActions -- CONDITIONAL NASALIZATION, checking equivalent groups")
                    if (grs[i][0] == nasalCorrespondingChar && grs[i][1] == formFirstChar) {
                        try {
                            val avc = Imacond(cond).ParseCondition()
                            affix.addPrecConstraint(avc)
                        } catch (e: ParseException) {
                            throw MorphologicalAnalyzerException(e)
                        }
                        res.add(Res(stem.substring(0, stem.length - 1) + context, stem, partOfComp))
                        break
                    }
                }
            }
        }

        return res
    }

    /*
     * ------- INSERTION
     */
    private fun validate_insertion_null(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()

        if ((context == "V" && (stem.endsWith("a") || stem.endsWith("i") || stem.endsWith("u"))) ||
            (stem.endsWith(context!!))
        ) {
            res.add(Res(stem, stem, partOfComp))
        }

        return res
    }

    /*
     * ------- FUSION
     */
    private fun validate_fusion_null(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)

        if ((context == "t" || context == "k" || context == "q") && typeOfStemEndChar == Roman.V) {
            res.add(Res(stem + context, stem, partOfComp))
        }

        return res
    }

    /*
     * ------- ASSIMILATION
     */
    private fun validate_assimilation_null(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val formFirstChar = affixCandidate[0]

        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)
        val typeOfFormFirstChar = Roman.typeOfLetterLat(formFirstChar)

        if (typeOfStemEndChar == Roman.C && stemEndChar == form.form[0]) {
            res.add(Res(stem.substring(0, stem.length - 1) + context, stem, partOfComp))
        }
        /*
         * If the context is a consonant and if the stem ends with a vowel,
         * it might be that the contextual consonant is missing because of
         * Schneider's law. In that case, let's return the suffixe with the
         * modified stems.
         */
        if (typeOfStemEndChar == Roman.V && typeOfFormFirstChar == Roman.C) {
            val (doubleConsonants, _) = Dialect.schneiderStateAtEnd(stem)
            if (doubleConsonants) {
                res.add(Res(stem + context, stem, partOfComp))
            }
        }

        return res
    }

    /*
     * ------- SPECIFICASSIMILATION
     */
    private fun validate_specificassimilation_null(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]

        if (stemEndChar == action1.getAssimA()!![0]) {
            res.add(Res(stem.substring(0, stem.length - 1) + context, stem, partOfComp))
        }

        return res
    }

    /*
     * ------- DELETION + SPECIFICDELETION
     */
    private fun validate_deletion_specificdeletion(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)

        if (typeOfStemEndChar == Roman.V) {
            res.add(Res(stem + context, stem, partOfComp))
            if (action2.getCondition() != null) {
                val cond = action2.getCondition()!!
                val p = Regex(cond)
                if (p.containsMatchIn(stem)) {
                    res.add(Res(stem + action2.getSuppr() + context, stem, partOfComp))
                }
            } else {
                res.add(Res(stem + action2.getSuppr() + context, stem, partOfComp))
            }
        }

        return res
    }

    /*
     * ------- DELETION + INSERTION
     */
    private fun validate_deletion_insertion(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()

        val isSyllabic = false
        val insert = Orthography.simplifiedOrthography(action2.getInsert()!!, isSyllabic)
        val lstem = stem.length
        val linsert = insert.length
        val cntx: String = if (context == "V") "" else context!!

        if (stem.endsWith(insert) && lstem - linsert > 2 &&
            Roman.typeOfLetterLat(stem[lstem - linsert - 1]) == Roman.V &&
            Roman.typeOfLetterLat(stem[lstem - linsert - 2]) == Roman.V
        ) {
            if (Roman.typeOfLetterLat(stem[lstem - 1]) == Roman.V && Roman.typeOfLetterLat(stem[lstem - 2]) == Roman.C) {
                val npartOfComp = AffixPartOfComposition(posAffix - linsert, form)
                res.add(Res(stem.substring(0, lstem - linsert) + cntx, stem.substring(0, lstem - linsert), npartOfComp))
                res.add(Res(stem + cntx, stem, partOfComp))
            } else {
                val npartOfComp = AffixPartOfComposition(posAffix - linsert, form)
                res.add(Res(stem.substring(0, lstem - linsert) + cntx, stem.substring(0, lstem - linsert), npartOfComp))
            }
        } else if (lstem > 2 && Roman.typeOfLetterLat(stem[lstem - 1]) == Roman.V && Roman.typeOfLetterLat(stem[lstem - 2]) == Roman.C) {
            res.add(Res(stem + cntx, stem, partOfComp))
        }

        return res
    }

    /*
     * ------- VOWELLENGTHENING + ANNULATION
     */
    private fun validate_vowellengthening_cancellation(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)

        if (typeOfStemEndChar == Roman.V) {
            if (stem.length > 3 && stem[stem.length - 2] == stemEndChar) {
                val npartOfComp = AffixPartOfComposition(posAffix - 1, form)
                res.add(Res(stem.substring(0, stem.length - 1), stem.substring(0, stem.length - 1), npartOfComp))
                res.add(Res(stem, stem, partOfComp))
            } else if (stem.length > 3 && Roman.typeOfLetterLat(stem[stem.length - 2]) == Roman.V) {
                res.add(Res(stem, stem, partOfComp))
            }
        }

        return res
    }

    /*
     * ------- DELETIONVOWELLENGTHENING + ANNULATION
     */
    private fun validate_deletionvowellengthening_cancellation(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)

        if (typeOfStemEndChar == Roman.V) {
            if (stem.length > 3 && stem[stem.length - 2] == stemEndChar) {
                val npartOfComp = AffixPartOfComposition(posAffix - 1, form)
                res.add(Res(stem.substring(0, stem.length - 1) + context, stem.substring(0, stem.length - 1), npartOfComp))
                res.add(Res(stem + context, stem, partOfComp))
            } else if (stem.length > 3 && Roman.typeOfLetterLat(stem[stem.length - 2]) == Roman.V) {
                res.add(Res(stem + context, stem, partOfComp))
            }
        }

        return res
    }

    /*
     * ------- INSERTIONVOWELLENGTHENING
     */
    private fun validate_insertionvowellengthening_null(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]

        if (stemEndChar == action1.getInsert()!![0] && stem.length > 3 &&
            stem[stem.length - 2] == stemEndChar &&
            stem[stem.length - 3] == context!![0]
        ) {
            res.add(Res(stem.substring(0, stem.length - 2), stem.substring(0, stem.length - 2), partOfComp))
        }

        return res
    }

    /*
     * ------- NEUTRAL + DECAPITATION
     */
    private fun validate_neutral_selfdecapitation(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)

        if (typeOfStemEndChar == Roman.V) {
            if (stem.length > 1) {
                if (Roman.typeOfLetterLat(stem[stem.length - 2]) == Roman.V &&
                    form.form.length == form.getAffix()!!.morpheme!!.length - 1
                ) {
                    res.add(Res(stem, stem, partOfComp))
                } else if (Roman.typeOfLetterLat(stem[stem.length - 2]) == Roman.C &&
                    form.form.length == form.getAffix()!!.morpheme!!.length
                ) {
                    res.add(Res(stem, stem, partOfComp))
                }
            }
        }

        return res
    }

    /*
     * ------- DELETION + DECAPITATION
     */
    private fun validate_deletion_selfdecapitation(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)

        if (typeOfStemEndChar == Roman.V) {
            if (stem.length > 2) {
                if (Roman.typeOfLetterLat(stem[stem.length - 2]) == Roman.V &&
                    form.form.length == form.getAffix()!!.morpheme!!.length - 1
                ) {
                    res.add(Res(stem + context, stem, partOfComp))
                } else if (Roman.typeOfLetterLat(stem[stem.length - 2]) == Roman.C &&
                    form.form.length == form.getAffix()!!.morpheme!!.length
                ) {
                    res.add(Res(stem + context, stem, partOfComp))
                }
            }
        }

        return res
    }

    /*
     * ------- DELETION + DELETION
     */
    private fun validate_deletion_deletion(
        context: String?, action1: Action, action2: Action, stem: String, affixCandidate: String,
        form: SurfaceFormOfAffix, affix: Affix, posAffix: Int, partOfComp: AffixPartOfComposition,
        checkPossibleDialectalChanges: Boolean
    ): Vector<Res> {
        val res = Vector<Res>()
        val stemEndChar = stem[stem.length - 1]
        val typeOfStemEndChar = Roman.typeOfLetterLat(stemEndChar)

        // Puisque ce suffixe supprime la consonne précédente, le radical actuel
        // doit se terminer par une voyelle.
        if (typeOfStemEndChar == Roman.V) {
            res.add(Res(stem + context, stem, partOfComp))
            res.add(Res(stem + "a" + context, stem, partOfComp))
            res.add(Res(stem + "i" + context, stem, partOfComp))
            res.add(Res(stem + "u" + context, stem, partOfComp))
        }

        return res
    }

    //----------------------------------------

    class ContextualResult(
        @JvmField val stemBeforeAffixAction: String,
        @JvmField val stemAfterAffixAction: String,
        @JvmField val affixPartOfComposition: AffixPartOfComposition
    )
}
