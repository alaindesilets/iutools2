package org.iutools.morph.r2l

/*
 * The original StateGraphForward defined a whole second (forward-direction)
 * finite-state graph mirroring Graph.kt, with State/Arc classes, nextState(),
 * canBeFinal(), verify(), etc. Grepping the whole repo showed it's used only
 * by two callers: org.iutools.morph.Decomposition (which calls only
 * morphemeCanBeAtEndOfWord) and the excluded morph/l2r/MorphologicalAnalyzer_L2R
 * (the unused alternative analyzer). So only that one static method was ported.
 */
object StateGraphForward {

    @JvmStatic
    fun morphemeCanBeAtEndOfWord(morphemeId: String): Boolean {
        val partsMorphemeId = morphemeId.split("/")
        val id = partsMorphemeId[1]
        // TODO (from original): instead of this "hard coding", use data from states (final state)
        return id.matches(Regex("^\\d+[nv]?n$")) || id.matches(Regex("^t[nv].+")) || id.matches(Regex("^\\d+q$"))
    }
}
