package org.iutools.morph

/*
 * MorphologicalAnalyzer_R2L's current Recall/Precision/reference-rank
 * expectations on the MorphAnalGoldStandard_WordsThatFailedBefore set.
 * GENERATED, not hand-curated -- run with
 * -Diutools.accuracy.dumpSnapshot=true and paste the output here wholesale;
 * do not hand-edit a single word.
 *
 * As of this generation: 3 words, recall 100.0%, precision 100.0% (both
 * exactly 100% by construction), reference decomp present anywhere 3/3
 * (100.0%), reference decomp in top-1 0/3 -- these are literally "words
 * that failed before," so a 0% top-1 rate here is the expected, unchanged
 * baseline, not a regression.
 */
class MorphAnalCurrentExpectations_WordsThatFailedBefore : MorphAnalCurrentExpectationsAbstract() {

    override fun initMorphAnalCurrentExpectations() {
        expect("angilligiaqtitsigunnaqpat", matched = 12, produced = 12, referenceRank = 6)
        expect("angilligiaqtittigunnaqpat", matched = 20, produced = 20, referenceRank = 13)
        expect("angilligiaqtunit", matched = 10, produced = 10, referenceRank = 5)
    }
}
