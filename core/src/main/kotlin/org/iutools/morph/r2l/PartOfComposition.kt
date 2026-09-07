package org.iutools.morph.r2l

import org.iutools.linguisticdata.LinguisticDataException
import org.iutools.linguisticdata.Morpheme

// PartOfComposition (Morceau): term / position / niveau
// RootPartOfComposition (MorceauRacine): the root Base
// AffixPartOfComposition (MorceauAffixe): SurfaceFormOfAffix form, reflexive, VerbEnding tv
abstract class PartOfComposition {

    @JvmField var term: String? = null
    @JvmField var position: Int = 0
    @JvmField var arc: Graph.Arc? = null
    @JvmField var arcs: Array<Graph.Arc>? = null

    fun setTerme(term: String?) {
        this.term = term
    }

    fun getTerm(): String? = term

    fun getPosition(): Int = position

    fun getArcs(): Array<Graph.Arc>? = arcs

    fun setArcs(arcs: Array<Graph.Arc>?) {
        this.arcs = arcs
    }

    fun getArc(): Graph.Arc? = arc

    @Throws(LinguisticDataException::class)
    abstract fun getMorpheme(): Morpheme?
}
