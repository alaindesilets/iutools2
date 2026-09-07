package org.iutools.llm

import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * fillPlaceholder() stands in for String.format() (absent from commonMain)
 * for the app's one-slot translated strings. These pin the two slot forms
 * it has to handle and confirm it leaves everything else alone.
 */
class PlaceholdersTest {

    @Test
    fun fillPlaceholder_stringSlot_isReplacedWithTheValue() {
        assertEquals(
            "Can you suggest meanings for iglu?",
            fillPlaceholder("Can you suggest meanings for %1\$s?", "iglu"),
        )
    }

    @Test
    fun fillPlaceholder_numberSlot_isReplacedWithTheValuesTextForm() {
        assertEquals("Decomposition 2:", fillPlaceholder("Decomposition %1\$d:", 2))
    }

    @Test
    fun fillPlaceholder_noSlot_returnsTemplateUnchanged() {
        assertEquals("No definition found.", fillPlaceholder("No definition found.", "iglu"))
    }

    @Test
    fun fillPlaceholder_slotAppearsTwice_everyOccurrenceIsReplaced() {
        // The French/English resources don't currently do this, but a plain
        // replace handles it -- pin that so a future translation reusing the
        // word twice doesn't silently drop the second one.
        assertEquals(
            "iglu: examples for iglu",
            fillPlaceholder("%1\$s: examples for %1\$s", "iglu"),
        )
    }

    @Test
    fun fillPlaceholder_surroundingTextAndPunctuation_arePreserved() {
        assertEquals(
            "Bilingual examples for a shorter, related word than ᐃᒡᓗ:",
            fillPlaceholder("Bilingual examples for a shorter, related word than %1\$s:", "ᐃᒡᓗ"),
        )
    }
}
