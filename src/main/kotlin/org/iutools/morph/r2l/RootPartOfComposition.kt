package org.iutools.morph.r2l

import org.iutools.linguisticdata.Base
import org.iutools.linguisticdata.Morpheme

class RootPartOfComposition(t: String?, @JvmField val root: Base, @JvmField val transitivity: String?, arc: Graph.Arc?) : PartOfComposition() {

    init {
        term = t
        this.arc = arc
    }

    override fun getMorpheme(): Morpheme = root

    fun getRoot(): Base = root

    fun getTransitivity(): String? = transitivity

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("[RootPartOfComposition: ")
        sb.append(", term=")
        sb.append(term)
        sb.append(", root=")
        sb.append(root)
        sb.append(", transitivity=")
        sb.append(transitivity)
        sb.append("]")
        return sb.toString()
    }

    fun toStr(): String {
        return DecompositionState.DecompositionExpression.DecPart(term, root.id!!).str
    }
}
