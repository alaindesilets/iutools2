package org.iutools.script

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * DisplayScript's resolve()/displayForm()/displayFormBothScripts(): turning
 * the user's script preference into a concrete script and transcoding text
 * to it. Expected strings are computed with TransCoder here (never
 * hand-typed) so the test can't drift from the real transcoding.
 */
class DisplayScriptTest {

    @Test
    fun resolve_romanAndSyllabic_areAbsolute() {
        assertEquals(Script.ROMAN, DisplayScript.ROMAN.resolve(enteredScript = Script.SYLLABIC))
        assertEquals(Script.SYLLABIC, DisplayScript.SYLLABIC.resolve(enteredScript = Script.ROMAN))
    }

    @Test
    fun resolve_asEntered_followsTheScriptTheWordWasTypedIn() {
        assertEquals(Script.SYLLABIC, DisplayScript.AS_ENTERED.resolve(enteredScript = Script.SYLLABIC))
        assertEquals(Script.ROMAN, DisplayScript.AS_ENTERED.resolve(enteredScript = Script.ROMAN))
    }

    @Test
    fun displayForm_transcodesTextIntoTheResolvedScript() {
        val roman = "iglu"

        assertEquals(
            TransCoder.ensureScript(Script.SYLLABIC, roman),
            displayForm(roman, DisplayScript.SYLLABIC, enteredScript = Script.ROMAN),
        )
    }

    @Test
    fun displayForm_asEnteredRoman_leavesRomanTextAsRoman() {
        assertEquals("iglu", displayForm("iglu", DisplayScript.AS_ENTERED, enteredScript = Script.ROMAN))
    }

    @Test
    fun displayFormBothScripts_isPrimaryThenTheOtherScriptInParentheses() {
        val syllabic = TransCoder.ensureScript(Script.SYLLABIC, "iglu")
        val roman = TransCoder.ensureScript(Script.ROMAN, "iglu")

        val both = displayFormBothScripts("iglu", DisplayScript.SYLLABIC, enteredScript = Script.ROMAN)

        assertEquals("$syllabic ($roman)", both)
        assertTrue(both.startsWith(syllabic) && both.endsWith("($roman)"))
    }
}
