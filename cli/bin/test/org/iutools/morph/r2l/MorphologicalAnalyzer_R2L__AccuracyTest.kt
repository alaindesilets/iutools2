package org.iutools.morph.r2l

import org.iutools.morph.MorphologicalAnalyzer
import org.iutools.morph.MorphologicalAnalyzer__AccuracyTest

class MorphologicalAnalyzer_R2L__AccuracyTest : MorphologicalAnalyzer__AccuracyTest() {
    override fun makeAnalyzer(): MorphologicalAnalyzer {
        return MorphologicalAnalyzer_R2L()
    }
}
