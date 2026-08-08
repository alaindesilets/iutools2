package org.iutools.morph

class AnalysisOutcome {
    var timedOut: Boolean = false
    var decompositions: Array<Decomposition> = emptyArray()

    fun includesAtLeastOneOfDecomps(decomps: Array<String>?): Boolean {
        return decompRank(decomps) != null
    }

    fun decompRank(correctDecomps: Array<String>?): Int? {
        if (correctDecomps == null) return null
        for (ii in decompositions.indices) {
            if (correctDecomps.contains(decompositions[ii].toString())) {
                return ii
            }
        }
        return null
    }

    fun joinDecomps(): String {
        return decompositions.joinToString("\n") { it.toString() }
    }
}
