package org.iutools.morph

/**
 * Same cases as OBSOLETE_MorphAnalGoldStandard_Hansard, built from
 * data/grammar/gold-standard/gold-standard.csv instead of hand-written
 * addCase() calls. This is what MorphologicalAnalyzer_R2L__AccuracyTest
 * and MorphologicalAnalyzerTest actually run against; the OBSOLETE class
 * is kept only as the addCase()-based input export_gold_standard.py reads.
 */
class MorphAnalGoldStandard_Hansard_FromCsv : MorphAnalGoldStandardAbstract() {
    override fun initCases() {
        GoldStandardCsvReader.casesFor("hansard").forEach { addCase(it) }
    }

    override fun sourceName(): String = "hansard"
}
