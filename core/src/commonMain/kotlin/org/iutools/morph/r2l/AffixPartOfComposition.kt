package org.iutools.morph.r2l

import org.iutools.linguisticdata.Affix
import org.iutools.linguisticdata.LinguisticDataException
import org.iutools.linguisticdata.Morpheme
import org.iutools.linguisticdata.SurfaceFormOfAffix
import org.iutools.linguisticdata.VerbEnding
import java.util.Vector

open class AffixPartOfComposition : PartOfComposition {

    @JvmField var form: SurfaceFormOfAffix? = null
    @JvmField var reflexive: Boolean = false
    private var tv: VerbEnding? = null
    private var multipleMorphparts: Array<AffixPartOfComposition>? = null

    constructor(posAffix: Int, f: SurfaceFormOfAffix?) {
        term = null
        position = posAffix
        form = f
    }

    // Seulement pour la sous-classe Inchoative
    protected constructor(pos: Int) {
        position = pos
    }

    fun getForm(): SurfaceFormOfAffix? = form

    fun getReflexive(): Boolean = reflexive

    @Throws(LinguisticDataException::class)
    override fun getMorpheme(): Morpheme? = form!!.getAffix()

    @Throws(LinguisticDataException::class)
    fun getAffix(): Affix? = form!!.getAffix()

    @Throws(LinguisticDataException::class)
    fun getType(): String? = form!!.getAffix()!!.type

    fun getVerbEnding(): VerbEnding? = tv

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("[AffixPartOfComposition: ")
        sb.append("\nterm= ")
        sb.append(term)
        sb.append("\nposition= ")
        sb.append(position)
        sb.append("\nform= ")
        if (form == null) sb.append("null") else sb.append(form.toString())
        sb.append("\nreflexive= ")
        sb.append(if (reflexive) "true" else "false")
        sb.append("\n]")
        return sb.toString()
    }

    @Throws(LinguisticDataException::class)
    fun toStr(): String {
        val aff = form!!.getAffix()!!
        val trm = if (!term.isNullOrEmpty() && term!![term!!.length - 1] == '*') {
            term!!.substring(0, term!!.length - 1)
        } else {
            term
        }
        return DecompositionState.DecompositionExpression.DecPart(trm, aff.id!!).str
    }

    /*
     * L'analyse d'un mot résulte souvent en une série de décompositions dont la
     * seule différence réside dans le dernier morceau, correspondant à différents
     * affixes de même type (typiquement terminaison nominale ou verbale) avec
     * une même forme de surface. Par exemple: 'mik' est la forme de surface de
     * 5 terminaisons nominales correspondant à des cas différents avec des
     * actions contextuelles différentes.
     *
     * On utilise 'multipleMorphparts' pour réduire le nombre de tableaux
     * d'affichage des résultats lors de la définition (décomposition) d'un mot.
     */
    fun setMultipleMorphparts(morphParts: Vector<AffixPartOfComposition>) {
        multipleMorphparts = morphParts.toTypedArray()
    }

    fun getMultipleMorphparts(): Array<AffixPartOfComposition>? = multipleMorphparts
}
