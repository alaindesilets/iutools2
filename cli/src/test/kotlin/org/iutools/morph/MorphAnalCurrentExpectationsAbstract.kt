package org.iutools.morph

abstract class MorphAnalCurrentExpectationsAbstract {

    // IMPORTANT: The order of these matters. They go from least successful
    // to most successful (relied on by OutcomeType.compareTo()).
    enum class OutcomeType {
        NO_DECOMPS, CORRECT_NOT_PRESENT, CORRECT_NOT_FIRST, SUCCESS
    }

    protected abstract fun initMorphAnalCurrentExpectations()

    // We want to know about ANY change in the number of cases where the
    // correct decomp is NOT included in the analyser results (either
    // because no decomps were produced, or the decomps produced did not
    // contain the correct one).
    var tolerance_NO_DECOMPS: Double = 0.00
    var tolerance_CORRECT_NOT_PRESENT: Double = 0.0

    // For other performance criteria, we don't signal changes unless they
    // are "significant".
    var tolerance_CORRECT_NOT_FIRST: Double = -0.05

    var focusOnWord: String? = null

    val expFailures = mutableMapOf<String, OutcomeType>()

    init {
        initMorphAnalCurrentExpectations()
    }

    fun expectFailure(word: String, type: OutcomeType) {
        if (type == OutcomeType.SUCCESS) {
            throw MorphologicalAnalyzerException(
                "Outcome ${OutcomeType.SUCCESS} is not a type of failure"
            )
        }
        expFailures[word] = type
    }

    fun type4outcome(outcome: AnalysisOutcome, correctDecomps: Array<String>?): OutcomeType {
        if (outcome.decompositions.isEmpty()) {
            return OutcomeType.NO_DECOMPS
        }

        // The Decomp produces some decompositions.
        // What is the position of the correct one in that list?
        val rank = outcome.decompRank(correctDecomps)
        return when {
            rank == null -> OutcomeType.CORRECT_NOT_PRESENT
            rank > 0 -> OutcomeType.CORRECT_NOT_FIRST
            else -> OutcomeType.SUCCESS
        }
    }

    fun expectedOutcome(word: String): OutcomeType {
        return expFailures[word] ?: OutcomeType.SUCCESS
    }
}
