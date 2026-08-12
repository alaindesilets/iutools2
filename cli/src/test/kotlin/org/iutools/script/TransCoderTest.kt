package org.iutools.script

import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Port of org.iutools.script.TransCoderTest (Java), for the script-choice
 * subset ported in TransCoder.kt. Skipped:
 *  - test__TransCoder__Synopsis: a documentation-only test with no
 *    assertions in the original, nothing to port.
 *  - test__ensureScript__VariousCases (expects
 *    inOtherScript("juHaanaspuug") == "ᔤᕺ..."): the original
 *    never actually calls `.run()` on its RunOnCases runner, so this
 *    "expected" value was never verified against the original
 *    implementation either -- porting it would assert something upstream
 *    itself never confirmed. Roman.transcodeToSyllabics's handling of a
 *    bare 'H' (which this case exercises) is covered instead by the
 *    round-trip test below, which only relies on behavior this port
 *    actually implements.
 *  - test__ensureSyllabic__ListInput, and the Collection<String> paths in
 *    general: TransCoder.kt only ports the single-String API (see its
 *    header comment).
 *  - test__textScript__Mixed: exactly duplicates one case already covered
 *    by test__textScript__VariousCase below ("ᐃᓄ..." + " inuktut"
 *    -> MIXED).
 */
class TransCoderTest {

    @Test
    fun ensureScript_syllabicToRoman() {
        assertEquals("inuktut, 2020", TransCoder.ensureScript(Script.ROMAN, "ᐃᓄᒃᑐᑦ, 2020"))
    }

    @Test
    fun ensureScript_romanStaysRoman() {
        assertEquals("inuk", TransCoder.ensureScript(Script.ROMAN, "inuk"))
    }

    @Test
    fun ensureScript_romanToSyllabic() {
        assertEquals("ᐃᓄᒃᑐᑦ, 2020", TransCoder.ensureScript(Script.SYLLABIC, "inuktut, 2020"))
    }

    @Test
    fun ensureScript_romanStaysRoman_explicitRomanTarget() {
        assertEquals("inuktut, 2020", TransCoder.ensureScript(Script.ROMAN, "inuktut, 2020"))
    }

    @Test
    fun ensureScript_syllabicStaysSyllabic() {
        assertEquals("ᐃᓄᒃᑐᑦ, 2020", TransCoder.ensureScript(Script.SYLLABIC, "ᐃᓄᒃᑐᑦ, 2020"))
    }

    @Test
    fun ensureScript_mixedToRoman() {
        assertEquals("inuktut-1, inuktut-2", TransCoder.ensureScript(Script.ROMAN, "ᐃᓄᒃᑐᑦ-1, inuktut-2"))
    }

    @Test
    fun ensureScript_mixedToSyllabic() {
        assertEquals("ᐃᓄᒃᑐᑦ-1, ᐃᓄᒃᑐᑦ-2", TransCoder.ensureScript(Script.SYLLABIC, "ᐃᓄᒃᑐᑦ-1, inuktut-2"))
    }

    @Test
    fun ensureScript_doubleAmpersandWord() {
        assertEquals("arviarmii&&utik", TransCoder.ensureScript(Script.ROMAN, "ᐊᕐᕕᐊᕐᒦᖦᖢᑎᒃ"))
    }

    @Test
    fun ensureSameScriptAsSecond_firstSyllSecondRoman() {
        assertEquals("nunavut, 2020", TransCoder.ensureSameScriptAsSecond("ᓄᓇᕗᑦ, 2020", "inuktut, 2019"))
    }

    @Test
    fun ensureSameScriptAsSecond_firstRomanSecondSyll() {
        assertEquals("ᐃᓄᒃᑐᑦ, 2019", TransCoder.ensureSameScriptAsSecond("inuktut, 2019", "ᓄᓇᕗᑦ, 2020"))
    }

    @Test
    fun ensureSameScriptAsSecond_bothRoman() {
        assertEquals("inuktut, 2019", TransCoder.ensureSameScriptAsSecond("inuktut, 2019", "nunavut, 2020"))
    }

    @Test
    fun ensureSameScriptAsSecond_bothSyllabic() {
        assertEquals("ᐃᓄᒃᑐᑦ, 2019", TransCoder.ensureSameScriptAsSecond("ᐃᓄᒃᑐᑦ, 2019", "ᓄᓇᕗᑦ, 2020"))
    }

    @Test
    fun ensureSyllabic_romanWordWithB() {
        // 'b' is a common typo/alias for 'p' in Roman Inuktitut spelling.
        assertEquals("ᓱᑉᓗᐃᑦ", TransCoder.ensureSyllabic("subluit"))
    }

    @Test
    fun textScript_variousCases() {
        val cases = listOf(
            "nunavut" to Script.ROMAN,
            "Inuktut" to Script.ROMAN,
            "inuktut, 2020" to Script.ROMAN,
            "ᐃᓄᒃᑐᑦ" to Script.SYLLABIC,
            "Hakirviksaq" to Script.ROMAN,
            "hakirviksaq" to Script.ROMAN,
            "ᐃᓄᒃᑐᑦ inuktut" to Script.MIXED,
            "inukshuk" to Script.ROMAN,
            "juHaanaspuug" to Script.ROMAN,
            // 'ᕼ' here is a Syllabic character that merely *looks* like ASCII 'H'.
            "juᕼaanaspuug" to Script.MIXED,
            "Hᐃᓄᒃᑐᑦ" to Script.SYLLABIC,
            "ᐃᓄHᒃᑐᑦ" to Script.SYLLABIC,
            // Same 'ᕼ' look-alike note as above.
            "ᕼᐃᓄᒃᑐᑦ" to Script.SYLLABIC,
            "ᐃᓄᕼᒃᑐᑦ" to Script.SYLLABIC,
            "inuktitut“‘$()[]|ʼ´" to Script.ROMAN,
            // Cree syllabics -- not part of the Inuktitut syllabics table.
            "ᑐᓵᔨᑎᒢᒍᕈᓐᓃᖅᑐᖅ" to Script.MIXED,
        )
        for ((text, expected) in cases) {
            assertEquals(expected, TransCoder.textScript(text), "Wrong script for text: $text")
        }
    }

    @Test
    fun transcodeToSyllabics_roundTripsRealHansardWords() {
        // Independent correctness signal that doesn't rely on hand-typing
        // more syllabic expected values: real words from the Hansard gold
        // standard (MorphAnalGoldStandard_Hansard.kt) should come back
        // unchanged after Roman -> Syllabic -> Roman.
        val words = listOf(
            "agluukkaq", "aippaanik", "ajjaqsijii", "ajjigiinngittut", "akinginnut",
        )
        for (word in words) {
            val syllabic = Roman.transcodeToSyllabics(word)
            assertEquals(word, Syllabics.transcodeToRoman(syllabic), "Round trip failed for '$word'")
        }
    }

    @Test
    fun transcodeToSyllabics_bareHRoundTrips() {
        // Roman.transcodeToSyllabics has no special combining for a bare 'H'
        // (unlike Syllabics.transcodeToRoman's reverse direction, which does
        // recognize the Nunavik Ha/Hii/Hu/etc. series) -- ported faithfully
        // from the original Roman.java, which has the same asymmetry. This
        // checks the round trip stays self-consistent rather than asserting
        // a specific syllabic string, since the original's own test for this
        // exact word was never actually executed (see class header).
        val roman = "juHaanaspuug"
        val syllabic = Roman.transcodeToSyllabics(roman)
        assertEquals(roman.lowercase(), Syllabics.transcodeToRoman(syllabic).lowercase())
    }
}
