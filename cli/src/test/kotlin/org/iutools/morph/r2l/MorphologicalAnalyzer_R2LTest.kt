package org.iutools.morph.r2l

import org.iutools.morph.MorphologicalAnalyzer
import org.iutools.morph.MorphologicalAnalyzerTest

class MorphologicalAnalyzer_R2LTest : MorphologicalAnalyzerTest() {
    override fun makeAnalyzer(): MorphologicalAnalyzer {
        return MorphologicalAnalyzer_R2L()
    }
}
