package org.iutools.morph.rerank

import org.iutools.morph.MorphemeFrequencyPrior
import kotlin.math.min

/*
 * Computes the 34 numeric features [ReferenceReranker]'s GBDT model was
 * trained on, for one candidate decomposition of a word among its sibling
 * candidates. This is a line-by-line Kotlin port of two Python functions
 * that must stay in lock step with them: `features()` and
 * `add_relative_features()` in data/grammar/fst/build_reranker_table.py
 * (imported unchanged by build_reranker_table_r2l.py, which built the R2L
 * training table this model was fit on) -- see
 * data/grammar/reference-reranker/README.md for why only these two
 * (identity-free + `freq`, both here) made the cut, not `bigram` /
 * `backoff` / `r2l`.
 *
 * [ReferenceRerankerFeatureNames.ORDER] fixes the feature order this file
 * writes into -- it must match `reranker_model.json`'s own `"features"`
 * list exactly (checked at load time by [ReferenceRerankerModel]) since
 * the model's trees reference features purely by index.
 */

/** One morpheme segment of a candidate decomposition: its canonical
 *  (citation) form and its grammatical-tag id, e.g. ("aaqqik", "1v"). */
data class MorphemePart(val canonical: String, val id: String)

object ReferenceRerankerFeatureNames {
    // Must match reranker_model.json's "features" list, in order.
    val ORDER = listOf(
        "n_morphemes", "n_nonroot", "n_deriv", "n_ending", "n_consec_dup_id",
        "wordlen_per_morph", "root_canon_len", "mean_canon_len", "min_canon_len",
        "n_thin_canon", "n_vv", "n_nn", "n_vn", "n_nv", "cat_switches",
        "type_violations", "ends_with_ending", "ends_with_bare_deriv",
        "edit_dist", "edit_dist_norm", "concat_len_minus_word", "first_canon_is_prefix",
        "rank_current_sort", "n_candidates", "edit_dist_rank_in_word",
        "n_morphemes_rank_in_word", "root_canon_len_rank_in_word",
        "type_violations_rank_in_word", "weight",
        "freq_sum", "freq_mean", "freq_min", "freq_root", "n_zero_freq",
    )
}

private val ENDING_RE = Regex("^(tn|tv|tad|tpd)-")
private val DERIV_RE = Regex("^\\d+(vn|nv|vv|nn)$")
private val ROOT_VN_RE = Regex("^\\d+[vn]$")
private val ROOT_OTHER_RE = Regex("^\\d+[a-z]+$")
private val VV_RE = Regex("^\\d+vv$")
private val NN_RE = Regex("^\\d+nn$")
private val VN_RE = Regex("^\\d+vn$")
private val NV_RE = Regex("^\\d+nv$")

/** ("root" | "deriv" | "ending" | "other", inCategory, outCategory) -- see
 *  build_reranker_table.py's morph_category() for the source of truth. */
private data class MorphCategory(val kind: String, val catIn: Char?, val catOut: Char?)

private fun morphCategory(id: String): MorphCategory {
    // ENDING_RE / DERIV_RE are fully self-anchored (^...$ or a ^-prefix
    // that only ever matches at position 0), so `find` here is equivalent
    // to Python's re.match().
    if (ENDING_RE.find(id) != null) return MorphCategory("ending", null, null)
    DERIV_RE.matchEntire(id)?.let {
        val g = it.groupValues[1]
        return MorphCategory("deriv", g[0], g[1])
    }
    if (ROOT_VN_RE.matches(id)) return MorphCategory("root", null, id.last())
    if (ROOT_OTHER_RE.matches(id) || id.startsWith("rad-") || id.startsWith("pd-") || id.startsWith("ad-")) {
        return MorphCategory("root", null, 'n')
    }
    return MorphCategory("other", null, null)
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        }
        prev = cur
    }
    return prev[b.length]
}

/** Identity-free features of one candidate: shape, grammatical-category
 *  shape, and faithfulness to the surface string. Does not need the
 *  sibling candidates -- see [relativeFeatures] for those. */
private fun identityFreeFeatures(word: String, parts: List<MorphemePart>): MutableMap<String, Double> {
    val canons = parts.map { it.canonical }
    val ids = parts.map { it.id }
    val cats = ids.map { morphCategory(it) }
    val n = parts.size

    val rootCanonLen = canons.firstOrNull()?.length ?: 0
    val concat = canons.joinToString("")

    var curCat = cats.firstOrNull()?.catOut ?: 'n'
    var switches = 0
    var viol = 0
    var nDeriv = 0
    for (cat in cats.drop(1)) {
        if (cat.kind == "deriv") {
            nDeriv++
            if (cat.catIn != curCat) viol++
            if (cat.catOut != curCat) switches++
            curCat = cat.catOut!!  // non-null by construction: "deriv" always sets catOut
        }
    }

    val ed = levenshtein(concat, word)
    val feat = LinkedHashMap<String, Double>()
    feat["n_morphemes"] = n.toDouble()
    feat["n_nonroot"] = (n - 1).toDouble()
    feat["n_deriv"] = nDeriv.toDouble()
    feat["n_ending"] = cats.count { it.kind == "ending" }.toDouble()
    feat["n_consec_dup_id"] = ids.zipWithNext().count { (a, b) -> a == b }.toDouble()
    feat["wordlen_per_morph"] = word.length.toDouble() / n
    feat["root_canon_len"] = rootCanonLen.toDouble()
    feat["mean_canon_len"] = canons.sumOf { it.length }.toDouble() / n
    feat["min_canon_len"] = (canons.minOfOrNull { it.length } ?: 0).toDouble()
    feat["n_thin_canon"] = canons.count { it.length <= 1 }.toDouble()
    feat["n_vv"] = ids.count { VV_RE.matches(it) }.toDouble()
    feat["n_nn"] = ids.count { NN_RE.matches(it) }.toDouble()
    feat["n_vn"] = ids.count { VN_RE.matches(it) }.toDouble()
    feat["n_nv"] = ids.count { NV_RE.matches(it) }.toDouble()
    feat["cat_switches"] = switches.toDouble()
    feat["type_violations"] = viol.toDouble()
    feat["ends_with_ending"] = if (cats.isNotEmpty() && cats.last().kind == "ending") 1.0 else 0.0
    feat["ends_with_bare_deriv"] = if (cats.isNotEmpty() && cats.last().kind == "deriv") 1.0 else 0.0
    feat["edit_dist"] = ed.toDouble()
    feat["edit_dist_norm"] = ed.toDouble() / maxOf(word.length, 1)
    feat["concat_len_minus_word"] = (concat.length - word.length).toDouble()
    feat["first_canon_is_prefix"] = if (canons.isNotEmpty() && word.startsWith(canons[0])) 1.0 else 0.0
    return feat
}

/** freq_sum/freq_mean/freq_min/freq_root/n_zero_freq, from
 *  MorphemeFrequencyPrior -- verified byte-for-byte identical to the
 *  Python model's own training-time frequency table (see this package's
 *  header comment / the reference-reranker README). */
private fun freqFeatures(parts: List<MorphemePart>): Map<String, Double> {
    val counts = parts.map { MorphemeFrequencyPrior.count("${it.canonical}/${it.id}") }
    val n = maxOf(parts.size, 1)
    return mapOf(
        "freq_sum" to counts.sum().toDouble(),
        "freq_mean" to counts.sum().toDouble() / n,
        "freq_min" to (counts.minOrNull() ?: 0).toDouble(),
        "freq_root" to (counts.firstOrNull() ?: 0).toDouble(),
        "n_zero_freq" to counts.count { it == 0 }.toDouble(),
    )
}

/**
 * Computes the full 34-feature vector (in [ReferenceRerankerFeatureNames.ORDER])
 * for every candidate in [candidatesInNativeRank] -- which MUST already be
 * deduped by (canonical, id) sequence and in R2L's own native rank order
 * (see [ReferenceReranker.dedupeByParts]) -- so that `rank_current_sort`
 * and the `*_rank_in_word` features come out identical to what the model
 * was trained on.
 */
fun computeFeatureVectors(word: String, candidatesInNativeRank: List<List<MorphemePart>>): List<DoubleArray> {
    val n = candidatesInNativeRank.size
    val base = candidatesInNativeRank.map { identityFreeFeatures(word, it) }
    val freq = candidatesInNativeRank.map { freqFeatures(it) }

    for ((i, feat) in base.withIndex()) {
        feat["rank_current_sort"] = i.toDouble()
        feat["n_candidates"] = n.toDouble()
        feat["weight"] = 0.0
        feat.putAll(freq[i])
    }

    // *_rank_in_word: 0-based rank under a STABLE sort (ties keep native-rank
    // order, matching Python's stable sorted() over the already
    // native-rank-ordered `group` list).
    fun rankInWord(key: String, descending: Boolean = false): List<Int> {
        val indices = base.indices.sortedBy { i -> if (descending) -base[i][key]!! else base[i][key] }
        val rank = IntArray(n)
        indices.forEachIndexed { r, i -> rank[i] = r }
        return rank.toList()
    }
    val edRank = rankInWord("edit_dist")
    val nMorphRank = rankInWord("n_morphemes")
    val rootLenRank = rankInWord("root_canon_len", descending = true)
    val violRank = rankInWord("type_violations")
    for (i in 0 until n) {
        base[i]["edit_dist_rank_in_word"] = edRank[i].toDouble()
        base[i]["n_morphemes_rank_in_word"] = nMorphRank[i].toDouble()
        base[i]["root_canon_len_rank_in_word"] = rootLenRank[i].toDouble()
        base[i]["type_violations_rank_in_word"] = violRank[i].toDouble()
    }

    return base.map { feat -> DoubleArray(ReferenceRerankerFeatureNames.ORDER.size) { j -> feat[ReferenceRerankerFeatureNames.ORDER[j]] ?: 0.0 } }
}
