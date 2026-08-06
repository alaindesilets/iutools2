package org.iutools.linguisticdata

class MorphemeException : Exception {
    constructor(mess: String, e: Exception) : super(mess, e)
    constructor(mess: String) : super(mess)
    constructor(e: Exception) : super(e)
}
