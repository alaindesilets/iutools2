package org.iutools.morph.rerank

import org.iutools.morph.Decomposition

/*
 * Re-sorts a word's R2L candidate decompositions so the one actually
 * attested by Hansard usage lands first more often than R2L's own native
 * order does (see data/grammar/reference-reranker/README.md: ~90% fair
 * reference@1 nested CV, vs R2L's native ~73%).
 *
 * This is the ONLY place that should apply the (canonical, id) dedup R2L's
 * raw output sometimes needs (see [dedupeByParts]) before scoring or
 * displaying "the top N decompositions" -- callers that used
 * `decomposeWord()`'s raw array capped to N were, before this class
 * existed, both un-re-ranked AND liable to waste a slot on a surface-only
 * duplicate.
 */
object ReferenceReranker {

    /** [decomps] in any order (typically R2L's own native
     *  `decomposeWord()` output) -> deduped-by-parts, best-first by this
     *  model's score. Empty input returns empty output. */
    fun rerank(word: String, decomps: Array<Decomposition>): List<Decomposition> {
        if (decomps.isEmpty()) return emptyList()
        val deduped = dedupeByParts(decomps)
        val vectors = computeFeatureVectors(word, deduped.map { it.parts })
        val model = ReferenceRerankerModel.instance
        return deduped.indices
            .sortedByDescending { model.score(vectors[it]) }
            .map { deduped[it].decomp }
    }

    private class Candidate(val parts: List<MorphemePart>, val decomp: Decomposition)

    /** Dedupe [decomps] by (canonical, id) sequence, keeping each distinct
     *  sequence's EARLIEST (best native-rank) occurrence. R2L can emit
     *  several surface segmentations that collapse to the same morpheme
     *  sequence (e.g. {mi:miik/1vn}{ik:k/tn-nom-d} ==
     *  {mii:miik/1vn}{k:k/tn-nom-d}); the model was trained on one
     *  candidate per sequence, at its earliest rank -- see
     *  data/grammar/reference-reranker/README.md. */
    private fun dedupeByParts(decomps: Array<Decomposition>): List<Candidate> {
        val seen = HashSet<List<MorphemePart>>()
        val out = ArrayList<Candidate>()
        for (d in decomps) {
            val parts = toParts(d)
            if (seen.add(parts)) out.add(Candidate(parts, d))
        }
        return out
    }

    private fun toParts(d: Decomposition): List<MorphemePart> =
        d.getMorphemes().map { m ->
            val slash = m.indexOf('/')
            MorphemePart(m.substring(0, slash), m.substring(slash + 1))
        }
}
