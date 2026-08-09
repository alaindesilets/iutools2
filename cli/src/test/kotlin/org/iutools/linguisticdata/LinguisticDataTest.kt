package org.iutools.linguisticdata

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/*
 * Port of org.iutools.linguisticdata.LinguisticDataTest (Java).
 * test__getMorpheme__VariousCases used the ca.nrc.testing.RunOnCases
 * mini-framework for a single case -- not part of this port, inlined
 * directly since there's nothing to gain from a single-case runner.
 */
class LinguisticDataTest {

    @Test
    fun synopsis_documentationExample() {
        // The LinguisticData class contains information about the various
        // morphemes used in the Inuktitut language. You typically obtain it
        // as a singleton.
        val data = LinguisticData.getInstance()

        // You can get an array with the IDs of all morphemes in the database
        val allMorphemeIDs = data.allMorphemeIDs()

        // Given a morpheme ID, you can get information about it
        for (anID in allMorphemeIDs) {
            data.getMorpheme(anID)
        }
    }

    @Test
    fun getMorpheme_variousCases() {
        val morphID = "iqqanaijaq/1v"
        val gotMorpheme = LinguisticData.getInstance().getMorpheme(morphID)
        assertFalse(gotMorpheme == null, "Morpheme should NOT have been null")
    }

    @Test
    fun allMorphemeIDs_idsDoNotContainSpecialChars() {
        val badIDs = mutableSetOf<String>()
        val specialChars = "~`!@#\$%^*()+={}[]|\\:;\"'<>?".map { it.toString() }
        for (id in LinguisticData.getInstance().allMorphemeIDs()) {
            for (badChar in specialChars) {
                if (id.contains(badChar)) {
                    badIDs.add(id)
                }
            }
        }

        if (badIDs.isNotEmpty()) {
            fail("The following morpheme IDs contained forbidden characters\n$badIDs")
        }
    }
}
