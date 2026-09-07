package org.iutools.linguisticdata

import org.iutools.script.Script
import org.iutools.script.TransCoder

/*
 * Given a morpheme id ("umiaq/1n", "mni/tn-loc-s-1s", "taava/ad-ml"), builds a
 * human-readable description of the morpheme: its canonical form and an
 * English gloss of the grammatical roles encoded in the id
 * ("possessive noun ending; locative singular; 1st person singular possessor").
 *
 * Ported from org.iutools.linguisticdata.MorphemeHumanReadableDescr in the
 * original iutools Java project. In that project this class is used only by
 * the morpheme-dictionary web service, which was out of scope for the first
 * pass of this port -- it is brought in now for the Morpheme Dictionary
 * feature. The original's dead/commented-out helpers (number4abbrev,
 * caseName4caseAbbrev, position, partofSpeechName, the alternate partOfSpeech)
 * were unreachable and are not ported. Regex id-splitting is replaced with
 * plain String ops (Android's regex engine has rejected JVM-valid syntax
 * before -- see AGENTS.md).
 */
class MorphemeHumanReadableDescr(
    val id: String,
    meaning: String? = null,
) : Comparable<MorphemeHumanReadableDescr> {

    var canonicalForm: String
        private set

    /** English gloss of the id's grammatical roles; null when the id encodes none. */
    val grammar: String?

    /**
     * English meaning of the morpheme. Defaults to the morpheme's own
     * englishMeaning from the linguistic database; a caller that wants the
     * French gloss (or any other) passes it in explicitly.
     */
    val meaning: String? =
        meaning ?: LinguisticData.getInstance().getMorpheme(id)?.englishMeaning

    init {
        val slash = id.indexOf('/')
        if (slash < 0) {
            canonicalForm = id
            grammar = null
        } else {
            canonicalForm = id.substring(0, slash)
            grammar = grammarFor(attributesOf(id))
        }
    }

    /** "umiaq (noun root)" style one-liner. */
    fun descriptiveText(): String = "$canonicalForm ($grammar)"

    fun ensureScript(script: Script) {
        canonicalForm = TransCoder.ensureScript(script, canonicalForm)
    }

    override fun compareTo(other: MorphemeHumanReadableDescr): Int = id.compareTo(other.id)

    companion object {
        // Morpheme-type code (first id attribute) -> English label.
        private val typesOfMorphemes: Map<String, String> = mapOf(
            "n" to "noun root",
            "v" to "verb root",
            "a" to "adverb",
            "c" to "conjunction",
            "e" to "exclamation/disclaimer",
            "q" to "tail suffix",
            "nn" to "noun-to-noun suffix",
            "nv" to "noun-to-verb suffix",
            "vn" to "verb-to-noun suffix",
            "vv" to "verb-to-verb suffix",
            "pr" to "pronoun",
            "p" to "pronoun",
            "rpr" to "pronoun root",
            "rp" to "pronoun root",
            "ad" to "demonstrative adverb",
            "rad" to "demonstrative adverb root",
            "tad" to "demonstrative adverb ending",
            "pd" to "demonstrative pronoun",
            "rpd" to "demonstrative pronoun root",
            "tpd" to "demonstrative prnoun ending",
            "tn" to "noun ending",
            "tv" to "verb ending",
        )

        // Case/mood abbreviation (second id attribute) -> English name.
        private val caseMoodAbbrevs: Map<String, String> = mapOf(
            "nom" to "nominative",
            "gen" to "genitive",
            "loc" to "locative",
            "acc" to "accusative",
            "abl" to "ablative",
            "dat" to "dative",
            "sim" to "similaris",
            "via" to "vialis",
            "dec" to "declarative",
            "caus" to "causative",
            "part" to "participial",
            "int" to "interrogative",
            "imp" to "imperative",
            "dub" to "dubitative",
            "freq" to "frequentative",
            "ger" to "gerundive",
            "cond" to "conditional",
            "sc" to "static/short referent",
            "ml" to "moving/long referent",
            "mlsc" to "referent of either moving/long OR static/short nature",
        )

        // First-attribute codes that carry case/mood + person/number roles.
        private val endingLikeTypes = setOf("tn", "tv", "ad", "rad", "pd", "rpd")

        fun descriptiveText(morphId: String): String =
            MorphemeHumanReadableDescr(morphId).descriptiveText()

        // "mni/tn-loc-s-1s" -> ["tn","loc","s","1s"]. The original regex
        // ("^([^/]*)/\\d?(.*)$") drops one optional digit right after the "/"
        // (the homograph number in "1n", "1vv", ...); id attributes are
        // "-"-separated.
        private fun attributesOf(morphId: String): List<String> {
            var rest = morphId.substringAfter('/', "")
            if (rest.firstOrNull()?.isDigit() == true) {
                rest = rest.substring(1)
            }
            return rest.split("-")
        }

        private fun grammarFor(attributes: List<String>): String {
            var gramm = ""
            gramm = expand(gramm, transitivityOrPossessivity(attributes))
            gramm = expand(gramm, partOfSpeech(attributes))
            val caseOrMood = caseOrMoodFor(attributes)
            val primary = primaryPersonAndNumber(attributes)
            if (caseOrMood != null || primary != null) {
                gramm += ";"
            }
            gramm = expand(gramm, caseOrMood)
            gramm = expand(gramm, primary)
            val secondary = secondaryPersonAndNumber(attributes)
            if (secondary != null) {
                gramm += ";"
            }
            gramm = expand(gramm, secondary)
            return gramm
        }

        private fun expand(descr: String, toAppend: String?): String {
            if (toAppend == null) return descr
            return if (descr.isNotEmpty()) "$descr $toAppend" else toAppend
        }

        private fun partOfSpeech(attributes: List<String>): String? =
            typesOfMorphemes[attributes[0]]

        private fun transitivityOrPossessivity(attributes: List<String>): String? {
            val first = attributes[0]
            if (!first.startsWith("t")) return null
            val hasFourthAttribute = attributes.size > 3
            return when (first) {
                "tn" -> if (hasFourthAttribute) "possessive" else null
                "tv" -> if (hasFourthAttribute) "transitive" else "intransitive"
                else -> null
            }
        }

        private fun caseOrMoodFor(attributes: List<String>): String? {
            if (attributes[0] !in endingLikeTypes) return null
            var caseAbbr: String? = attributes[1]
            if (caseAbbr == "?") caseAbbr = null
            if (caseAbbr == null) return null
            var descr = caseMoodAbbrevs[caseAbbr] ?: return null
            if (descr == "participial") {
                when (attributes.last()) {
                    "fut" -> descr = "future $descr"
                    "prespas" -> descr = "past/present $descr"
                }
            }
            return descr
        }

        private fun primaryPersonAndNumber(attributes: List<String>): String? {
            if (attributes[0] !in endingLikeTypes) return null
            if (attributes.size <= 2) return null
            return personAndNumber4abbrev(attributes[2])
        }

        private fun secondaryPersonAndNumber(attributes: List<String>): String? {
            val first = attributes[0]
            if (!first.startsWith("t") || attributes.size <= 3) return null
            val persNum = personAndNumber4abbrev(attributes[3]) ?: return null
            return when (first) {
                "tn" -> "$persNum possessor"
                "tv" -> "$persNum object"
                else -> persNum
            }
        }

        // "1s" -> "1st person singular", "s" -> "singular", "3d" -> "3rd person dual".
        private fun personAndNumber4abbrev(abbrev: String): String? {
            val person = when {
                abbrev.startsWith("1") -> "1st person"
                abbrev.startsWith("2") -> "2nd person"
                abbrev.startsWith("3") -> "3rd person"
                abbrev.startsWith("4") -> "4th person"
                else -> null
            }
            val number = when {
                abbrev.contains("s") -> "singular"
                abbrev.contains("d") -> "dual"
                abbrev.contains("p") -> "plural"
                else -> null
            }
            return when {
                person != null && number != null -> "$person $number"
                person != null -> person
                number != null -> number
                else -> null
            }
        }
    }
}
