package org.iutools.linguisticdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * Port of org.iutools.linguisticdata.LinguisticObjectFactoryTest (Java).
 * The Java test called the package-private _makeBaseCompositionRoot()
 * directly; that method is `private` in the Kotlin LinguisticObjectFactory
 * object (not just package-scoped), so it's exercised here through the
 * public makeBase() entry point instead -- with a "compositionRoot" set and
 * no "variant", makeBase() returns [base, baseComp], matching the direct
 * call's inputs/outputs exactly.
 */
class LinguisticObjectFactoryTest {

    @Test
    fun makeBaseCompositionRoot() {
        // arviat,,1,n,place,p,arviaq,communauté de Eskimo Point,settlement of Eskimo Point,,arviq,.
        val linguisticDataMap = hashMapOf(
            "morpheme" to "arviat",
            "variant" to "",
            "nb" to "1",
            "type" to "n",
            "nature" to "place",
            "number" to "p",
            "compositionRoot" to "arviaq",
            "freMean" to "communauté de Eskimo Point",
            "engMean" to "settlement of Eskimo Point",
            "combination" to "",
            "root" to "arviq",
        )

        val objects = LinguisticObjectFactory.makeBase(linguisticDataMap)
        val base = objects[0]
        val baseComp = objects[1]

        assertEquals("arviat", base.morpheme, "'morpheme' of original base is incorrect.")
        assertEquals("arviaq", baseComp.morpheme, "'morpheme' of composition root is incorrect.")
        assertNull(baseComp.variant, "'variant' is incorrect.")
        assertNull(baseComp.compositionRoot, "'compositionRoot' is incorrect.")
        assertEquals("arviat/1n", baseComp.originalMorpheme, "'originalMorpheme' is incorrect.")
        assertEquals("nc", baseComp.subtype, "'subtype' is incorrect.")
        assertEquals("arviat/1n", baseComp.id, "'id' is incorrect.")
    }
}
