package org.iutools.morph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 * Unit tests for Decomposition -- currently just the `lenient` property,
 * added so an offline analyzed-lexicon dataset can record which readings a
 * word only has under the "a final consonant was dropped" assumption. The
 * bulk of Decomposition's behaviour is exercised indirectly through the
 * analyzer test suites and AssertDecompositionList.
 */
class DecompositionTest {

    @Test
    fun lenient_defaultsToFalse() {
        assertFalse(Decomposition("{iglu:iglu/1n}{mut:mut/tn-dat-s}").lenient)
    }

    @Test
    fun lenient_isCarriedFromConstructor() {
        assertTrue(Decomposition("{amma:angmaq/1v}{lu:luk/tv-imp-1d}", lenient = true).lenient)
        assertFalse(Decomposition("{ammalu:ammalu/1c}", lenient = false).lenient)
    }

    @Test
    fun lenient_doesNotAffectDecompSpecsOrToString() {
        val strict = Decomposition("{ammalu:ammalu/1c}", lenient = false)
        val guessed = Decomposition("{ammalu:ammalu/1c}", lenient = true)
        assertEquals(strict.decompSpecs, guessed.decompSpecs)
        assertEquals(strict.toString(), guessed.toString())
    }
}
