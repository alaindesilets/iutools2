package org.iutools.morphemedict

import org.iutools.linguisticdata.LinguisticData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 * Tests for the Morpheme Dictionary lookup. The original Java
 * MorphemeDictionaryTest is corpus/Elasticsearch-heavy (it checks example
 * words and their ranking); this pass ports only the "which morphemes match"
 * half, so this is a fresh, focused test of that.
 */
class MorphemeDictionaryTest {

    private val dict = MorphemeDictionary()

    private fun ids(results: List<org.iutools.linguisticdata.MorphemeHumanReadableDescr>) =
        results.map { it.id }

    @Test
    fun exactCanonicalForm_findsTheMorpheme() {
        assertTrue("umiaq/1n" in ids(dict.search("umiaq")))
    }

    @Test
    fun prefixOfCanonicalForm_findsTheMorpheme() {
        val results = dict.search("umia")
        assertTrue("umiaq/1n" in ids(results))
        assertTrue(
            results.all { it.canonicalForm.startsWith("umia", ignoreCase = true) },
            "every result should start with the prefix",
        )
    }

    @Test
    fun blankPrefix_withGrammarFilter_keepsOnlyMatchingGrammar() {
        val results = dict.search("", grammarSubstring = "locative")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.grammar?.contains("locative", ignoreCase = true) == true })
        assertTrue("mi/tn-loc-s" in ids(results))
    }

    @Test
    fun grammarFilter_isASubstringMatch() {
        assertTrue("ji/1vn" in ids(dict.search("", grammarSubstring = "verb-to-noun")))
    }

    @Test
    fun meaningFilter_keepsOnlyMorphemesWhoseMeaningContainsIt() {
        val results = dict.search("", meaningSubstring = "walk")
        assertTrue(results.isNotEmpty())
        assertTrue("pisuk/1v" in ids(results))
        assertTrue(
            results.all {
                val m = LinguisticData.getInstance().getMorpheme(it.id)
                (m?.englishMeaning?.contains("walk", ignoreCase = true) == true) ||
                    (m?.frenchMeaning?.contains("walk", ignoreCase = true) == true)
            },
        )
    }

    @Test
    fun compositeMorphemes_areExcludedByDefault_butOptIn() {
        assertFalse("ilinniaqtit/1v" in ids(dict.search("ilinniaqtit")))
        assertTrue("ilinniaqtit/1v" in ids(dict.search("ilinniaqtit", includeComposite = true)))
    }

    @Test
    fun results_areSortedByCanonicalThenId() {
        val results = dict.search("i")
        val keys = results.map { it.canonicalForm.lowercase() to it.id }
        assertEquals(keys.sortedWith(compareBy({ it.first }, { it.second })), keys)
    }

    @Test
    fun preferFrench_usesMorphemePreferredMeaning() {
        val expected = LinguisticData.getInstance().getMorpheme("umiaq/1n")?.preferredMeaning(true)
        val descr = dict.search("umiaq", preferFrench = true).first { it.id == "umiaq/1n" }
        assertEquals(expected, descr.meaning)
    }

    @Test
    fun preferFrench_fallsBackToEnglishWhenNoFrenchGloss() {
        // ~24% of Spalding roots have no French gloss; the French UI must
        // still show something (the English one) for those, not a blank.
        val data = LinguisticData.getInstance()
        val frenchless = data.allMorphemeIDs().firstOrNull { id ->
            val m = data.getMorpheme(id)
            !m?.englishMeaning.isNullOrBlank() && m?.frenchMeaning.isNullOrBlank()
        }
        assertTrue(frenchless != null, "expected at least one morpheme with no French gloss")
        val canonical = frenchless!!.substringBefore('/')
        val descr = dict.search(canonical, preferFrench = true).first { it.id == frenchless }
        assertEquals(data.getMorpheme(frenchless)?.englishMeaning, descr.meaning)
    }
}
