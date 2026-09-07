package org.iutools.morph

class MorphologicalAnalyzerException : Exception {
    constructor(e: Exception) : super(e)
    constructor(mess: String) : super(mess)
    constructor(mess: String, e: Exception) : super(mess, e)
}
