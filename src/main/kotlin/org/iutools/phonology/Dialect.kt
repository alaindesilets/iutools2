package org.iutools.phonology

import org.iutools.linguisticdata.LinguisticData
import org.iutools.linguisticdata.LinguisticDataException
import org.iutools.script.Orthography
import org.iutools.script.Roman
import org.iutools.utilities.StopWatch
import java.util.Vector
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

/*
 * Note: `possibleDialects()`/`containsNunavikChars()` from the original were
 * dropped — they depend on `script.TransCoder`, which is excluded (only used
 * by the web transcoding UI, not by decomposeWord()). Everything else in this
 * file is needed transitively by `dialectalSpellingVariations()` and
 * `newRootCandidates()`, both called from the R2L engine.
 */
object Dialect {

    private var stpw: StopWatch? = null

    /*
     * Il est à noter que les seuls cas où la seconde consonne du groupe change
     * ont '&' comme seconde consonne; et il y a 'ts > tt'.
     * Arctic Quebec:  & > s
     * South Baffin: & > t dans les groupes de consonnes (South East)
     *                         & > s dans les groupes de consonnes (South West)
     *                         & > l ou s entre voyelles (South East)
     *                         & > s entre voyelles (South West)
     * t& > ts (AQ)
     * t& > tt (SE)
     * k& > ss : k& > ks (AQ) > ss (AQ)
     * k& > ts : k& > ks (SW) > ts (SW)
     * k& > kt (SE)
     * q& > qs
     * q& > qt
     *
     * ts > tt
     */
    private val groups = arrayOf(
        arrayOf("bl", "ll"), arrayOf("bj", "jj"), arrayOf("bg", "gg"), arrayOf("bv", "vv"),
        arrayOf("pl", "ll"), arrayOf("pk", "kk"), arrayOf("pg", "gg"), arrayOf("pv", "vv"), arrayOf("pq", "qq"),
        arrayOf("ps", "ts"), arrayOf("ps", "ss"),
        arrayOf("pt", "tt"),
        arrayOf("mN", "NN"), arrayOf("mn", "nn"), arrayOf("mp", "pp"),
        arrayOf("tp", "pp"), arrayOf("tk", "kk"), arrayOf("tj", "jj"), arrayOf("ts", "ss"),
        arrayOf("ts", "tt"), arrayOf("t&", "ts"), arrayOf("t&", "tt"),
        arrayOf("&&", "tt"),
        arrayOf("lv", "vv"),
        arrayOf("nN", "NN"), arrayOf("nm", "mm"),
        arrayOf("kt", "tt"), arrayOf("ks", "ss"), arrayOf("kp", "pp"), arrayOf("kv", "vv"), arrayOf("ks", "ts"),
        arrayOf("k&", "ss"), arrayOf("k&", "ts"), arrayOf("k&", "kt"),
        arrayOf("gl", "ll"), arrayOf("gv", "vv"), arrayOf("gj", "jj"),
        arrayOf("Nm", "mm"), arrayOf("Nn", "nn"),
        arrayOf("qt", "tt"), arrayOf("q&", "r&"), arrayOf("qt", "rt"), arrayOf("ql", "rl"), arrayOf("qp", "rp"), arrayOf("qs", "rs"),
        arrayOf("q&", "qs"), arrayOf("q&", "qt"),
        arrayOf("rq", "qq")
    )
    private val groups2 = arrayOf(arrayOf("it", "is"))

    @JvmStatic
    fun getKeys(): Array<String> {
        val keys = arrayOfNulls<String>(groups.size * 2)
        for (i in groups.indices) {
            val j = i * 2
            keys[j] = groups[i][0]
            keys[j + 1] = groups[i][1]
        }
        @Suppress("UNCHECKED_CAST")
        return keys as Array<String>
    }

    /**
     * Returns groups of consonants equivalent to "l1l2".
     * For example, for "pp", return "mp","tp","kp"
     */
    @Throws(TimeoutException::class)
    @JvmStatic
    fun equivalentGroups(l1: Char, l2: Char): Vector<String>? {
        stpw!!.check("Dialect.equivalentGroups")
        val group = "" + l1 + l2
        val terms = Vector<String>()
        for (i in groups.indices) {
            stpw!!.check("Dialect.equivalentGroups loop")
            if (groups[i][0] == group) terms.add(groups[i][1])
            if (groups[i][1] == group) terms.add(groups[i][0])
        }
        return if (terms.size == 0) null else terms
    }

    /*
     * This method checks whether the initial of the candidate morpheme and the
     * final of the stem that precedes it form a cluster that has equivalent
     * clusters in other dialects. And it checks for internal equivalent
     * clusters.
     *
     * It also checks for Schneider's Law of double consonants.
     *
     * The results of each check are joined.
     */
    @Throws(TimeoutException::class, LinguisticDataException::class)
    @JvmStatic
    fun dialectalSpellingVariations(candidateMorpheme: String, stem: String): Vector<String> {
        stpw!!.check("dialectalSpellingVariations")
        val cands = Vector<String>()
        val candsAndChanges = mutableListOf<Pair<String, MutableList<PhonologicalTransformation>?>>()

        // 1. Get all possible variants of the candidate morpheme with initial consonant resulting
        //    from a possible assimilation of place with the final consonant of the stem
        getVariantsOfCandidateWithDifferentFirstConsonant(candidateMorpheme, stem, cands, candsAndChanges)

        // 2. For the original candidate and each variant of the candidate with a different first consonant
        //    found in the above part, check for internal equivalent clusters.
        getVariantsWithDifferentInternalConsonantClusters(candidateMorpheme, cands, candsAndChanges)

        // 3. Schneider's Law: there cannot be 2 consecutive clusters of 2 consonants.
        //    When there would be such a second group of 2 consonants, the first consonant is dropped.
        //    Check on the original morpheme and then on all variants found in the steps above.
        getVariantsForSchneidersLaw(candidateMorpheme, stem, cands, candsAndChanges)

        return cands
    }

    @Throws(TimeoutException::class, LinguisticDataException::class)
    private fun getVariantsForSchneidersLaw(
        candidateMorpheme: String,
        stem: String,
        cands: Vector<String>,
        candsAndChanges: MutableList<Pair<String, MutableList<PhonologicalTransformation>?>>
    ) {
        val schCands = _schneiderCandidates(stem, candidateMorpheme)
        schCands.remove(candidateMorpheme)
        for (i in cands.indices) {
            stpw!!.check("Dialect.getVariantsForSchneidersLaw")
            schCands.addAll(_schneiderCandidates(stem, cands[i]))
            schCands.remove(cands[i])
        }
        cands.addAll(schCands)
    }

    @Throws(TimeoutException::class)
    private fun getVariantsWithDifferentInternalConsonantClusters(
        candidateMorpheme: String,
        cands: Vector<String>,
        candsAndChanges: MutableList<Pair<String, MutableList<PhonologicalTransformation>?>>
    ) {
        val cands2 = Vector<String>()
        cands2.add(candidateMorpheme)
        cands2.addAll(cands)
        val candsAndChanges2 = mutableListOf<Pair<String, MutableList<PhonologicalTransformation>?>>()
        candsAndChanges2.add(Pair(candidateMorpheme, null))
        candsAndChanges2.addAll(candsAndChanges)
        for (m in cands2.indices) {
            stpw!!.check("Dialect.getVariantsWithDifferentInternalConsonantClusters")
            val candStr = cands2[m]
            val correspondingTerms = correspondingTermsEquivalentGroups(candStr)
            val correspTermsAndChanges = correspondingTermsEquivalentGroups(candStr, 0)
            if (correspondingTerms != null) {
                /*
                 * For each term, add the change to the initial consonant, if any.
                 */
                val ltf = candsAndChanges2[m].second
                if (ltf != null) {
                    val tf = ltf[0]
                    for (i in correspTermsAndChanges.indices) {
                        stpw!!.check("Dialect.getVariantsWithDifferentInternalConsonantClusters inner")
                        correspTermsAndChanges[i].second.add(tf)
                    }
                }
                for (n in correspondingTerms.indices) {
                    stpw!!.check("Dialect.getVariantsWithDifferentInternalConsonantClusters n")
                    val candN = correspondingTerms[n]
                    if (!cands.contains(candN)) {
                        cands.add(candN)
                        candsAndChanges.add(correspTermsAndChanges[n])
                    }
                }
            }
        }
        removeCandidateMorphemeFromLists(candidateMorpheme, cands, candsAndChanges)
    }

    @Throws(TimeoutException::class)
    private fun getVariantsOfCandidateWithDifferentFirstConsonant(
        candidateMorpheme: String,
        stem: String,
        cands: Vector<String>,
        candsAndChanges: MutableList<Pair<String, MutableList<PhonologicalTransformation>?>>
    ) {
        val finalOfStem = stem[stem.length - 1]
        val initialOfMorpheme = candidateMorpheme[0]
        val afterInitialOfMorpheme = candidateMorpheme.substring(1)
        if (Roman.typeOfLetterLat(initialOfMorpheme) == Roman.C) {
            val groupOrig = "" + finalOfStem + initialOfMorpheme
            val grps = equivalentGroups(groupOrig[0], groupOrig[1])
            if (grps != null) {
                for (j in grps.indices) {
                    stpw!!.check("Dialect.getVariantsOfCandidateWithDifferentFirstConsonant")
                    val groupj = grps[j]
                    val c = groupj[1]
                    // Replace the initial consonant of the candidate with the final consonant of the equivalent
                    // cluster, for each possible equivalent cluster.
                    val candStr = c + afterInitialOfMorpheme
                    if (!cands.contains(candStr)) {
                        cands.add(candStr)
                        val l = mutableListOf(PhonologicalTransformation(groupOrig, groupj, 0))
                        candsAndChanges.add(Pair(candStr, l))
                    }
                }
            }
        }
    }

    @Throws(TimeoutException::class)
    private fun removeCandidateMorphemeFromLists(
        candidateMorpheme: String,
        cands: Vector<String>,
        candsAndChanges: MutableList<Pair<String, MutableList<PhonologicalTransformation>?>>
    ) {
        var i = 0
        while (i < cands.size) {
            stpw!!.check("Dialect.removeCandidateMorphemeFromLists")
            if (cands[i] == candidateMorpheme) {
                cands.removeAt(i)
                candsAndChanges.removeAt(i)
                i--
            }
            i++
        }
    }

    @Throws(TimeoutException::class, LinguisticDataException::class)
    @JvmStatic
    fun newRootCandidates(rootICI: String): Vector<String>? {
        val cands = Vector<String>()

        /*
         * Check for internal equivalent clusters in the root and add the
         * corresponding terms to the candidates.
         */
        val correspondingTerms = correspondingTermsEquivalentGroups(rootICI)
        if (correspondingTerms != null) {
            for (n in correspondingTerms.indices) {
                val candN = Orthography.orthographyICILat(correspondingTerms[n])
                stpw!!.check("Dialect.newRootCandidates corresponding term $n")
                if (!cands.contains(candN)) cands.add(candN)
            }
        }
        if (cands.size == 0) {
            return null
        } else {
            cands.remove(rootICI)
        }

        // Schneider's Law
        val schCands = _schneiderCandidates(null, rootICI)
        for (i in cands.indices) {
            stpw!!.check("Dialect.newRootCandidates cands $i")
            schCands.addAll(_schneiderCandidates(null, cands[i]))
            schCands.remove(cands[i])
        }
        cands.addAll(schCands)
        return if (cands.size == 0) {
            null
        } else {
            cands.remove(rootICI)
            cands
        }
    }

    @Throws(TimeoutException::class)
    @JvmStatic
    fun schneiderStateAtEnd(stem: String?): Pair<Boolean, Int> {
        var doubleConsonants: Boolean
        val vcState: Int

        if (stem == null) {
            // For roots
            return Pair(false, Roman.V)
        }

        doubleConsonants = false
        // Check whether the stem's last group of consonants is single or double
        var i = stem.length - 1
        while (i > 0) {
            stpw!!.check("Dialect.schneiderStateAtEnd")
            if (Roman.isConsonant(stem[i])) {
                doubleConsonants = Roman.isConsonant(stem[i - 1])
                break
            }
            i--
        }
        // Set the vowel/consonant state at the end of the stem
        vcState = if (Roman.isConsonant(stem[stem.length - 1])) Roman.C else Roman.V
        return Pair(doubleConsonants, vcState)
    }

    /*
     * 'terme' est un morphème inuktitut en caractères latins
     * dans l'orthographe simplifiée.
     */
    @Throws(TimeoutException::class)
    @JvmStatic
    fun correspondingTermsEquivalentGroups(term: String): Vector<String>? {
        val terms = Vector<String>()
        var i = 0
        while (i < term.length - 1) {
            stpw!!.check("Dialect.correspondingTermsEquivalentGroups term=$term i=$i")
            val greqs = equivalentGroups(term[i], term[i + 1])
            val l3 = if (i == term.length - 2) (-1).toChar() else term[i + 2]
            val greqs2 = equivalentGroups2(term[i], term[i + 1], l3)
            val combinedGreqs: Vector<String>?
            if (greqs2 != null) {
                combinedGreqs = greqs ?: Vector()
                combinedGreqs.addAll(greqs2)
            } else {
                combinedGreqs = greqs
            }

            if (combinedGreqs != null) {
                combinedGreqs.add("" + term[i] + term[i + 1])
                val remains = correspondingTermsEquivalentGroups(term.substring(i + 2))!!
                for (j in combinedGreqs.indices) {
                    stpw!!.check("Dialect.correspondingTermsEquivalentGroups j=$j")
                    val termTemp = term.substring(0, i) + combinedGreqs[j]
                    if (remains.size > 0) {
                        for (k in remains.indices) {
                            stpw!!.check("Dialect.correspondingTermsEquivalentGroups k=$k")
                            terms.add(termTemp + remains[k])
                        }
                    } else {
                        terms.add(termTemp)
                    }
                }
                return terms
            }
            i++
        }
        if (i == term.length - 1) {
            terms.add(term)
        }
        return terms
    }

    @Throws(TimeoutException::class)
    @JvmStatic
    fun correspondingTermsEquivalentGroups(term: String, pos: Int): MutableList<Pair<String, MutableList<PhonologicalTransformation>>> {
        stpw!!.check("Dialect.correspondingTermsEquivalentGroups term=$term pos=$pos")
        val termsAndAlterations = mutableListOf<Pair<String, MutableList<PhonologicalTransformation>>>()
        var i = pos
        while (i < term.length - 1) {
            stpw!!.check("Dialect.correspondingTermsEquivalentGroups loop i=$i")
            val groupOfConsonants = "" + term[i] + term[i + 1]
            val greqs = equivalentGroups(term[i], term[i + 1])
            val l3 = if (i == term.length - 2) (-1).toChar() else term[i + 2]
            val greqs2 = equivalentGroups2(term[i], term[i + 1], l3)
            val combinedGreqs: Vector<String>?
            if (greqs2 != null) {
                combinedGreqs = greqs ?: Vector()
                combinedGreqs.addAll(greqs2)
            } else {
                combinedGreqs = greqs
            }

            if (combinedGreqs != null) {
                // Groupe de consonnes avec équivalents trouvé à 'pos'
                combinedGreqs.add(groupOfConsonants)

                // Traiter le reste du mot pour chercher d'autres groupes.
                val remainsAndAlterations = correspondingTermsEquivalentGroups(term, i + 2)

                /*
                 * Pour chacun des groupes de consonnes équivalents, former un mot
                 * avec chaque possibilité retournée pour le reste du mot.
                 */
                for (j in combinedGreqs.indices) {
                    stpw!!.check("Dialect.correspondingTermsEquivalentGroups j=$j")
                    val grp = combinedGreqs[j]
                    val termTemp = term.substring(pos, i) + grp
                    val tp = PhonologicalTransformation(groupOfConsonants, grp, i)
                    if (remainsAndAlterations.size > 0) {
                        for (k in remainsAndAlterations.indices) {
                            stpw!!.check("Dialect.correspondingTermsEquivalentGroups k=$k")
                            val newTerm = termTemp + remainsAndAlterations[k].first
                            val alterations = mutableListOf<PhonologicalTransformation>()
                            alterations.addAll(remainsAndAlterations[k].second)
                            alterations.add(0, tp)
                            termsAndAlterations.add(Pair(newTerm, alterations))
                        }
                    } else {
                        val alterations = mutableListOf(tp)
                        termsAndAlterations.add(Pair(termTemp, alterations))
                    }
                }
                return termsAndAlterations
            }
            i++
        }
        if (i == term.length - 1) {
            termsAndAlterations.add(Pair(term.substring(pos), mutableListOf()))
        }
        return termsAndAlterations
    }

    // Same thing as equivalentGroups, except that it has the
    // additional constraint that the group is followed by a vowel.
    // For example : isa returns ita
    // NOTE: this is a hack and it will have to be revised.
    @Throws(TimeoutException::class)
    private fun equivalentGroups2(l1: Char, l2: Char, l3: Char): Vector<String>? {
        val group = "" + l1 + l2
        val terms = Vector<String>()
        if (l3 != (-1).toChar() && Roman.typeOfLetterLat(l3) == Roman.V) {
            for (i in groups2.indices) {
                stpw!!.check("Dialect.equivalentGroups2")
                if (groups2[i][0] == group) terms.add(groups2[i][1])
                if (groups2[i][1] == group) terms.add(groups2[i][0])
            }
        }
        return if (terms.size == 0) null else terms
    }

    /*
     * Schneider's law applies in Nunavik, where a word cannot have two
     * consecutive consonant clusters. The initial consonant of the second cluster
     * is deleted. This method returns a number of possible words corresponding
     * to the 'candidate' word assuming that Schneider's law has been applied to it.
     */
    @Throws(TimeoutException::class, LinguisticDataException::class)
    @JvmStatic
    fun _schneiderCandidates(stem: String?, candidate: String): Vector<String> {
        return __schneiderCandidates(stem, candidate, '@')
    }

    @Throws(TimeoutException::class, LinguisticDataException::class)
    private fun __schneiderCandidates(stem: String?, candidate: String, mark: Char): Vector<String> {
        val markedCandidate = schneiderCandidatesToString(stem, candidate, mark)
        return __explode(markedCandidate)
    }

    @Throws(TimeoutException::class)
    @JvmStatic
    fun schneiderCandidatesToString(stem: String?, candidate: String, mark: Char): String {
        val candSimp = candidate
        val (doubleConsonants, vcState) = schneiderStateAtEnd(stem)
        /*
         * From the beginning of the candidate and forward, insert a mark
         * wherever a consonant is possibly missing according to Schneider's Law
         */
        return markCandidate(candSimp, vcState, doubleConsonants, mark)
    }

    @JvmStatic
    fun markCandidate(cand: String, vcState: Int, doubleConsonants: Boolean, mark: Char): String {
        var marked = StringBuilder()
        var str = cand
        if (doubleConsonants && vcState == 0) str = "XXa$str"
        val p = Pattern.compile("(([^aiu][^aiu][aiu][aiu]?)([^aiu][aiu]))")
        val m = p.matcher(str)
        var pos = 0
        while (m.find(pos)) {
            marked.append(str.substring(pos, m.start(1)))
            marked.append(m.group(2))
            marked.append(mark)
            marked.append(m.group(3))
            pos = m.end(1)
        }
        marked.append(str.substring(pos))
        return marked.toString().replaceFirst(Regex("^XXa"), "")
    }

    @Throws(TimeoutException::class, LinguisticDataException::class)
    private fun __explode(s: String): Vector<String> {
        return if (s.isEmpty()) Vector() else __explode2(s)
    }

    /*
     * Wherever there might be a deleted consonant, add a word with one of the
     * possible consonants at that place.
     */
    @Throws(TimeoutException::class, LinguisticDataException::class)
    private fun __explode2(s: String): Vector<String> {
        val a = Vector<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            stpw!!.check("Dialect.explode2 i=$i")
            if (s[i] == '@') {
                val grCons = LinguisticData.getInstance().getGroupsOfConsonants()[s[i + 1]]
                if (grCons != null) {
                    for (j in grCons.indices) {
                        stpw!!.check("Dialect.explode j=$j")
                        a.addAll(__explode(grCons[j] + s.substring(i + 2)))
                    }
                    break
                }
            } else {
                sb.append(s[i])
            }
            i++
        }
        if (a.size == 0) a.add("")
        val deb = sb.toString()
        for (i2 in a.indices) {
            stpw!!.check("Dialect.equivalentGroups2 i=$i2")
            a[i2] = deb + a[i2]
        }
        return a
    }

    @JvmStatic
    fun setStopWatch(_stpw: StopWatch) {
        stpw = _stpw
    }
}
