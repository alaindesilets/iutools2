package org.iutools.morph

/*
 * MorphologicalAnalyzer_FST's current outcomes on the
 * MorphAnalGoldStandard_WordsThatFailedBefore set -- the FST counterpart of
 * MorphAnalCurrentExpectations_WordsThatFailedBefore. GENERATED (see
 * MorphAnalCurrentExpectations_FST_Hansard's header). First-decomposition-
 * correct: 0/3.
 */
class MorphAnalCurrentExpectations_FST_WordsThatFailedBefore : MorphAnalCurrentExpectationsAbstract() {

    override fun initMorphAnalCurrentExpectations() {

        //
        // Correct analysis is produced, but not as the top alternative.
        //
        expectFailure("angilligiaqtunit", OutcomeType.CORRECT_NOT_FIRST)
        expectFailure("angilligiaqtittigunnaqpat", OutcomeType.CORRECT_NOT_FIRST)
        expectFailure("angilligiaqtitsigunnaqpat", OutcomeType.CORRECT_NOT_FIRST)

        //
        // Some analyses are produced, but none is the correct one: none.
        //

        //
        // No decomposition produced at all: none.
        //
    }
}
