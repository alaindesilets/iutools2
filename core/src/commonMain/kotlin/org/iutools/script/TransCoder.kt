package org.iutools.script

/*
 * Port of the original iutools TransCoder.java (by Benoit Farley), script-
 * choice subset only -- this is what the app's display-script setting
 * (Roman/Syllabic/as-entered) is built on, ported as a real utility rather
 * than a one-off helper because the original already solved this problem.
 *
 * Deliberately NOT ported (out of scope, see AGENTS.md's scoping
 * methodology):
 *  - Legacy pre-Unicode font conversion (`romanToLegacy`, `legacyToRoman`,
 *    `unicodeToLegacy`, `legacyToUnicode`, `macRoman2cp1252`,
 *    `windows1252Toiso88591`, `unistringToUnicode`, `unicodeToUnistring`,
 *    URL/HTML-entity helpers, the `main()` CLI entry point) -- the Inuit
 *    community has moved to Unicode, these exist only to support
 *    proprietary pre-Unicode Inuktitut fonts.
 *  - The `Collection<String>`/`Map<String, V>` overloads of
 *    `ensureRoman`/`ensureSyllabic`/`ensureScript`/`ensureKeysScript` --
 *    nothing in this app transcodes more than one word at a time, and the
 *    originals depend on an NRC-internal `Cloner` reflection utility this
 *    project doesn't have.
 *  - `CollectionTranscoder.java` (transcodes dictionary `Map` entries --
 *    dictionary/worddict is out of scope), `TranscoderApplet.java` and
 *    `script/exec/TranscodingWebApp*.java` (obsolete Java Applet / a
 *    standalone web app entry point -- the web/servlet layer is out of
 *    scope).
 *
 * `ensureScript`'s Java original throws a checked `TransCoderException` in
 * an "else" branch that's actually unreachable from any real call site
 * (target `script` is always ROMAN or SYLLABIC, both handled by the two
 * preceding branches) -- Kotlin has no checked exceptions and this project
 * prunes unreachable branches, so it becomes a same-text fallback instead
 * of porting the exception type.
 *
 * `textScript`'s punctuation/digit-stripping originally used Java regex's
 * embedded `(?U)` flag (equivalent to `Pattern.UNICODE_CHARACTER_CLASS`) so
 * `\W`/`\d` would classify by full Unicode character properties, not just
 * ASCII. That crashed the app on Android at runtime -- Android's regex
 * engine doesn't accept that embedded flag the way desktop-JVM's does, and
 * `:cli:test` alone couldn't catch this because it only ever runs on the
 * JVM target, never the Android one. Rewritten below as a plain
 * character-by-character filter using `Char.isLetter()` (kotlin-stdlib-
 * common, identical behavior on every Kotlin target) instead, avoiding
 * platform regex-engine differences entirely.
 */

enum class Script { SYLLABIC, ROMAN, MIXED }

object TransCoder {

    // Everything textScript() should treat as "not part of a word": actual
    // punctuation/digits/whitespace (non-letters), plus the specific
    // apostrophe-lookalike 'ʼ' (Unicode category Lm, technically a
    // "letter" but not one that should count for script detection), plus
    // ASCII h/H (see textScript's own comment for why).
    private fun isWordChar(c: Char): Boolean = c.isLetter() && c != 'ʼ' && c != 'h' && c != 'H'

    @JvmStatic
    fun otherScriptThan(script: Script): Script =
        if (script == Script.SYLLABIC) Script.ROMAN else Script.SYLLABIC

    @JvmStatic
    fun inOtherScript(text: String): String =
        ensureScript(otherScriptThan(textScript(text)), text)

    @JvmStatic
    @JvmOverloads
    fun ensureRoman(text: String, force: Boolean = false): String =
        if (force || Syllabics.syllabicCharsRatio(text) > 0.7) Syllabics.transcodeToRoman(text) else text

    @JvmStatic
    @JvmOverloads
    fun ensureSyllabic(text: String, force: Boolean = false): String =
        if (force || Syllabics.syllabicCharsRatio(text) < 0.7) Roman.transcodeToSyllabics(text) else text

    @JvmStatic
    @JvmOverloads
    fun ensureScript(script: Script, text: String, mixedMeansSyll: Boolean = false): String {
        val currScript = textScript(text, mixedMeansSyll)
        return when {
            script == currScript -> text
            script == Script.ROMAN -> Syllabics.transcodeToRoman(text)
            script == Script.SYLLABIC -> Roman.transcodeToSyllabics(text)
            else -> text
        }
    }

    @JvmStatic
    @JvmOverloads
    fun ensureSameScriptAsSecond(text: String, otherText: String, mixedMeansSyll: Boolean = false): String =
        ensureScript(textScript(otherText, mixedMeansSyll), text, mixedMeansSyll)

    @JvmStatic
    @JvmOverloads
    fun textScript(text: String, mixedMeansSyll: Boolean = false): Script {
        val textNoPunctDigitNorH = text.filter(::isWordChar)
        return when {
            Syllabics.allInuktitut(textNoPunctDigitNorH) -> Script.SYLLABIC
            Roman.allInuktitut(textNoPunctDigitNorH) -> Script.ROMAN
            mixedMeansSyll -> Script.SYLLABIC
            else -> Script.MIXED
        }
    }
}
