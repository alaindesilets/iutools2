package org.iutools.morph.r2l

import org.apache.logging.log4j.LogManager
import org.iutools.lib.SimpleLruCache
import org.iutools.linguisticdata.Affix
import org.iutools.linguisticdata.Base
import org.iutools.linguisticdata.LinguisticData
import org.iutools.linguisticdata.LinguisticDataException
import org.iutools.linguisticdata.Lexicon
import org.iutools.linguisticdata.Morpheme
import org.iutools.linguisticdata.SurfaceFormOfAffix
import org.iutools.linguisticdata.constraints.Conditions
import org.iutools.morph.Decomposition
import org.iutools.morph.DecompositionException
import org.iutools.morph.MorphologicalAnalyzer
import org.iutools.morph.MorphologicalAnalyzerException
import org.iutools.morph.RankedDecomposition
import org.iutools.morph.r2l.Graph.State
import org.iutools.phonology.Dialect
import org.iutools.script.Orthography
import org.iutools.script.Roman
import org.iutools.script.Syllabics
import org.iutools.utilities.StopWatch
import org.iutools.utilities1.Util
import java.util.Hashtable
import java.util.Vector
import java.util.concurrent.TimeoutException

/**
 * Right-to-left implementation of the morphological analyser.
 *
 * This class decomposes an Inuktitut word into morphemes, starting from the
 * end of the word, and moving towards the beginning.
 */
class MorphologicalAnalyzer_R2L : MorphologicalAnalyzer() {

    private val traceLogger = LogManager.getLogger("MorphologicalAnalyzer_R2L.analyzeWithCandidateAffixes")

    private val arcsByMorpheme = Hashtable<String, Array<Graph.Arc>>()

    protected var _decompsSoFar: MutableSet<DecompositionState> = HashSet()

    /**
     * The MorphologicalAnalyzer will raise this exception when it has found enough analyses.
     */
    class MorphologicalAnalyzerDoneException : Exception()

    init {
        LinguisticData.getInstance()
    }

    /**
     * Main method for decomposing a word.
     *
     * @param word             word to be analyzed
     * @param extendedAnalysisIn when true, and if the word ends in a vowel, analyze also the words
     *                                 word+'t', word+'k', word+'q'
     * @return an array of Decomposition objects
     */
    @Throws(MorphologicalAnalyzerException::class, TimeoutException::class)
    override fun doDecompose(word: String, extendedAnalysisIn: Boolean?): Array<Decomposition> {
        val extendedAnalysis = extendedAnalysisIn ?: true

        val cachedDecomps = uncache(word, extendedAnalysis)
        if (cachedDecomps != null) {
            return cachedDecomps
        }

        val decomposeCompositeRootLocal = false // do not decompose composite root

        try {
            // Do the morphological analysis.
            val decStatesList = decomposeUntilTimeoutOrCompletion(word, extendedAnalysis, decomposeCompositeRootLocal)

            // A.
            // Eliminate decompositions that contain a sequence of suffixes
            // that corresponds to a composite suffix, in order to keep only
            // the decompositions with the composite suffix.
            var decStatesArray = DecompositionState.removeCombinedSuffixes(decStatesList.toTypedArray())

            // B. Eliminate duplicate decompositions.
            decStatesArray = DecompositionState.removeMultiples(decStatesArray) ?: decStatesArray

            // C.
            // Rank the decompositions with the ranking shared by every
            // analyzer (see MorphologicalAnalyzer.sortDecompositions).
            //  - weight 1 for the "final consonant may have been dropped"
            //    readings, 0 for strict ones -- the R2L equivalent of the
            //    FST's LENIENT weight, so both analyzers rank strict readings
            //    ahead of guessed ones the same way.
            //  - root length, then affix count: exactly what
            //    DecompositionState.compareTo did here before; the stable sort
            //    still breaks remaining ties by this search's discovery order.
            //  - the optional morpheme-frequency key is left OFF for R2L (it
            //    helps the FST, whose raw order is arbitrary, but hurts R2L --
            //    see that method's doc).
            // Root canonical length is taken straight from the same
            // stem.getRoot().morpheme compareTo() used.
            val decomps = sortDecompositions(
                decStatesArray.map {
                    RankedDecomposition(
                        it.toDecomposition(),
                        it.stem.getRoot().morpheme!!.length,
                        weight = if (it.assumedMissingFinalConsonant) 1f else 0f,
                    )
                }
            )
            cache(decomps, word, extendedAnalysis)

            return decomps
        } catch (e: LinguisticDataException) {
            throw MorphologicalAnalyzerException(e)
        } catch (e: MorphologicalAnalyzerException) {
            throw e
        } catch (e: DecompositionException) {
            throw MorphologicalAnalyzerException(e)
        }
    }

    /**
     * This method is called by doDecompose (see above).
     * The Inuktitut word can be in syllabics or in the Roman alphabet at this point.
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class)
    private fun decomposeUntilTimeoutOrCompletion(
        wordToBeAnalyzedIn: String,
        extendedAnalysis: Boolean,
        decomposeCompositeRoot: Boolean
    ): MutableList<DecompositionState> {
        var wordToBeAnalyzed = wordToBeAnalyzedIn
        this._decompsSoFar = HashSet()
        val decompsSoFar = mutableListOf<DecompositionState>()
        try {
            val decomps: MutableList<DecompositionState>
            if (Syllabics.containsInuktitut(wordToBeAnalyzed)) {
                wordToBeAnalyzed = Syllabics.transcodeToRoman(wordToBeAnalyzed)
            }
            wordToBeAnalyzed = Util.enMinuscule(wordToBeAnalyzed)
            wordToBeAnalyzed = wordToBeAnalyzed.replace(Regex("([iua])qk([iua])"), "$1qq$2") // to cope with error of transliteration

            if (wordToBeAnalyzed[wordToBeAnalyzed.length - 1] != 'n') {
                // if the word does not end in 'n', analyze it as is.
                decomps = _decompose(wordToBeAnalyzed, decomposeCompositeRoot, decompsSoFar)
                decompsSoFar.addAll(decomps)
                // if the flag is set for treating possibly missing consonant, do it.
                if (extendedAnalysis && Roman.typeOfLetterLat(wordToBeAnalyzed[wordToBeAnalyzed.length - 1]) == Roman.V) {
                    val otherDecomps = _decomposeForFinalConsonantPossiblyMissing(wordToBeAnalyzed, decomposeCompositeRoot)
                    decomps.addAll(otherDecomps)
                    decompsSoFar.addAll(otherDecomps)
                }
            } else {
                // replace 'n' by 't' and analyze.
                decomps = _decomposeForFinalN(wordToBeAnalyzed, decomposeCompositeRoot)
                decompsSoFar.addAll(decomps)
            }
        } catch (e: MorphologicalAnalyzerDoneException) {
            // If this exception is raised, it just means we stopped at some point where we had found enough decomps
            decompsSoFar.addAll(this._decompsSoFar)
        }

        return decompsSoFar
    }

    @Synchronized
    private fun cache(decs: Array<Decomposition>, word: String, extendedAnalysis: Boolean) {
        val key = cacheKeyFor(word, _stopAfterNDecomps, extendedAnalysis)
        decompsCache.put(key, decs)
    }

    @Synchronized
    private fun uncache(word: String, extendedAnalysis: Boolean): Array<Decomposition>? {
        val key = cacheKeyFor(word, _stopAfterNDecomps, extendedAnalysis)
        return decompsCache.getIfPresent(key)
    }

    /*
     * If the word ends with the consonant 'n', we could be in the presence of a nasalized 't'; this happens often.
     * Analyze the word with its final 'n' replaced by 't'.
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class, MorphologicalAnalyzerDoneException::class)
    private fun _decomposeForFinalN(aWord: String, decomposeCompositeRoot: Boolean): MutableList<DecompositionState> {
        val wordWithNReplaced = aWord.substring(0, aWord.length - 1) + "t"
        val newDecomps = _decompose(wordWithNReplaced, decomposeCompositeRoot)
        for (dec in newDecomps) {
            dec.stem.term = aWord
            val morphemesOfDecomposition = dec.morphParts
            if (morphemesOfDecomposition.isNotEmpty()) {
                val lastAffix = morphemesOfDecomposition[morphemesOfDecomposition.size - 1]
                val t = lastAffix.getTerm()!!
                lastAffix.setTerme(t.substring(0, t.length - 1) + "n")
            }
        }
        return newDecomps
    }

    /*
     * It is often seen that Inuktitut words are written without their final consonant.
     * If the word ends with a vowel, there could be missing a consonant.
     * Add '*' at the end of the word in place of a missing consonant and analyze the word.
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class, MorphologicalAnalyzerDoneException::class)
    private fun _decomposeForFinalConsonantPossiblyMissing(aWord: String, decomposeCompositeRoot: Boolean): MutableList<DecompositionState> {
        stpw!!.check("_decomposeForFinalConsonantPossiblyMissing -- upon entry, word=$aWord, decomposeCompositeRoot=$decomposeCompositeRoot")
        val decomps = _decompose("$aWord*", false)
        // These readings are only valid if you assume the typed word lost its
        // final k/p/q/t -- flag them so the shared ranking can rank them below
        // strict readings, the way the FST's LENIENT weight does. (A reading
        // also reachable strictly is kept from the strict pass instead, which
        // runs first: removeMultiples() drops the later duplicate.)
        for (dec in decomps) dec.assumedMissingFinalConsonant = true
        return decomps
    }

    /**
     * Decompositions are returned in the same order as they were found.
     * At this stage, they are not sorted.
     * Note: The argument decomposeCompositeRoot set to 'true' means
     * that roots known as composite in the database must be decomposed.
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class, MorphologicalAnalyzerDoneException::class)
    private fun _decompose(
        word: String,
        decomposeCompositeRoot: Boolean,
        decompsSoFar: MutableList<DecompositionState>?
    ): MutableList<DecompositionState> {
        val morphPartsInit = Vector<AffixPartOfComposition>()
        val state = Graph.initialState
        var decompositions: MutableList<DecompositionState>
        val preCond: Conditions? = null

        stpw = StopWatch(millisTimeout, "Decomposing word=$word").start()
        Dialect.setStopWatch(stpw!!)
        MorphAnalyzerValidation.setStopWatch(stpw!!)
        if (!timeoutActive) stpw!!.disactivate() // for debugging
        stpw!!.reset()

        arcsByMorpheme.clear()

        // Simplify spelling: replace 'nng' by 'NN' and 'ng' by 'N'.
        val simplifiedTerm = Orthography.simplifiedOrthography(word, USE_SYLLABICS)
        val transitivity: String? = null
        decompositions = __decompose_simplified_term__(
            simplifiedTerm, simplifiedTerm, simplifiedTerm,
            morphPartsInit, arrayOf(state), preCond, transitivity
        )

        decompsSoFar?.addAll(decompositions)

        return decompositions
    }

    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class, MorphologicalAnalyzerDoneException::class)
    private fun _decompose(word: String, decomposeCompositeRoot: Boolean): MutableList<DecompositionState> {
        return _decompose(word, decomposeCompositeRoot, null)
    }

    //========================== __decompose_simplified_term__ ====================================
    // This is where the morphological analysis is done. RECURSIVE.
    //=============================================================================================

    /**
     * Decomposition of an Inuktitut term.
     *
     * This method is called recursively with the remaining stem every time
     * a candidate affix has been validated. The first time it is called, 'term' is the original word,
     * the same as 'termOrig'. The next times, 'termOrig' is the stem remaining in front of the consumed
     * affix's characters, and 'term' is that stem with its end contextualized, i.e. rebuilt according to the
     * actions of the validated affixes. For example, when analyzing 'umiarjualiuq...' and 'liuq' is found a
     * valid affix, the remaining stem 'umiarjua' becomes the value of 'termOrig' and 'term' is set iteratively
     * to that value appended with a consonant since 'liuq' is known to delete the last consonant of the stem:
     * 'umiarjuat', 'umiarjuak' and 'umiarjuaq'. This is done such that the term analyzed presents itself as
     * if there had been no deletion, voicing or any other action, for the purpose of looking up the forms
     * in the database's list of forms.
     *
     * @param term the contextualized term to be analyzed, in simplified spelling; initially, the whole word
     * @param termOrig the real term to be analyzed, in simplified spelling.
     * @param word the original word being analyzed, in simplified spelling.
     * @param morphParts morphemes accepted so far
     * @param states the sequence of states so far
     * @param preConds conditions to be met by the (last morpheme of the) stem, i.e. next during the analysis
     * @param transitivity the state of the transitivity so far
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class, MorphologicalAnalyzerDoneException::class)
    private fun __decompose_simplified_term__(
        term: String,
        termOrig: String,
        word: String,
        morphParts: Vector<AffixPartOfComposition>,
        states: Array<Graph.State>,
        preConds: Conditions?,
        transitivity: String?
    ): MutableList<DecompositionState> {
        val logger = LogManager.getLogger("MorphologicalAnalyzer_R2L.__decompose_simplified_term__")
        stpw!!.check("__decompose_simplified_term__ -- Upon entry")

        val completeAnalysis = mutableListOf<DecompositionState>()

        // 1. Check if the term to be analyzed could be a root (simple or composite) in the database.
        //    If that is the case, add the resulting decomposition(s) to the list of all the resulting decompositions.
        val analysesAsRoot = analyzeAsRoot(term, termOrig, word, morphParts, states, preConds, transitivity)
        completeAnalysis.addAll(analysesAsRoot)
        logger.debug("analysesAsRoot: ${analysesAsRoot.size}")

        // 2. If the term could not be analyzed as a root, or if it could be analyzed as a root and the flag is set
        //    to further decompose a composite root into its parts, analyze the term as a sequence of morphemes.
        if (analysesAsRoot.size == 0 || decomposeCompositeRoot) {
            val analysesAsSequenceOfMorphemes = analyzeAsSequenceOfMorphemes(term, word, morphParts, states, preConds, transitivity)
            completeAnalysis.addAll(analysesAsSequenceOfMorphemes)
        }

        return completeAnalysis
    }

    /*
     * ===================== ANALYZE AS A SEQUENCE OF MORPHEMES ===========================
     * Starting at the last character of the term, go back 1 character at a time
     * until an affix is found. One looks up for affixes for the sequence of characters as
     * read from the term and for any variation of that sequence with equivalent groups of
     * consonants resulting from the assimilation of place (eg. kt<>tt, gv<>vv, etc.).
     *
     * When an affix candidate has been found, a branch is created and the analysis is
     * continued. If more than 1 affix candidates were found, other branches are
     * checked by analyzing the same remaining stem from these other candidates points
     * of view.
     *
     * When those branches have all been processed, the analysis continues by eating up
     * another character, as if affix(es) had not been found. This allows for all the
     * possibilities for combining characters in morphemes of different lengths. For example,
     * in a word containing 'lauqsima', 'sima' will be found first; in order to analyze
     * 'lauqsima', one has to continue on another branch as if 'sima' had not been found.
     *
     * The character counter is stopped at 2 because there exists no root of 2 characters
     * the last of which would be a consonant susceptible of being deleted by an affix.
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class, MorphologicalAnalyzerDoneException::class)
    private fun analyzeAsSequenceOfMorphemes(
        simplifiedTerm: String,
        word: String,
        morphParts: Vector<AffixPartOfComposition>,
        states: Array<State>,
        preCond: Conditions?,
        transitivity: String?
    ): MutableList<DecompositionState> {
        stpw!!.check("analyzeAsSequenceOfMorphemes -- Upon entry")
        val logger = LogManager.getLogger("MorphologicalAnalyzer_R2L.analyzeAsSequenceOfMorphemes")
        logger.debug("++++++simplifiedTerm= $simplifiedTerm")
        val completeAnalysis = mutableListOf<DecompositionState>()

        val positionAffixStart = simplifiedTerm.length - 1

        // from the end of the stem, backwards
        var positionAffix = positionAffixStart
        while (positionAffix > 1) {
            val affixCandidate = simplifiedTerm.substring(positionAffix)
            val remainingStem = simplifiedTerm.substring(0, positionAffix)
            stpw!!.check("analyzeAsSequenceOfMorphemes -- position: $positionAffix; affixCandidate: $affixCandidate; remainingStem: $remainingStem")

            val formsOfAffixForOriginalSpelling = lookForForms(affixCandidate, USE_SYLLABICS) // USE_SYLLABICS set to false

            // It is possible that a dialectal difference in pronunciation shows up at the junction of 2 morphemes.
            // It can affect the end of the first morpheme, the beginning of the second, or both. It is also possible
            // that some dialectal changes happen inside the candidate affix. All possibilities are considered, including
            // Schneider's law.
            val otherFormsOfAffixResultingFromAssimilationOfPlace = Vector<SurfaceFormOfAffix>()
            val dialectalSpellingVariationsOfAffix: Vector<String>
            try {
                dialectalSpellingVariationsOfAffix = Dialect.dialectalSpellingVariations(affixCandidate, remainingStem)
            } catch (e: LinguisticDataException) {
                throw MorphologicalAnalyzerException(e)
            }
            for (k in dialectalSpellingVariationsOfAffix.indices) {
                val formsOfAffixForAVariationSpelling = lookForForms(dialectalSpellingVariationsOfAffix[k], USE_SYLLABICS)
                otherFormsOfAffixResultingFromAssimilationOfPlace.addAll(formsOfAffixForAVariationSpelling)
            }

            /*
             * BRANCHING POINT
             * Analyze the remaining stem on the basis of each possible affix candidate.
             */

            // 1. Do the analysis on the basis of the candidates from the original string.
            val analysesOfOriginal = analyzeWithCandidateAffixes(
                formsOfAffixForOriginalSpelling, remainingStem, affixCandidate, states, preCond,
                transitivity, positionAffix, morphParts, word, true
            )
            completeAnalysis.addAll(analysesOfOriginal)

            // 2. Do the analysis on the basis of the candidates from the dialectal differences.
            val analysesOfDialectal = analyzeWithCandidateAffixes(
                otherFormsOfAffixResultingFromAssimilationOfPlace, remainingStem, affixCandidate, states, preCond,
                transitivity, positionAffix, morphParts, word, false
            )
            completeAnalysis.addAll(analysesOfDialectal)

            // Continue the analysis of the term by eating up another character backwards.
            positionAffix--
        }

        return completeAnalysis
    }

    /**
     * Look in the database for all the objects of the class SurfaceFormOfAffix associated with a string of
     * characters representing an affix. Those objects describe an affix in different contexts with the
     * contextual actions.
     */
    @Throws(MorphologicalAnalyzerException::class)
    fun lookForForms(term: String, syllabic: Boolean): Vector<SurfaceFormOfAffix> {
        val cons = if (syllabic) Lexicon.consonantsSyl else Lexicon.consonants
        var formsFound = Vector<SurfaceFormOfAffix>()
        if (term.endsWith("*")) {
            val termWithoutStar = term.substring(0, term.length - 1)
            for (con in cons) {
                val termWithConsonant = termWithoutStar + con
                val formsFoundForTermWithAddedConsonant: Vector<SurfaceFormOfAffix>
                try {
                    formsFoundForTermWithAddedConsonant = Lexicon.lookForForms(termWithConsonant, syllabic)
                } catch (e: LinguisticDataException) {
                    throw MorphologicalAnalyzerException(e)
                }
                formsFound.addAll(formsFoundForTermWithAddedConsonant)
            }
        } else {
            try {
                formsFound = Lexicon.lookForForms(term, syllabic)
            } catch (e: LinguisticDataException) {
                throw MorphologicalAnalyzerException(e)
            }
        }
        return formsFound
    }

    /**
     * Validate each possible form of affix and continue analyzing the remaining stem.
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class, MorphologicalAnalyzerDoneException::class)
    private fun analyzeWithCandidateAffixes(
        formsOfAffixFound: Vector<SurfaceFormOfAffix>,
        stem: String,
        affixCandidateOrig: String,
        states: Array<Graph.State>,
        preConds: Conditions?,
        transitivity: String?,
        positionAffix: Int,
        morphParts: Vector<AffixPartOfComposition>,
        word: String,
        notResultingFromDialectalPhonologicalTransformation: Boolean
    ): MutableList<DecompositionState> {
        val completeAnalysis = mutableListOf<DecompositionState>()

        val keyStateIDs = computeStateIDs(states)

        val contextualForms: List<SurfaceFormOfAffix> = formsOfAffixFound.toList()

        //---------------------------------------
        // For each contextual form of affix submitted:
        //---------------------------------------
        for (contextualForm in contextualForms) {

            stpw!!.check("analyzeWithCandidateAffixes -- contextualForm: ${contextualForm.form}")

            val affix: Affix
            try {
                affix = contextualForm.getAffix()!!.copyOf() as Affix
            } catch (e1: LinguisticDataException) {
                throw MorphologicalAnalyzerException(e1)
            }
            var arcsFollowed: Array<Graph.Arc>? = null
            var validStemAffixCombinationsInContext: Array<MorphAnalyzerValidation.ContextualResult>? = null

            var validate: Boolean
            try {
                arcsFollowed = arcsSuivis(affix, states, keyStateIDs)
                val conditionsMet = affix.meetsConditions(preConds, morphParts)
                validStemAffixCombinationsInContext = agreeWithContextAndActions(
                    affixCandidateOrig, affix, stem, positionAffix, contextualForm,
                    notResultingFromDialectalPhonologicalTransformation
                )
                val transitivityMet = affix.meetsTransitivityCondition(transitivity)
                val sameAffixAsNext = !sameAsNext(affix, morphParts)
                val samePositionResult = !samePosition(positionAffix, morphParts)
                validate = arcsFollowed != null && conditionsMet &&
                    validStemAffixCombinationsInContext != null && transitivityMet &&
                    sameAffixAsNext && samePositionResult
            } catch (e: LinguisticDataException) {
                throw MorphologicalAnalyzerException(e)
            }
            traceLogger.debug(
                "affixCandidateOrig=$affixCandidateOrig stem=$stem -> " +
                    "affix=${affix.id} context=${contextualForm.context} form=${contextualForm.form} " +
                    "validate=$validate reconstructedStems=" +
                    validStemAffixCombinationsInContext?.map {
                        "${it.stemBeforeAffixAction}/${it.stemAfterAffixAction}"
                    }
            )
            //--------------------- validation -------------------------------------------------------
            if (validate) {
                val nextPossibleStates = arrayOfNulls<Graph.State>(arcsFollowed!!.size)
                for (i in arcsFollowed.indices) {
                    val dest = arcsFollowed[i].getDestinationState()
                    nextPossibleStates[i] = dest.clone()
                }

                val newConditionsToBeMetByNextMorpheme = affix.getPrecCond()

                // TODO (from original): this is not done properly; has to be modified.
                val affixTransitivity = affix.getTransitivityConstraint()
                val newTransitivity: String? = if (transitivity == null || transitivity == "n") {
                    null
                } else if (affixTransitivity == null || affixTransitivity == "n") {
                    transitivity
                } else {
                    null
                }

                for (validStemAffixCombinationInContext in validStemAffixCombinationsInContext!!) {
                    stpw!!.check("decomposeByAffixes -- affixes respecting context and actions: ${validStemAffixCombinationInContext.stemBeforeAffixAction}")
                    @Suppress("UNCHECKED_CAST")
                    val newMorphparts = morphParts.clone() as Vector<AffixPartOfComposition>
                    val partIro = validStemAffixCombinationInContext.affixPartOfComposition
                    partIro.arcs = arcsFollowed
                    newMorphparts.add(0, partIro) // morceau ajouté
                    @Suppress("UNCHECKED_CAST")
                    val analyses = __decompose_simplified_term__(
                        validStemAffixCombinationInContext.stemBeforeAffixAction,
                        validStemAffixCombinationInContext.stemAfterAffixAction,
                        word,
                        newMorphparts,
                        nextPossibleStates as Array<Graph.State>,
                        newConditionsToBeMetByNextMorpheme,
                        newTransitivity
                    )
                    completeAnalysis.addAll(analyses)
                }
            } // if (validation)
        } // for

        return completeAnalysis
    }

    private fun computeStateIDs(states: Array<State>): String {
        var keyStateIDs = "0"
        for (state in states) keyStateIDs += "+${state.id}"
        return keyStateIDs
    }

    /*
     * ===================== ANALYZE AS A ROOT ===========================
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class, MorphologicalAnalyzerDoneException::class)
    private fun analyzeAsRoot(
        term: String,
        termOrig: String,
        word: String,
        morphParts: Vector<AffixPartOfComposition>,
        states: Array<Graph.State>,
        preConds: Conditions?,
        transitivity: String?
    ): Vector<DecompositionState> {
        val allAnalyses = Vector<DecompositionState>()

        val termICI = Orthography.orthographyICI(term, USE_SYLLABICS) // de-simplify the term (re: NN>nng; N>ng); needed to search the database
        var termOrigICI = Orthography.orthographyICI(termOrig, USE_SYLLABICS)

        /*
         * Enlever le '*' à la fin du terme, s'il s'y trouve à la suite d'une
         * tentative pour trouver des analyses au cas où la consonne finale du
         * mot à analyser aurait été omise.
         */
        if (termOrigICI.endsWith("*")) {
            termOrigICI = termOrigICI.substring(0, termOrigICI.length - 1)
        }

        /*
         * À ce point-ci, nous sommes au début du terme. À cause de la
         * récursivité au point de branchement, le terme en question sera ce qui
         * précède tout affixe trouvé. Cela ira donc du mot entier à la racine
         * réelle, en passant par plusieurs termes intermédiaires.
         *
         * On vérifie si cette partie initiale du mot est une racine connue.
         *
         * Chercher le TERME dans les racines.
         */
        var lexs: Vector<Morpheme>? = lookForBase(termICI, USE_SYLLABICS)
        /*
         * Il est possible qu'une différence de prononciation dialectale se
         * produise dans un groupe de consonnes à la frontière de deux suffixes.
         * Il faut vérifier si le suffixe trouvé précédemment commence par une
         * consonne et si le candidat racine finit par une consonne et si ce
         * groupe de deux consonnes correspond à un autre groupe de consonnes.
         *
         * On cherche aussi des groupes de consonnes équivalents à l'intérieur
         * de la racine candidate. Toutes les possibilités sont retenues.
         */
        val newRootCandidates: Vector<String>?
        try {
            newRootCandidates = Dialect.newRootCandidates(termICI)
        } catch (e: LinguisticDataException) {
            throw MorphologicalAnalyzerException(e)
        }
        if (newRootCandidates != null) {
            for (k in newRootCandidates.indices) {
                val tr = lookForBase(newRootCandidates[k], USE_SYLLABICS)
                if (tr != null) {
                    if (lexs == null) {
                        @Suppress("UNCHECKED_CAST")
                        lexs = tr.clone() as Vector<Morpheme>
                    } else {
                        lexs.addAll(tr)
                    }
                }
            }
        }
        val rootAnalyses = checkRoots(lexs, word, termOrigICI, morphParts, states, preConds, transitivity)

        allAnalyses.addAll(rootAnalyses)

        return allAnalyses
    }

    @Throws(MorphologicalAnalyzerException::class)
    fun lookForBase(termICI: String, isSyllabic: Boolean): Vector<Morpheme>? {
        var basesFound: Vector<Morpheme>?
        try {
            if (termICI.endsWith("*")) {
                val cons = if (isSyllabic) Lexicon.consonantsSyl else Lexicon.consonants
                basesFound = Vector()
                val termICIWithoutStar = termICI.substring(0, termICI.length - 1)
                for (con in cons) {
                    val termICIWithConsonant = termICIWithoutStar + con
                    val morphemesFoundForWordWithAddedConsonant = Lexicon.lookForBase(termICIWithConsonant, isSyllabic)
                    if (morphemesFoundForWordWithAddedConsonant != null) {
                        basesFound.addAll(morphemesFoundForWordWithAddedConsonant)
                    }
                }
                if (basesFound.size == 0) {
                    basesFound = null
                }
            } else {
                basesFound = Lexicon.lookForBase(termICI, isSyllabic)
            }
        } catch (e: LinguisticDataException) {
            throw MorphologicalAnalyzerException(e)
        }
        return basesFound
    }

    /**
     * Compute the root morphemes corresponding to the given lexemes.
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class, MorphologicalAnalyzerDoneException::class)
    private fun checkRoots(
        lexsIn: Vector<Morpheme>?,
        word: String,
        termOrigICI: String,
        morphParts: Vector<AffixPartOfComposition>,
        states: Array<Graph.State>,
        preConds: Conditions?,
        transitivity: String?
    ): Vector<DecompositionState> {
        val rootAnalyses = Vector<DecompositionState>()

        val lexs = lexsIn ?: Vector()

        //-------------------------------------------
        // Pour chaque base possible du vecteur lexs:----------------
        //-------------------------------------------
        for (ib in lexs.indices) {
            val root = lexs[ib] as Base

            stpw!!.check("checkRoots -- morpheme: ${root.morpheme}")

            val typeBase = root.type!![0]

            if (typeBase == '?') {
                /*
                 * Si la racine est inconnue, on ajoute simplement une nouvelle
                 * décomposition à la liste des décompositions. (Note: ceci
                 * n'est pas effectué puisqu'on a mis en commentaire plus haut
                 * le traitement des racines inconnues.)
                 */
                val res = DecompositionState(word, RootPartOfComposition(termOrigICI, root, transitivity, null), morphParts.toTypedArray())
                onNewDecompFound(res)
                rootAnalyses.add(res)
            } else {
                /*
                 * Si la racine est connue : vérifier la validité du candidat.
                 */
                val arcFollowed = checkValidityOfRoot(root, states, morphParts, preConds, transitivity)

                if (arcFollowed != null) {
                    /*
                     * Toutes les conditions ont été respectées. Créer une
                     * nouvelle décomposition avec cette racine et les morphParts
                     * trouvés jusqu'ici.
                     */
                    val arc = arcFollowed.copy()
                    val mr = RootPartOfComposition(termOrigICI, root, transitivity, arc)
                    val res = DecompositionState(word, mr, morphParts.toTypedArray())
                    onNewDecompFound(res)
                    rootAnalyses.add(res)
                }
            }
        }

        return rootAnalyses
    }

    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class)
    private fun checkValidityOfRoot(
        root: Morpheme,
        states: Array<Graph.State>,
        morphParts: Vector<AffixPartOfComposition>,
        preConds: Conditions?,
        transitivity: String?
    ): Graph.Arc? {
        /*
         * il faut vérifier si le type de la racine correspond à un arc à partir
         * de l'état actuel, et cet arc doit conduire à l'état final (aucun arc
         * partant de cet état final). En principe, il ne devrait y avoir qu'un
         * seul arc accepté puisqu'on est rendu à la racine et qu'une racine ne
         * peut prendre qu'un seul arc.
         */
        var accepted = false

        val keyStateIDs = computeStateIDs(states)

        var arcFollowed: Graph.Arc? = null
        val arcsFollowed = arcsSuivis(root, states, keyStateIDs)
        if (arcsFollowed != null) {
            arcFollowed = arcToZero(arcsFollowed)
            if (arcFollowed != null) {
                var preConditionsMet: Boolean
                try {
                    preConditionsMet = root.meetsConditions(preConds, morphParts)
                } catch (e: LinguisticDataException) {
                    throw MorphologicalAnalyzerException(e)
                }
                if (preConditionsMet) {
                    val postConds = root.getNextCond()
                    var postConditionsMet = true
                    if (morphParts.size != 0) {
                        try {
                            postConditionsMet = morphParts.firstElement().getAffix()!!.meetsConditions(postConds)
                        } catch (e: LinguisticDataException) {
                            throw MorphologicalAnalyzerException(e)
                        }
                    }
                    if (postConditionsMet) {
                        if (root.type == "v") {
                            val transitivityMet = root.meetsTransitivityCondition(transitivity)
                            if (transitivityMet) accepted = true
                        } else {
                            accepted = true
                        }
                    }
                }
            }
        }

        return if (accepted) arcFollowed else null
    }

    @Throws(TimeoutException::class)
    private fun arcToZero(arcsFollowed: Array<Graph.Arc>): Graph.Arc? {
        for (arc in arcsFollowed) {
            stpw!!.check("arcToZero -- arc: $arc")
            if (arc.getDestinationState() == Graph.finalState) {
                return arc
            }
        }
        return null
    }

    /*
     * Vérifier si ce suffixe est le même que le dernier suffixe trouvé
     * précédemment. Cela permet d'éliminer certaines analyses, entre autres,
     * celles qui retournent le suffixe "a" d'action de groupe deux fois
     * lorsqu'on a un double "a" dans le mot.
     */
    @Throws(MorphologicalAnalyzerException::class)
    private fun sameAsNext(morpheme: Morpheme, partsAlreadyAnalyzed: Vector<AffixPartOfComposition>): Boolean {
        var isSameAsNext = false
        try {
            if (partsAlreadyAnalyzed.size != 0) {
                val affPrec = partsAlreadyAnalyzed.elementAt(0).getAffix()
                if (morpheme.id == affPrec!!.id) {
                    isSameAsNext = true
                }
            }
        } catch (e: LinguisticDataException) {
            throw MorphologicalAnalyzerException(e)
        }
        return isSameAsNext
    }

    /*
     * Vérifier si ce suffixe est à la même position dans le mot que le
     * suffixe trouvé précédemment (celui qui le suit dans le mot dans l'analyse
     * courante). Cela permet d'éliminer certaines analyses, entre autres,
     * celles où le suffixe suivant, à cause de son action de suppression,
     * ajoute les caractères supprimés au radical, lesquels caractères sont
     * interprétés comme suffixe. Or un suffixe ne peut logiquement supprimer un
     * autre suffixe.
     */
    private fun samePosition(positionAffixInWord: Int, partsAlreadyAnalyzed: Vector<AffixPartOfComposition>): Boolean {
        var isAtSamePosition = false
        if (partsAlreadyAnalyzed.size != 0) {
            val nextMorphpart = partsAlreadyAnalyzed.elementAt(0)
            if (nextMorphpart.getPosition() == positionAffixInWord) {
                isAtSamePosition = true
            }
        }
        return isAtSamePosition
    }

    /*
     * Vérifier si ce suffixe est permis en ce moment. Il doit
     * correspondre à un des arcs partant de l'état actuel.
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class)
    private fun arcsSuivis(morpheme: Morpheme, states: Array<Graph.State>, keyStateIDs: String): Array<Graph.Arc>? {
        var arcsFollowed: Array<Graph.Arc>? = null
        try {
            val keyMorphemeStateIDs = "${morpheme.id}:$keyStateIDs"
            val arcsFollowedByHash = arcsByMorpheme[keyMorphemeStateIDs]
            if (arcsFollowedByHash == null) {
                val arcsFollowedV = Vector<Graph.Arc>()
                for (state in states) {
                    stpw!!.check("arcsSuivis --- morpheme: ${morpheme.morpheme}")
                    val arcs = state.verify(morpheme)
                    arcsFollowedV.addAll(arcs)
                }
                if (arcsFollowedV.size != 0) {
                    arcsFollowed = arcsFollowedV.toTypedArray()
                    arcsByMorpheme[keyMorphemeStateIDs] = arcsFollowed
                }
            } else {
                arcsFollowed = arcsFollowedByHash
            }
        } catch (e: LinguisticDataException) {
            throw MorphologicalAnalyzerException(e)
        }
        return arcsFollowed
    }

    /*
     * Vérifier si le contexte est respecté. La forme candidate
     * trouvée est associée à un contexte de radical et à des actions.
     * On vérifie si le radical et la forme de l'affixe correspondent à
     * ce contexte et à ces actions. Si c'est le cas, on retourne les
     * résultats possibles:
     *
     * a. radical sans les changements morphologiques causés par
     * l'affixe; b. un objet de classe MorceauAffixe contenant: 1. la
     * position de l'affixe dans le mot (la valeur de i); 2. un objet de
     * classe SurfaceFormOfAffix décrivant totalement l'affixe.
     */
    @Throws(TimeoutException::class, MorphologicalAnalyzerException::class)
    private fun agreeWithContextAndActions(
        affixCandidateOrig: String,
        affix: Affix,
        stem: String,
        positionAffixInWord: Int,
        form: SurfaceFormOfAffix,
        notResultingFromDialectalPhonologicalTransformation: Boolean
    ): Array<MorphAnalyzerValidation.ContextualResult>? {
        var checkStartOfConsonantsGroup = true
        /*
         * Si la forme du candidat affixe est le résultat de changements
         * phonologiques, et si ces changements impliquent la consonne initiale,
         * on ne vérifiera pas la possibilité de changements phonologiques parce
         * qu'on ne veut pas que le groupe de consonne soit vérifié à nouveau.
         */
        if (!notResultingFromDialectalPhonologicalTransformation) {
            if (Roman.isConsonant(form.form[0]) &&
                Roman.isConsonant(affixCandidateOrig[0]) &&
                form.form[0] != affixCandidateOrig[0]
            ) {
                checkStartOfConsonantsGroup = false
            }
        }
        val context = form.context
        val action1 = form.action1!!
        val action2 = form.action2!!
        return MorphAnalyzerValidation.validateContextActions(
            context, action1, action2, stem, positionAffixInWord, affix, form, false,
            checkStartOfConsonantsGroup, affixCandidateOrig
        )
    }

    companion object {
        private const val USE_SYLLABICS = false

        private val decompsCache = SimpleLruCache<String, Array<Decomposition>>(10000)

        @JvmStatic
        @JvmOverloads
        fun removeFromCache(word: String, maxDecomps: Int? = null, extendedAnalysesIn: Boolean? = null) {
            val extendedAnalyses = extendedAnalysesIn ?: false
            val key = cacheKeyFor(word, maxDecomps, extendedAnalyses)
            decompsCache.invalidate(key)
        }

        private fun cacheKeyFor(word: String, maxDecomps: Int?, extendedAnalyses: Boolean): String {
            var key = word
            if (maxDecomps != null) {
                key += "/max=$maxDecomps"
            }
            if (extendedAnalyses) {
                key += "/extended"
            }
            return key
        }
    }

    /**
     * Add decompositions to the list of decomps found so far.
     */
    @Throws(MorphologicalAnalyzerDoneException::class, LinguisticDataException::class)
    private fun onNewDecompFound(newDecomp: DecompositionState) {
        if (newDecomp.isComplete()) {
            _decompsSoFar.add(newDecomp)
        }
        val stopAfterNDecomps = _stopAfterNDecomps
        if (stopAfterNDecomps != null) {
            // _decompsSoFar's size is NOT the number of final decompositions
            // this word will end up with: DecompositionState has no
            // equals()/hashCode() override, so the HashSet never
            // deduplicates raw search candidates -- that only happens
            // afterwards, in doDecompose()'s removeCombinedSuffixes()/
            // removeMultiples() pipeline. Stopping on the raw count could
            // yield fewer final decompositions than requested (even fewer
            // than the word's true total), so we run that same pipeline
            // here to check the count it actually implies.
            val distinctSoFar = DecompositionState.removeMultiples(
                DecompositionState.removeCombinedSuffixes(_decompsSoFar.toTypedArray())
            )
            if ((distinctSoFar?.size ?: 0) >= stopAfterNDecomps) {
                // We are done.
                // Throw a MorphologicalAnalyzerDoneException so we stop processing
                // The exception will be caught at the level of doDecompose(), which will return
                // the list of decomps found so far.
                throw MorphologicalAnalyzerDoneException()
            }
        }
    }
}
