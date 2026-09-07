package org.iutools.morph

class DecompositionException : Exception {
    constructor(mess: String, e: Exception) : super(mess, e)
    constructor(e: Exception) : super(e)
    constructor(mess: String) : super(mess)
}
