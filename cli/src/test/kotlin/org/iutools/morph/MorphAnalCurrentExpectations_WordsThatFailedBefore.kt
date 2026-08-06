package org.iutools.morph

class MorphAnalCurrentExpectations_WordsThatFailedBefore : MorphAnalCurrentExpectationsAbstract() {

    override fun initMorphAnalCurrentExpectations() {
        // Words that produce the correct analysis, but not as the top
        // alternative
        expectFailure("angilligiaqtunit", OutcomeType.CORRECT_NOT_FIRST)
        expectFailure("angilligiaqtitsigunnaqpat", OutcomeType.CORRECT_NOT_FIRST)
        expectFailure("angilligiaqtittigunnaqpat", OutcomeType.CORRECT_NOT_FIRST)

        // Words that produce some analyses, but none of them is the correct
        // one.
        // expectFailure("taaksumunga", OutcomeType.CORRECT_NOT_PRESENT);

        // Words that produce no decomposition at all
    }
}
