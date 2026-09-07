package org.iutools.utilities

class StopWatchException : Exception {
    constructor(mess: String, e: Exception) : super(mess, e)
    constructor(mess: String) : super(mess)
    constructor(e: Exception) : super(e)
}
