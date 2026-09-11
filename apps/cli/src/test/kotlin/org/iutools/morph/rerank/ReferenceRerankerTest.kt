package org.iutools.morph.rerank

import org.iutools.morph.Decomposition
import org.iutools.morph.r2l.MorphologicalAnalyzer_R2L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * ReferenceReranker's own responsibilities beyond feature/score computation
 * (covered by ReferenceRerankerParityTest against the Python golden
 * fixture): deduping R2L's raw candidate array by (canonical, id) sequence,
 * and actually sorting best-first by the model's score, on real
 * Decomposition objects.
 */
class ReferenceRerankerTest {

    @Test
    fun rerank_emptyInput_returnsEmpty() {
        assertEquals(emptyList(), ReferenceReranker.rerank("iglumik", emptyArray()))
    }

    @Test
    fun rerank_dedupesSurfaceVariantsOfTheSameMorphemeSequence() {
        // Same (canonical, id) sequence, two different surface
        // segmentations -- exactly the R2L phenomenon this class exists to
        // collapse (see its header comment).
        val a = Decomposition("{mi:miik/1vn} {ik:k/tn-nom-d}")
        val b = Decomposition("{mii:miik/1vn} {k:k/tn-nom-d}")

        val result = ReferenceReranker.rerank("miik", arrayOf(a, b))

        assertEquals(1, result.size)
        assertTrue(result[0] === a, "should keep the EARLIEST (first) occurrence")
    }

    @Test
    fun rerank_keepsDistinctMorphemeSequencesSeparate() {
        val a = Decomposition("{iglu:iglu/1n} {mik:mik/tn-acc-s}")
        val b = Decomposition("{iglu:iglu/1n} {mik:mik/tn-gen-s-3d}")

        val result = ReferenceReranker.rerank("iglumik", arrayOf(a, b))

        assertEquals(2, result.size)
    }

    private fun toParts(d: Decomposition): List<MorphemePart> =
        d.getMorphemes().map { m ->
            val slash = m.indexOf('/')
            MorphemePart(m.substring(0, slash), m.substring(slash + 1))
        }

    @Test
    fun rerank_sortsBestScoreFirst() {
        val word = "iglumik"
        val analyzer = MorphologicalAnalyzer_R2L()
        val nativeOrder = analyzer.decomposeWord(word, false)
        assertTrue(nativeOrder.size > 1, "test word should have several candidate decomps")

        // Same (canonical, id)-sequence dedup ReferenceReranker itself
        // applies, kept independent here so this test computes the
        // relative features (rank_current_sort, etc.) the same way
        // rerank() does internally -- over the whole deduped, native-order
        // set at once -- rather than recomputing them one candidate at a
        // time, which would give different (wrong) relative-feature values.
        val seen = HashSet<List<MorphemePart>>()
        val dedupedParts = nativeOrder.map { toParts(it) }.filter { seen.add(it) }

        val model = ReferenceRerankerModel.instance
        val expectedScores = computeFeatureVectors(word, dedupedParts).map { model.score(it) }
        val expectedOrder = dedupedParts.indices.sortedByDescending { expectedScores[it] }

        val result = ReferenceReranker.rerank(word, nativeOrder)

        assertEquals(expectedOrder.map { dedupedParts[it] }, result.map { toParts(it) })
    }
}
