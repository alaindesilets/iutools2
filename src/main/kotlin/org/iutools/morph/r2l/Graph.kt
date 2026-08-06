package org.iutools.morph.r2l

import org.iutools.linguisticdata.LinguisticDataException
import org.iutools.linguisticdata.Morpheme
import org.iutools.linguisticdata.constraints.Conditions
import org.iutools.linguisticdata.constraints.Imacond
import org.iutools.linguisticdata.constraints.ParseException
import java.util.Vector

object Graph {

    lateinit var states: Array<State>
        private set
    lateinit var initialState: State
    lateinit var finalState: State
    lateinit var verbState: State

    init {
        val word = State("w") // word with a tail element
        val wordWithLiLuTail = State("wt") // word with an intermediate 'li' or 'lu' tail element
        val wordWithoutTail = State("wnt") // word without a tail element
        val noun = State("n") // nominal word
        val verb = State("v") // verbal word
        verbState = verb
        val adverb = State("a") // adverb
        val expression = State("e") // expression
        val conjunction = State("c") // conjunction
        val nominalStem = State("ns") // nominal stem
        val nominalCompositeStem = State("nsc") // composite nominal stem
        val verbalStem = State("rv") // verbal stem
        val demonstrativeAdverb = State("ad") // demonstrative adverb
        val demonstrativePronounSingular = State("pds") // singular demonstrative pronoun
        val demonstrativePronounPlural = State("pdp") // plural demonstrative pronoun
        val demonstrative = State("d") // demonstrative
        val personalPronoun = State("pp") // personal pronoun
        val personalPronounUvaIli = State("pp12") // personal pronoun based on uva (1st person) or ili (2nd person)
        val personalPronounStemUvaIli = State("pps12") // personal pronoun stem based on uva or ili
        val personalPronounStemOther = State("pps") // personal pronoun other than uva and ili
        val personalPronounRootUva = State("ppr1") // personal pronoun root 'uva'
        val personalPronounRootIli = State("ppr2") // personal pronoun root 'ili'
        val zero = State("0") // beginning of the word

        word.setArcsInternal(arrayOf(
            Arc(makeCond("id:guuq/1q"), wordWithLiLuTail),
            Arc(makeCond("id:kia/1q"), wordWithLiLuTail),
            Arc(makeCond("id:ttauq/1q"), wordWithLiLuTail),
            Arc(makeCond("id:qai/1q"), wordWithLiLuTail),
            Arc(makeCond("type:q"), wordWithoutTail),
            Arc(null, wordWithoutTail)
        ))

        wordWithLiLuTail.setArcsInternal(arrayOf(
            Arc(makeCond("id:li/1q"), wordWithoutTail),
            Arc(makeCond("id:lu/1q"), wordWithoutTail)
        ))

        wordWithoutTail.setArcsInternal(arrayOf(
            Arc(null, noun),
            Arc(null, verb),
            Arc(null, expression),
            Arc(null, adverb),
            Arc(null, conjunction),
            Arc(null, personalPronoun),
            Arc(null, demonstrative),
            // addition of the next arc: see comment below
            Arc(null, nominalStem)
        ))

        noun.setArcsInternal(arrayOf(
            Arc(makeCond("type:tn"), nominalStem),
            Arc(makeCond("type:tn"), nominalCompositeStem),
            Arc(makeCond("type:n,number:d"), zero),
            Arc(makeCond("type:n,number:p"), zero),
            /*
             * The following arc is fine on paper, but in practice, it makes
             * that there is a duplication: Rv (by NV) to N (by null) to Rn
             * (by nominal root) to 0; Rv (by NV) to Rn (by nominal root) to
             * 0. So, it is commented out and replaced by a null arc from M
             * to Rn
             */
            Arc(makeCond("type:tn,number:s,possPers:null"), adverb)
        ))

        verb.setArcsInternal(arrayOf(
            Arc(makeCond("type:tv"), verbalStem)
        ))

        adverb.setArcsInternal(arrayOf(
            Arc(makeCond("type:a"), zero)
        ))

        expression.setArcsInternal(arrayOf(
            Arc(makeCond("type:e"), zero)
        ))

        conjunction.setArcsInternal(arrayOf(
            Arc(makeCond("type:c"), zero)
        ))

        nominalStem.setArcsInternal(arrayOf(
            Arc(makeCond("function:nn"), nominalStem),
            Arc(makeCond("function:nn"), nominalCompositeStem),
            Arc(makeCond("function:nn"), adverb),
            Arc(makeCond("function:vn"), adverb),
            Arc(makeCond("function:vn"), verbalStem),
            Arc(makeCond("type:n,number:s"), zero),
            Arc(makeCond("type:p,!nature:per"), zero)
        ))

        nominalCompositeStem.setArcsInternal(arrayOf(
            Arc(makeCond("type:n,subtype:nc"), zero)
        ))

        verbalStem.setArcsInternal(arrayOf(
            Arc(makeCond("function:vv"), verbalStem),
            Arc(makeCond("function:nv"), noun),
            Arc(makeCond("function:nv"), nominalStem),
            Arc(makeCond("function:nv"), nominalCompositeStem),
            Arc(makeCond("function:nv"), adverb),
            Arc(makeCond("function:nv"), demonstrative),
            Arc(makeCond("type:v"), zero)
        ))

        // Demonstratives
        demonstrative.setArcsInternal(arrayOf(
            Arc(makeCond("type:ad"), zero),
            Arc(makeCond("type:pd"), zero),
            Arc(makeCond("type:tad"), demonstrativeAdverb),
            Arc(makeCond("type:tpd,number:s"), demonstrativePronounSingular),
            Arc(makeCond("type:tpd,number:p"), demonstrativePronounPlural)
        ))

        demonstrativeAdverb.setArcsInternal(arrayOf(
            Arc(makeCond("type:rad"), zero)
        ))

        demonstrativePronounSingular.setArcsInternal(arrayOf(
            Arc(makeCond("type:rpd,number:s"), zero)
        ))

        demonstrativePronounPlural.setArcsInternal(arrayOf(
            Arc(makeCond("type:rpd,number:p"), zero)
        ))

        // Personal pronouns
        personalPronoun.setArcsInternal(arrayOf(
            Arc(null, personalPronounUvaIli),
            Arc(null, personalPronounStemUvaIli),
            Arc(null, personalPronounStemOther),
            Arc(makeCond("type:tn,possPers:null"), personalPronounStemOther)
        ))

        personalPronounUvaIli.setArcsInternal(arrayOf(
            Arc(makeCond("function:nn"), personalPronounStemUvaIli),
            Arc(makeCond("function:nn"), personalPronounUvaIli)
        ))

        personalPronounStemUvaIli.setArcsInternal(arrayOf(
            Arc(makeCond("type:tn,possPers:1,possNumber:Xnumber"), personalPronounRootUva),
            Arc(makeCond("type:tn,possPers:2,possNumber:Xnumber"), personalPronounRootIli)
        ))

        personalPronounStemOther.setArcsInternal(arrayOf(
            Arc(makeCond("type:p"), zero), // pr to p
            Arc(makeCond("function:nn"), personalPronounStemOther)
        ))

        personalPronounRootUva.setArcsInternal(arrayOf(
            Arc(makeCond("id:uva/1rpr"), zero)
        ))

        personalPronounRootIli.setArcsInternal(arrayOf(
            Arc(makeCond("id:ili/1rp"), zero)
        ))

        // Arcs of state 'zero' are null since this is the final state

        initialState = word
        finalState = zero

        states = arrayOf(
            word, wordWithLiLuTail, wordWithoutTail, noun, verb, adverb, expression, conjunction,
            nominalStem, verbalStem, demonstrativeAdverb, demonstrativePronounSingular,
            demonstrativePronounPlural, demonstrative, personalPronoun,
            personalPronounUvaIli, personalPronounStemUvaIli, personalPronounStemOther,
            personalPronounRootUva, personalPronounRootIli, zero
        )
    }

    private fun makeCond(str: String): Conditions? {
        return try {
            Imacond(str).ParseCondition() as Conditions
        } catch (e: ParseException) {
            e.printStackTrace()
            null
        }
    }

    // ------------------------------ Internal Classes ------------------------------
    class State {
        var id: String
        var arcs: Array<Arc> = arrayOf()
            private set

        constructor(id: String) {
            this.id = id
        }

        internal fun setArcsInternal(arcs: Array<Arc>) {
            this.arcs = arcs
            for (arc in arcs) {
                arc.startState = this
            }
        }

        @Throws(LinguisticDataException::class)
        fun verify(affixe: Morpheme): Vector<Arc> {
            val possibleArcs = Vector<Arc>()
            for (arc in arcs) {
                val conds = arc.getCondition()
                if (conds != null) {
                    if (conds.isMetByFullMorphem(affixe)) {
                        possibleArcs.add(arc)
                    }
                } else {
                    val possibles1 = arc.destState.verify(affixe)
                    possibleArcs.addAll(possibles1)
                }
            }
            return possibleArcs
        }

        fun clone(): State {
            val cl = State(this.id)
            cl.arcs = this.arcs.clone()
            return cl
        }
    }

    class Arc(private val cond: Conditions?, val destState: State) {
        var startState: State? = null

        fun getDestinationState(): State = destState

        fun getCondition(): Conditions? = cond

        fun getDestinationStateStr(): String = destState.id

        fun copy(): Arc {
            val arc = Arc(this.cond, this.destState)
            arc.startState = this.startState
            return arc
        }
    }
}
