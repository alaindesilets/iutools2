package org.iutools.linguisticdata.constraints

/**
 * Simplified stand-in for the JavaCC-generated ParseException (which carried
 * detailed expected-token diagnostics we don't need for a hand-written parser).
 */
class ParseException(message: String = "Parse error") : Exception(message)
