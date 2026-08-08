package org.iutools.morph

class AnalyzerCase(val word: String, val correctDecomps: Array<String>?) {

    constructor(word: String) : this(word, null)

    var misspelled: Boolean = false
        private set
    var possiblyMisspelled: Boolean = false
        private set
    var borrowed: Boolean = false
        private set
    var decompUnknown: Boolean = false
        private set
    var properName: Boolean = false
        private set
    var caseComment: String? = null
        private set

    fun isMisspelled(): AnalyzerCase {
        misspelled = true
        return this
    }

    fun possiblyMisspelledWord(): AnalyzerCase {
        possiblyMisspelled = true
        return this
    }

    fun isBorrowedWord(): AnalyzerCase {
        borrowed = true
        return this
    }

    fun correctDecompUnknown(): AnalyzerCase {
        decompUnknown = true
        return this
    }

    fun isProperName(): AnalyzerCase {
        properName = true
        return this
    }

    fun comment(text: String): AnalyzerCase {
        caseComment = text
        return this
    }
}
