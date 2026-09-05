package org.iutools.morph

/**
 * Same cases as MorphAnalGoldStandard_WordsThatFailedBefore, built from
 * data/grammar/gold-standard/gold-standard.csv instead of hand-written
 * addCase() calls. This is what MorphologicalAnalyzer_R2L__AccuracyTest
 * actually runs against; MorphAnalGoldStandard_WordsThatFailedBefore is
 * kept only as the addCase()-based input export_gold_standard.py reads.
 */
class MorphAnalGoldStandard_WordsThatFailedBefore_FromCsv : MorphAnalGoldStandardAbstract() {
    override fun initCases() {
        GoldStandardCsvReader.casesFor("words_that_failed_before").forEach { addCase(it) }
    }

    override fun sourceName(): String = "words_that_failed_before"
}
