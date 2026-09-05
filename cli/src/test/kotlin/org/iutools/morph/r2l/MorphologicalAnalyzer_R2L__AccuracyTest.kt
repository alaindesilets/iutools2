package org.iutools.morph.r2l

import org.iutools.morph.MorphAnalGoldStandardAbstract
import org.iutools.morph.MorphAnalGoldStandard_Hansard_FromCsv
import org.iutools.morph.MorphAnalGoldStandard_WordsThatFailedBefore_FromCsv
import org.iutools.morph.MorphologicalAnalyzer
import org.iutools.morph.MorphologicalAnalyzer__AccuracyTest

class MorphologicalAnalyzer_R2L__AccuracyTest : MorphologicalAnalyzer__AccuracyTest() {
    override fun makeAnalyzer(): MorphologicalAnalyzer {
        return MorphologicalAnalyzer_R2L()
    }

    // Reads data/grammar/gold-standard/gold-standard.csv instead of the
    // hand-written addCase() calls -- see GoldStandardCsvMatchesKotlinTest
    // for the check that the two are equivalent.
    override fun makeHansardGoldStandard(): MorphAnalGoldStandardAbstract =
        MorphAnalGoldStandard_Hansard_FromCsv()

    override fun makeWordsThatFailedBeforeGoldStandard(): MorphAnalGoldStandardAbstract =
        MorphAnalGoldStandard_WordsThatFailedBefore_FromCsv()
}
