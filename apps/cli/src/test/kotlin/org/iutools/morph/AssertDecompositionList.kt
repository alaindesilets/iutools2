package org.iutools.morph

import kotlin.test.assertTrue
import kotlin.test.fail

/*
 * Port of org.iutools.morph.AssertDecompositionList (Java, ca.nrc.testing.Asserter
 * fluent-assertion style). The original chained onto a generic Asserter<T> base
 * class from the external ca.nrc.testing library, which isn't part of this port
 * -- reimplemented here as a plain Kotlin class holding just the assertions
 * MorphologicalAnalyzerTest actually needs.
 */
class AssertDecompositionList(
    private val decompositions: Array<Decomposition>,
    private val baseMessage: String = "",
) {
    fun decompositionStrings(): String = decompositions.joinToString("\n") { it.toString() }

    fun includesDecomps(vararg expDecomps: String): AssertDecompositionList {
        val gotDecomps = decompositionStrings()
        for (anExpDecomp in expDecomps) {
            if (!gotDecomps.contains(anExpDecomp)) {
                fail(
                    "$baseMessage\nDecomposition '$anExpDecomp' was missing.\n" +
                        "Decompositions were:\n$gotDecomps"
                )
            }
        }
        return this
    }

    fun includesAtLeastOneOfDecomps(vararg expDecompStrings: String): AssertDecompositionList {
        val gotDecompStrings = decompositionStrings()
        if (expDecompStrings.isEmpty()) {
            assertTrue(
                gotDecompStrings.isEmpty(),
                "${baseMessage}Decompositions should have been empty, but were:\n$gotDecompStrings\n"
            )
        } else {
            val found = expDecompStrings.any { gotDecompStrings.contains(it) }
            assertTrue(
                found,
                "$baseMessage\nDecompositions did not include any of the expected possibilities.\n" +
                    "Expected one of :\n   ${expDecompStrings.joinToString("\n   ")}\n" +
                    "Got             :\n   $gotDecompStrings\n"
            )
        }
        return this
    }

    fun allDecompsContain(expMorphNgramRegex: String): AssertDecompositionList {
        val regex = Regex(expMorphNgramRegex)
        decompositions.forEachIndexed { ii, decomp ->
            assertTrue(
                regex.containsMatchIn(decomp.toStr()),
                "$baseMessage${ii}th decomposition did not contain regexp '$expMorphNgramRegex'\n" +
                    "Decomposition was: ${decomp.toStr()}"
            )
        }
        return this
    }

    fun atLeastOneDecompContains(expMorphSequ: String): AssertDecompositionList {
        val allDecomps = decompositions.joinToString("\n") { it.toStr() }
        assertTrue(
            allDecomps.contains(expMorphSequ),
            "$baseMessage\nNone of the decompositions contained '$expMorphSequ'"
        )
        return this
    }

    fun producesAtLeastNDecomps(minDecomps: Int): AssertDecompositionList {
        assertTrue(
            decompositions.size >= minDecomps,
            "${baseMessage}Number of decompositions produced was too low: " +
                "got ${decompositions.size}, expected at least $minDecomps"
        )
        return this
    }
}
