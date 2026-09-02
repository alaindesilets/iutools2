package org.iutools.morph.r2l

import org.apache.logging.log4j.LogManager
import org.iutools.linguisticdata.Base
import org.iutools.linguisticdata.LinguisticDataException
import org.iutools.linguisticdata.Morpheme
import org.iutools.linguisticdata.Morpheme.MorphFormat
import org.iutools.morph.Decomposition
import org.iutools.morph.DecompositionException
import org.iutools.script.Orthography
import java.util.Vector

/*
 * Note on scope: the original also had getMeaningsInArrayOfStrings/
 * getMeaningsInString/DecompositionExpression's parsing constructor+
 * getMeanings/expr2parts/toStringWithoutSurfaceForms (only used by the
 * excluded Gist.java dictionary-display feature), plus getSurfaceForms/
 * morphemeSurfaceForms/containsMorpheme/decompstr2morphemes/decomps2morphemes/
 * isEqualDecomposition/decompStrWithBraces/the Collection & array toString()
 * overloads — none of which are called from MorphologicalAnalyzer_R2L
 * (confirmed via grep). Dropped all of that; DecPart is kept only for its
 * (surface, id) constructor, which is what RootPartOfComposition.toStr() /
 * AffixPartOfComposition.toStr() actually use.
 */
class DecompositionState(
    val word: String,
    val stem: RootPartOfComposition,
    @JvmField var morphParts: Array<AffixPartOfComposition>
) : Comparable<DecompositionState> {

    // Set by MorphologicalAnalyzer_R2L when this decomposition comes from the
    // "a final k/p/q/t may have been dropped after a vowel" pass, i.e. it only
    // holds if the typed word is assumed to be missing its last letter. Lets
    // the shared ranking rank these below strict readings, as the FST's
    // LENIENT weight does. Not a search input -- purely a post-search tag.
    var assumedMissingFinalConsonant: Boolean = false

    init {
        var origState = stem.arc!!.startState!!.id
        var nextPos = word.length
        for (i in morphParts.indices.reversed()) {
            val m = morphParts[i]
            val pos = m.position
            // Desimplify, because morphological analysis is done on simplified orthography.
            val deSimplifiedTerm = Orthography.orthographyICI(word.substring(pos, nextPos), false)
            m.setTerme(deSimplifiedTerm)
            nextPos = pos
        }
        for (m in morphParts) {
            // 'arc' de chaque morceau
            for (arc in m.arcs!!) {
                if (arc.destState.id == origState) {
                    m.arc = arc
                    origState = m.arc!!.startState!!.id
                    break
                }
            }
        }
    }

    fun getRootMorphpart(): RootPartOfComposition = stem

    fun getMorphParts(): Array<AffixPartOfComposition> = morphParts

    fun setMorphParts(parts: Array<AffixPartOfComposition>) {
        morphParts = parts
    }

    fun getLastMorphpart(): AffixPartOfComposition? {
        return if (morphParts.isEmpty()) null else morphParts[morphParts.size - 1]
    }

    fun getNbMorphparts(): Int = morphParts.size

    // - Les racines connues en premier
    // - Les racines les plus longues
    // - Le nombre minimum de morphParts en premier
    //
    // No longer called directly: MorphologicalAnalyzer_R2L.doDecompose ranks
    // through the shared MorphologicalAnalyzer.sortDecompositions now, so that
    // the FST analyzer orders its output the same way. These same two keys
    // (root length, then morphPart count) are keys 2-3 of that shared sort --
    // kept here as their reference definition.
    override fun compareTo(other: DecompositionState): Int {
        var returnValue: Int
        val lengthOfRoot = stem.getRoot().morpheme!!.length
        val lengthOfRootOfOtherDec = other.stem.getRoot().morpheme!!.length
        returnValue = lengthOfRootOfOtherDec.compareTo(lengthOfRoot)
        if (returnValue == 0) {
            returnValue = morphParts.size.compareTo(other.morphParts.size)
        }
        return returnValue
    }

    @Throws(LinguisticDataException::class)
    fun toStr2(): String {
        val sb = StringBuilder()
        sb.append(stem.toStr())
        for (ma in morphParts) {
            sb.append(ma.toStr())
        }
        return sb.toString()
    }

    override fun toString(): String {
        var toStr: String? = null
        try {
            toStr = this.toStr2()
        } catch (e: LinguisticDataException) {
            e.printStackTrace()
        }
        return toStr ?: ""
    }

    @Throws(DecompositionException::class)
    fun toDecomposition(): Decomposition {
        val decompStr = formatDecompStr(toString(), MorphFormat.NO_BRACES)
        return Decomposition(decompStr)
    }

    fun isComplete(): Boolean = true // stem is non-null by construction in this Kotlin port

    companion object {
        @JvmStatic
        @JvmOverloads
        fun formatDecompStr(decompStrIn: String, formatIn: MorphFormat? = null): String {
            val format = formatIn ?: MorphFormat.WITH_BRACES
            // Remove leading and trailing spaces
            var decompStr = decompStrIn.replace(Regex("(^\\s+|\\s+$)"), "")
            // Replace multiple spaces by a single one
            decompStr = decompStr.replace(Regex("\\s+"), " ")
            if (format == MorphFormat.NO_BRACES) {
                decompStr = decompStr.replace(Regex("\\}\\s*\\{"), " ")
                decompStr = decompStr.replace(Regex("(^\\s*\\{\\s*|\\s*\\}\\s*$)"), "")
            } else {
                // Insert braces before and after single spaces
                decompStr = decompStr.replace(" ", "} {")
                decompStr = "{$decompStr}"
                decompStr = decompStr.replace("{{", "{")
                decompStr = decompStr.replace("}}", "}")
                // Ensure there is a space between braces
                decompStr = decompStr.replace("}{", "} {")
            }
            return decompStr
        }

        @JvmStatic
        @Throws(DecompositionException::class)
        fun toDecompositionArray(decStates: Array<DecompositionState>): Array<Decomposition> {
            return decStates.map { it.toDecomposition() }.toTypedArray()
        }

        @JvmStatic
        @Throws(LinguisticDataException::class)
        fun removeMultiples(decs: Array<DecompositionState>?): Array<DecompositionState>? {
            if (decs == null || decs.isEmpty()) return decs
            val v = mutableListOf<DecompositionState>()
            val vc = mutableListOf<String>()
            v.add(decs[0])
            vc.add(decs[0].toStr2())
            for (i in 1 until decs.size) {
                val c = decs[i].toStr2()
                if (!vc.contains(c)) {
                    v.add(decs[i])
                    vc.add(c)
                }
            }
            return v.toTypedArray()
        }

        // Éliminer les décompositions qui contiennent une suite de suffixes
        // pour laquelle il existe un suffixe composé, pour ne garder que
        // la décomposition dans laquelle se trouve le suffixe composé.
        // For example, apiqsuqtaujuksaq: there is a suffix -juksaq/vn which
        // is the combination of -juq/vn and -ksaq/nn. The analyzer will find
        // a decomposition with -juksaq but will also find a decomposition with
        // juq+ksaq; this is to remove the latter.
        @JvmStatic
        @Throws(LinguisticDataException::class)
        fun removeCombinedSuffixes(decs: Array<DecompositionState>): Array<DecompositionState> {
            val logger = LogManager.getLogger("DecompositionState.removeCombinedSuffixes")
            val decsAndKeepstatus: Array<Pair<DecompositionState, BooleanArray>> = decs.map {
                logger.debug("decs = ${it.toStr2()}")
                Pair(it, booleanArrayOf(true)) // keep-flag boxed in a 1-element array so it's mutable
            }.toTypedArray()

            for (i in decsAndKeepstatus.indices) {
                // Pendant l'exécution de cette boucle, certaines décompositions
                // plus loin dans la liste et pas encore traitées peuvent avoir été rejetées;
                // on ne considère que les décompositions qui n'ont pas encore été rejetées.
                if (decsAndKeepstatus[i].second[0]) {
                    val dec = decsAndKeepstatus[i].first
                    val affixesOfDecomposition = Vector(dec.morphParts.toList())

                    // Pour chaque affixe combiné, trouver celui qui le précède et
                    // celui qui le suit, et vérifier dans les autres
                    // décompositions retenues si ces deux affixes limites
                    // contiennent les éléments de l'affixe combiné. Si c'est
                    // le cas, on rejette ces décompositions.
                    for (indexOfProcessedVPart in affixesOfDecomposition.indices) {
                        val morph = affixesOfDecomposition[indexOfProcessedVPart].getMorpheme()
                        val cs = morph?.getCombiningParts()
                        // Seulement pour les affixes combinés.
                        if (cs != null) {
                            removeDecsWithCombinationAsSeparateElements(dec, affixesOfDecomposition, indexOfProcessedVPart, cs, decsAndKeepstatus)
                        }
                    }
                }
            }
            return decsAndKeepstatus.filter { it.second[0] }.map { it.first }.toTypedArray()
        }

        @Throws(LinguisticDataException::class)
        private fun removeDecsWithCombinationAsSeparateElements(
            dec: DecompositionState,
            vParts: Vector<AffixPartOfComposition>,
            indexOfProcessedVPart: Int,
            cs: Array<String>,
            decsAndKeepstatus: Array<Pair<DecompositionState, BooleanArray>>
        ) {
            // Trouver les décompositions qui ont les éléments du
            // suffixe combiné flanqués de part et d'autre par les
            // mêmes morphèmes, et les enlever de la liste.
            val prec: String? = when {
                indexOfProcessedVPart == 0 -> null
                indexOfProcessedVPart == 1 -> dec.stem.root.id
                else -> (vParts.elementAt(indexOfProcessedVPart - 1) as PartOfComposition).getMorpheme()!!.id
            }
            val follow: String? = if (indexOfProcessedVPart == vParts.size - 1) {
                null
            } else {
                (vParts.elementAt(indexOfProcessedVPart + 1) as PartOfComposition).getMorpheme()!!.id
            }
            // Vérifier dans les décompositions retenues.
            var k = 0
            while (k < decsAndKeepstatus.size) {
                // Décompositions retenues seulement.
                if (decsAndKeepstatus[k].second[0]) {
                    val deck = decsAndKeepstatus[k].first
                    val vPartsk = Vector<PartOfComposition>()
                    vPartsk.add(deck.stem)
                    vPartsk.addAll(deck.morphParts.toList())
                    var l = 0
                    var cont = true
                    var inCombined = false
                    var iCombined = 0
                    // Analyser chaque morphème de cette décomposition pour
                    // vérifier s'il correspond à un élément du morphème combiné.
                    while (l < vPartsk.size && cont) {
                        val morphk = vPartsk.elementAt(l).getMorpheme()
                        if (inCombined) {
                            // On a déjà déterminé qu'un ou plusieurs morphèmes
                            // correspondent aux éléments du morphème combiné.
                            // Vérifier celui-ci.
                            if (morphk!!.id == cs[iCombined]) {
                                // C'est aussi un élément du morphème combiné.
                                iCombined++
                                if (iCombined == cs.size) {
                                    // C'est le dernier élément du morphème
                                    // combiné. Vérifier si le morphème qui le
                                    // suit est le même que le morphème suivant
                                    // le morphème combiné. Si c'est le cas, on
                                    // rejette cette décomposition. De toute
                                    // façon, on arrête cette vérification.
                                    val followk: String? = if (l == vPartsk.size - 1) {
                                        null
                                    } else {
                                        vPartsk.elementAt(l + 1).getMorpheme()!!.id
                                    }
                                    if ((follow == null && followk == null) ||
                                        (follow != null && followk != null && followk == follow)
                                    ) {
                                        // *** REJETER CETTE DÉCOMPOSITION ***
                                        decsAndKeepstatus[k].second[0] = false
                                    }
                                    cont = false
                                }
                            } else {
                                // Ce n'est pas un élément du morphème combiné. On remet à 0.
                                inCombined = false
                                iCombined = 0
                            }
                        } else {
                            // On n'a pas encore reconnu un morphème comme
                            // premier élément du morphème combiné. Est-ce que
                            // celui-ci l'est?
                            if (morphk!!.id == cs[iCombined]) {
                                // Premier élément du morphème combiné.
                                inCombined = true
                                iCombined++
                                // Vérifier si le morphème qui le précède est le
                                // même que le morphème qui précède le morphème
                                // combiné. Si c'est le cas, on continue la
                                // vérification. Sinon on arrête la vérification
                                // de cette décomposition.
                                val preck: String? = when {
                                    l == 0 -> null
                                    l == 1 -> deck.stem.root.id
                                    else -> vPartsk.elementAt(l - 1).getMorpheme()!!.id
                                }
                                if ((preck == null && prec != null) ||
                                    (preck != null && prec == null) ||
                                    (preck != null && prec != null && preck != prec)
                                ) {
                                    cont = false
                                }
                            }
                        }
                        l++
                    }
                }
                k++
            }
        }

        const val startDelimitor = "{"
        const val endDelimitor = "}"
        const val interDelimitor = ":"
    }

    /*
     * {<forme de surface>:<signature du morphème>}{...}...
     */
    object DecompositionExpression {
        class DecPart(val surface: String?, val morphid: String) {
            val str: String = startDelimitor + (surface ?: "") + interDelimitor + morphid + endDelimitor
        }
    }
}
