package org.iutools.morph

abstract class MorphAnalGoldStandardAbstract {

    val case4word = mutableMapOf<String, AnalyzerCase>()

    protected abstract fun initCases()

    init {
        initCases()
    }

    fun addCase(caseData: AnalyzerCase) {
        case4word[caseData.word] = caseData
    }

    fun allWords(): Set<String> = case4word.keys

    fun correctDecomps(word: String): Array<String>? = case4word[word]?.correctDecomps

    fun caseData(word: String): AnalyzerCase? = case4word[word]
}
