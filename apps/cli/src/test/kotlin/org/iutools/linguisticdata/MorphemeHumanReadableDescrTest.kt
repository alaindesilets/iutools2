package org.iutools.linguisticdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/*
 * Port of org.iutools.linguisticdata.MorphemeHumanReadableDescrTest (Java).
 *
 * The Java "SeveralCases" table also pinned each morpheme's `meaning`; those
 * strings are linguistic data (English glosses from the CSVs) and are NOT
 * retyped here (see AGENTS.md). Instead the port asserts the two things this
 * class actually computes -- canonicalForm and grammar -- and separately
 * checks that `meaning` is wired to the morpheme's own englishMeaning.
 */
class MorphemeHumanReadableDescrTest {

    private data class Case(val id: String, val canonical: String, val grammar: String)

    private val severalCases = listOf(
        Case("umiaq/1n", "umiaq", "noun root"),
        Case("pisuk/1v", "pisuk", "verb root"),
        Case("amma/1c", "amma", "conjunction"),
        Case("aakka/1a", "aakka", "adverb"),
        Case("aamai/1e", "aamai", "exclamation/disclaimer"),
        Case("uvanga/1p", "uvanga", "pronoun"),
        Case("quti/1nn", "quti", "noun-to-noun suffix"),
        Case("liuq/1nv", "liuq", "noun-to-verb suffix"),
        Case("ji/1vn", "ji", "verb-to-noun suffix"),
        Case("nasuk/1vv", "nasuk", "verb-to-verb suffix"),
        Case("lu/1q", "lu", "tail suffix"),
        Case("mi/tn-loc-s", "mi", "noun ending; locative singular"),
        Case(
            "mni/tn-loc-s-1s", "mni",
            "possessive noun ending; locative singular; 1st person singular possessor",
        ),
        Case(
            "vugut/tv-dec-1p", "vugut",
            "intransitive verb ending; declarative 1st person plural",
        ),
        Case(
            "gapku/tv-caus-1s-3s", "gapku",
            "transitive verb ending; causative 1st person singular; 3rd person singular object",
        ),
        Case(
            "lunikku/tv-part-3d-3s-fut", "lunikku",
            "transitive verb ending; future participial 3rd person dual; 3rd person singular object",
        ),
        Case("taava/ad-ml", "taava", "demonstrative adverb; moving/long referent"),
        Case(
            "qaksu/rpd-mlsc-s", "qaksu",
            "demonstrative pronoun root; referent of either moving/long OR static/short nature singular",
        ),
        Case("tagg/rad-sc", "tagg", "demonstrative adverb root; static/short referent"),
        Case(
            "taaksu/rpd-ml-s", "taaksu",
            "demonstrative pronoun root; moving/long referent singular",
        ),
    )

    @Test
    fun severalCases_canonicalAndGrammar() {
        for (c in severalCases) {
            val descr = MorphemeHumanReadableDescr(c.id)
            assertEquals(c.canonical, descr.canonicalForm, "canonical form for ${c.id}")
            assertEquals(c.grammar, descr.grammar, "grammar for ${c.id}")
        }
    }

    @Test
    fun meaning_defaultsToMorphemeEnglishMeaning() {
        val data = LinguisticData.getInstance()
        for (c in severalCases) {
            val descr = MorphemeHumanReadableDescr(c.id)
            assertEquals(
                data.getMorpheme(c.id)?.englishMeaning,
                descr.meaning,
                "meaning for ${c.id}",
            )
        }
    }

    @Test
    fun meaning_canBeOverridden() {
        val descr = MorphemeHumanReadableDescr("umiaq/1n", "bateau")
        assertEquals("bateau", descr.meaning)
    }

    @Test
    fun descriptiveText_happyPath() {
        assertEquals("umiaq (noun root)", MorphemeHumanReadableDescr.descriptiveText("umiaq/1n"))
    }

    @Test
    fun idWithoutSlash_hasNoGrammar() {
        val descr = MorphemeHumanReadableDescr("umiaq")
        assertEquals("umiaq", descr.canonicalForm)
        assertNull(descr.grammar)
    }

    @Test
    fun runsOnEveryMorphemeIdWithoutThrowing() {
        val ids = LinguisticData.getInstance().allMorphemeIDs()
        assertEquals(true, ids.isNotEmpty(), "expected some morpheme ids")
        val failures = StringBuilder()
        for (id in ids) {
            try {
                MorphemeHumanReadableDescr(id)
            } catch (e: Exception) {
                failures.append("\n  $id -> ${e::class.simpleName}: ${e.message}")
            }
        }
        if (failures.isNotEmpty()) {
            fail("MorphemeHumanReadableDescr threw for some ids:$failures")
        }
    }
}
