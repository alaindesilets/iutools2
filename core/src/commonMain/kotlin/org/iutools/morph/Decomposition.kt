package org.iutools.morph

import org.apache.logging.log4j.LogManager
import org.iutools.linguisticdata.Morpheme
import org.iutools.morph.r2l.StateGraphForward

/**
 * One morphological decomposition of a word, as a "surface:id ..." spec
 * string (see [decompSpecs]).
 *
 * [lenient] is true when this decomposition was only found by assuming the
 * word had a final consonant (k/p/q/t) that was silently dropped after a
 * vowel -- R2L's `_decomposeForFinalConsonantPossiblyMissing` /
 * `assumedMissingFinalConsonant` path, enabled by `extendedAnalysisIn`. It
 * is the same "guessed final consonant" signal the ranking already turns
 * into a sort weight (strict readings rank ahead of lenient ones); exposing
 * it per-decomposition lets callers (e.g. an offline analyzed-lexicon
 * dataset) record which readings are strict and which are reconstructions.
 * A decomposition obtainable both strictly and leniently is `false` (the
 * strict producer is kept when duplicates are removed).
 */
class Decomposition(val decompSpecs: String, val lenient: Boolean = false) {

    private var _components: Array<String>? = null

    fun components(): Array<String> {
        if (_components == null) {
            _components = decompSpecs.trim().split(Regex("\\s+")).toTypedArray()
        }
        return _components!!
    }

    @Throws(DecompositionException::class)
    private fun morphemeIDs(): Array<String> {
        return components().map { parseComponent(it).second }.toTypedArray()
    }

    @Throws(DecompositionException::class)
    fun surfaceForms(): List<String> {
        return components().map { parseComponent(it).first ?: "" }
    }

    @Throws(DecompositionException::class)
    fun getMorphemes(): Array<String> {
        return components().map { parseComponent(it).second }.toTypedArray()
    }

    fun getSurfaceForm(component: String): String {
        val componentStr = component.substring(1, component.length - 1)
        return componentStr.split(":")[0]
    }

    fun getMorphemeId(component: String): String {
        val componentStr = component.substring(1, component.length - 1)
        return componentStr.split(":")[1]
    }

    fun validateForFinalComponent(): Boolean {
        val logger = LogManager.getLogger("Decomposition.validateForFinalComponent")
        val lastComponentRaw = components()[components().size - 1]
        val lastComponent = lastComponentRaw.substring(1, lastComponentRaw.length - 1)
        logger.debug("last component: $lastComponent")
        val parts = lastComponent.split(":")
        val morphemeId = parts[1]
        val res = StateGraphForward.morphemeCanBeAtEndOfWord(morphemeId)
        logger.debug("res= $res")
        return res
    }

    fun toStr(): String = decompSpecs

    override fun toString(): String {
        val sb = StringBuilder("{")
        val comps = components()
        for (ii in comps.indices) {
            if (ii > 0) sb.append("}{")
            sb.append(comps[ii])
        }
        sb.append("}")
        return sb.toString()
    }

    companion object {
        @JvmStatic
        @Throws(DecompositionException::class)
        fun decomps2morphemes(decompObjs: Array<Decomposition>): Array<Array<String>> {
            return decompObjs.map { it.morphemeIDs() }.toTypedArray()
        }

        @JvmStatic
        @JvmOverloads
        fun morphIDs2DecompString(morphIDs: Array<String>?, withBraces: Boolean? = null): String {
            val useBraces = withBraces ?: true
            var decompString = ""
            if (morphIDs != null) {
                val formatted = if (useBraces) Morpheme.withBraces(morphIDs) else Morpheme.removeIDBraces(morphIDs)
                decompString = formatted.joinToString(" ")
            }
            return decompString
        }

        @JvmStatic
        fun removeMultiples(decs: Array<Decomposition>?): Array<Decomposition> {
            if (decs == null || decs.isEmpty()) {
                return decs ?: arrayOf()
            }
            val v = mutableListOf<Decomposition>()
            val vc = mutableListOf<String>()
            v.add(decs[0])
            vc.add(decs[0].toString())
            for (i in 1 until decs.size) {
                val c = decs[i].toString()
                if (!vc.contains(c)) {
                    v.add(decs[i])
                    vc.add(c)
                }
            }
            return v.toTypedArray()
        }

        @JvmStatic
        @JvmOverloads
        @Throws(DecompositionException::class)
        fun parseComponent(comp: String, mayMissFirstComponentIn: Boolean? = null): Pair<String?, String> {
            val mayMissFirstComponent = mayMissFirstComponentIn ?: false
            val cleaned = comp.replace(Regex("[{}]"), "")
            val parsed = cleaned.split(":")
            var correctlyParsed = true
            if (parsed.isEmpty() || parsed.size > 2 || (parsed.size == 1 && !mayMissFirstComponent)) {
                correctlyParsed = false
            }
            if (!correctlyParsed) {
                throw DecompositionException("Could not parse component '$comp'")
            }

            val matchedString: String?
            val morphID: String
            if (parsed.size == 2) {
                matchedString = parsed[0]
                morphID = parsed[1]
            } else {
                matchedString = null
                morphID = parsed[0]
            }

            return Pair(matchedString, morphID)
        }
    }
}
