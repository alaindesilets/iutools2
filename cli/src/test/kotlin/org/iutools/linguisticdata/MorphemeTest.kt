package org.iutools.linguisticdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 * Port of org.iutools.linguisticdata.MorphemeTest (Java). Two of the Java
 * suite's six @Test methods -- test__Morpheme__Synopsis and
 * test__humanReadableDescription__HappyPath -- are not ported: both need
 * Morpheme.humanReadableDescription(), which delegates to
 * MorphemeHumanReadableDescr.java in the original. Grepping the original
 * repo shows that class is used exclusively by morphemedict/worddict/
 * webservice code (the morpheme dictionary web service), which is
 * explicitly out of scope for this port -- so it was correctly never
 * ported, and neither is its test coverage.
 */
class MorphemeTest {

    @Test
    fun hasCanonicalForm_happyPath() {
        val morpheme = "{inuk/1v}"
        assertTrue(
            Morpheme.hasCanonicalForm(morpheme, "inuk"),
            "Morpheme $morpheme should have had canonical form inuk"
        )
        assertFalse(
            Morpheme.hasCanonicalForm(morpheme, "it"),
            "Morpheme $morpheme should NOT have had canonical form it"
        )
    }

    @Test
    fun isComposite() {
        val lingData = LinguisticData.getInstance()
        var morph = lingData.getMorpheme("tit/1vv")
        assertFalse(morph!!.isComposite())
        morph = lingData.getMorpheme("ilinniaqtit/1v")
        assertTrue(morph!!.isComposite())
    }

    @Test
    fun canonicalForm_happyPath() {
        val morphID = "inuk/1n"
        val gotCanonical = Morpheme.canonicalForm(morphID)
        assertEquals("inuk", gotCanonical, "Wrong canonical form for morpheme $morphID")
    }

    @Test
    fun typeConstraints_variousCases() {
        val cases = listOf(
            Triple("inuk/1n", "%", "n"),
            Triple("it/tn-gen-p", "X", "%"),
            Triple("t/1vv", "v", "v"),
            Triple("aq/2nv", "n", "v"),
        )

        for ((morphID, expAttachesTo, expResultsIn) in cases) {
            val constraints = Morpheme.typeConstraints(morphID)
            assertEquals(expAttachesTo, constraints.first, "Wrong 'attachesTo' for morpheme $morphID")
            assertEquals(expResultsIn, constraints.second, "Wrong 'resultsIn' for morpheme $morphID")
        }
    }
}
