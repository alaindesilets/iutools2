package org.iutools.script

/*
 * Which script Inuktitut surface text (a word, a morpheme's forms, a
 * dictionary headword) is shown to the user in.
 *
 * ROMAN and SYLLABIC are absolute. AS_ENTERED means "follow whatever script
 * the word was originally typed in" -- callers pass that per-word script
 * alongside, and it falls back to Roman when unknown. This is a user
 * preference about presentation, but resolving it into a concrete script
 * and transcoding to it is plain logic with no UI dependency.
 */
enum class DisplayScript { ROMAN, SYLLABIC, AS_ENTERED }

/** The concrete [Script] this setting resolves to for a word that was
 *  entered in [enteredScript] (only consulted for AS_ENTERED). */
fun DisplayScript.resolve(enteredScript: Script): Script = when (this) {
    DisplayScript.ROMAN -> Script.ROMAN
    DisplayScript.SYLLABIC -> Script.SYLLABIC
    DisplayScript.AS_ENTERED -> if (enteredScript == Script.SYLLABIC) Script.SYLLABIC else Script.ROMAN
}

/** [text] transcoded into the script [script] resolves to for a word
 *  entered in [enteredScript]. */
fun displayForm(text: String, script: DisplayScript, enteredScript: Script): String =
    TransCoder.ensureScript(script.resolve(enteredScript), text)

/**
 * [text] in the chosen display script, followed by its transcoding into the
 * other script in parentheses -- e.g.
 * "ᐃᖅᑲᓇᐃᔭᕐᕕᓕᒫᑦ (iqqanaijarvilimaat)".
 */
fun displayFormBothScripts(text: String, script: DisplayScript, enteredScript: Script): String {
    val primaryScript = script.resolve(enteredScript)
    val primary = TransCoder.ensureScript(primaryScript, text)
    val other = TransCoder.ensureScript(TransCoder.otherScriptThan(primaryScript), text)
    return "$primary ($other)"
}
