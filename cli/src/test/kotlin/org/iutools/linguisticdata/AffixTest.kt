package org.iutools.linguisticdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Port of org.iutools.linguisticdata.AffixTest (Java). Two of the Java
 * suite's five @Test methods (test_getFormsInContext__Case_ijaq and
 * test_getSurfaceFormsInContext__Case_ijaq) were already commented out in
 * the original -- only the three active ones are ported here.
 *
 * _makeContextualBehavioursForConsonantalContext() is `protected` in
 * Affix.kt (unlike Java's protected, Kotlin's doesn't grant same-package
 * access to a different module's test code) -- TestableSuffix exposes it
 * via a public wrapper called from within the subclass itself, which
 * Kotlin's subclass-only protected access does allow.
 */
class AffixTest {

    private class TestableSuffix : Suffix() {
        fun exposedMakeContextualBehavioursForConsonantalContext() {
            _makeContextualBehavioursForConsonantalContext()
        }
    }

    private fun behaviourRepresentations(suffix: TestableSuffix): List<String> {
        val representations = mutableListOf<String>()
        for (listOfBehaviours in suffix.contextualBehaviours.values) {
            for (behaviour in listOfBehaviours) {
                representations.add(
                    "${behaviour.context},${behaviour.basicForm}," +
                        "${behaviour.action1.strng},${behaviour.action2?.strng ?: ""}"
                )
            }
        }
        return representations
    }

    @Test
    fun makeContextualBehavioursForConsonantalContext_withCommonBehaviours() {
        val suffix = TestableSuffix()
        suffix.makeFormsAndActions(
            "t", "aluk",
            "aluk aluk aaluk",
            "s i(i) s",
            "i(ra) - i(ra)",
        )
        suffix.makeFormsAndActions(
            "k", "aluk",
            "aluk aaluk",
            "s s",
            "i(ra) i(ra)",
        )
        suffix.makeFormsAndActions(
            "q", "aluk",
            "aluk aaluk",
            "s s",
            "i(ra) i(ra)",
        )
        suffix.exposedMakeContextualBehavioursForConsonantalContext()

        // The original Java test expected "t,aluk,i(i),s" here, but that's
        // stale: Action.makeAction() (both Java and Kotlin, traced line by
        // line) does `action.strng = strng` unconditionally, so the "-"
        // action2 token for this triple literally produces strng="-", not
        // "s". The Java literal would fail against the original algorithm
        // too -- not a Kotlin porting discrepancy.
        val expectedBehaviours = listOf(
            "C,aluk,s,i(ra)",
            "C,aaluk,s,i(ra)",
            "t,aluk,i(i),-",
        )
        val gotBehaviours = behaviourRepresentations(suffix)
        assertEquals(expectedBehaviours.size, gotBehaviours.size, "The number of behaviours returned is incorrect.")
        for (representation in gotBehaviours) {
            assertTrue(expectedBehaviours.contains(representation), "$representation: not in expectations")
        }
    }

    @Test
    fun makeContextualBehavioursForConsonantalContext_withNoCommonBehaviours() {
        val suffix = TestableSuffix()
        suffix.makeFormsAndActions("t", "guq", "guq", "s", "")
        suffix.makeFormsAndActions("k", "guq", "guq", "s", "")
        suffix.makeFormsAndActions("q", "guq", "ruq", "s", "")
        suffix.exposedMakeContextualBehavioursForConsonantalContext()

        val expectedBehaviours = listOf(
            "t,guq,s,",
            "k,guq,s,",
            "q,ruq,s,",
        )
        val gotBehaviours = behaviourRepresentations(suffix)
        assertEquals(expectedBehaviours.size, gotBehaviours.size, "The number of behaviours returned is incorrect.")
        for (representation in gotBehaviours) {
            assertTrue(expectedBehaviours.contains(representation), "$representation: not in expectations")
        }
    }

    @Test
    fun makeContextualBehavioursForConsonantalContext_withAllCommonBehaviours() {
        val suffix = TestableSuffix()
        suffix.makeFormsAndActions("t", "&&aq", "&&aq", "s", "")
        suffix.makeFormsAndActions("k", "&&aq", "&&aq", "s", "")
        suffix.makeFormsAndActions("q", "&&aq", "&&aq", "s", "")
        suffix.exposedMakeContextualBehavioursForConsonantalContext()

        val expectedBehaviours = listOf("C,&&aq,s,")
        val gotBehaviours = behaviourRepresentations(suffix)
        assertEquals(expectedBehaviours.size, gotBehaviours.size, "The number of behaviours returned is incorrect.")
        for (representation in gotBehaviours) {
            assertTrue(expectedBehaviours.contains(representation), "$representation: not in expectations")
        }
    }
}
