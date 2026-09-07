package org.iutools.morph

/*
 * MorphologicalAnalyzer_FST's current Recall/Precision/reference-rank
 * expectations on the MorphAnalGoldStandard_WordsThatFailedBefore set --
 * the FST counterpart of MorphAnalCurrentExpectations_WordsThatFailedBefore.
 * GENERATED, not hand-curated -- run with
 * -Diutools.accuracy.dumpSnapshot=true and paste the output here wholesale;
 * do not hand-edit a single word.
 *
 * As of this generation: 3 words, recall 37.8%, precision 21.6%, reference
 * decomp present anywhere 3/3 (100.0%), reference decomp in top-1 0/3 --
 * these are literally "words that failed before," so a 0% top-1 rate here
 * is the expected, unchanged baseline, not a regression.
 */
class MorphAnalCurrentExpectations_FST_WordsThatFailedBefore : MorphAnalCurrentExpectationsAbstract() {

    override fun initMorphAnalCurrentExpectations() {
        expect("angilligiaqtitsigunnaqpat", matched = 4, produced = 24, referenceRank = 8)
        expect("angilligiaqtittigunnaqpat", matched = 8, produced = 24, referenceRank = 8)
        expect("angilligiaqtunit", matched = 4, produced = 27, referenceRank = 9)
    }
}
