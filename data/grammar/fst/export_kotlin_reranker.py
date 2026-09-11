"""
Freeze the R2L-targeted GBDT re-ranker as a portable artifact for the Kotlin
port (see doc/dev/plans/offline-dictionary-generation.md, "Post-validation",
item 2).

Feature set: shape + cat + surface + relative + weight + freq (drops r2l,
backoff, bigram -- empirically ~0 cost on the R2L table, see the session
that produced this script: flat-CV fair P@1 90.3% all-groups vs 90.4%
r2l+backoff+bigram-dropped). This particular reduction also means the
Kotlin port needs ZERO new corpus-derived data tables: `freq` is exactly
MorphemeFrequencyPrior (core/src/main/kotlin/org/iutools/morph/
MorphemeFrequencyPrior.kt) -- verified byte-for-byte identical to this
script's `ci` counter, all 1259 keys -- and every other kept group is a
pure function of (word, decomp), no corpus statistics at all.

Two outputs:
1. reranker_model.json -- the model actually shipped: trained on ALL rows
   in the table (no held-out fold), settled hyperparameters, trees
   rewritten from (feature_bin, threshold_bin) to (feature_index,
   raw_threshold_value) so Kotlin needs no quantile-binning logic at
   inference time -- just `if x[feat] < thr: left else: right`.
2. reranker_golden_fixture.json -- a sample of (word, parts, feature
   vector, model score) rows, for a Kotlin unit test to assert against
   (feature-by-feature AND score parity with this Python implementation).

Both validated in-process before writing: the raw-threshold tree walk is
checked bit-for-bit against the original bin-indexed tree_pred on every
row of the table before trusting the conversion.

Run from data/grammar/fst/.
"""
import json
import random

import reranker_cv as R
import reranker_gbdt as G

DROP = {"r2l", "backoff", "bigram"}
CFG = dict(trees=150, depth=4, lr=0.1, leaf=20, lam=1.0, neg=8, rowsub=0.4, featsub=0.6)
NBINS = 32
SEED = R.SEED

MODEL_OUT = "../reference-reranker/reranker_model.json"
FIXTURE_OUT = "../reference-reranker/reranker_golden_fixture.json"
N_FIXTURE_WORDS = 25


def raw_threshold(edges_j, thr_bin):
    """See the session's derivation: B[i][j] <= thr_bin (go left)  <=>
    x[i][j] < edges_j[thr_bin] (or always-true if thr_bin >= len(edges_j))."""
    if thr_bin >= len(edges_j):
        return float("inf")
    return edges_j[thr_bin]


def convert_tree(node, edges):
    if node.feat is None:
        return {"leaf": node.val}
    return {
        "feat": node.feat,
        "thr": raw_threshold(edges[node.feat], node.thr),
        "left": convert_tree(node.left, edges),
        "right": convert_tree(node.right, edges),
    }


def raw_tree_pred(node, x):
    while "leaf" not in node:
        node = node["left"] if x[node["feat"]] < node["thr"] else node["right"]
    return node["leaf"]


def main():
    rows, words, keep = R.load(DROP)
    R.recompute_stats(rows, R.build_stats(R.load_cache_rows()))
    R.set_x(rows, keep)
    edges = G.prebin(rows, keep, NBINS)
    nf = len(keep)

    all_words = sorted(words)
    rng = random.Random(SEED)
    print(f"training final model on ALL {len(all_words)} words ({nf} features: {keep})")
    trees = G.train_gbdt(rows, words, all_words, nf, NBINS, CFG, rng, "label")

    # sanity: in-sample fair P@1 of the frozen model (upper bound, not a CV
    # number -- the honest nested-CV number is reported separately).
    G.score_all(rows, trees, CFG["lr"])
    fair_of = {w: words[w][0]["fair"] for w in all_words}
    tot, _ = G.evaluate(words, all_words, fair_of)
    h1, h3, rr, n = tot["fair"]
    print(f"in-sample fair P@1 {h1}/{n} ({100*h1/n:.1f}%) -- not the honest number, sanity only")

    print("converting trees to raw-threshold form + validating bit-for-bit against binned tree_pred ...")
    raw_trees = [convert_tree(t, edges) for t in trees]
    mismatches = 0
    for r in rows:
        binned_score = CFG["lr"] * sum(G.tree_pred(t, r["b"]) for t in trees)
        raw_score = CFG["lr"] * sum(raw_tree_pred(t, r["x"]) for t in raw_trees)
        if binned_score != raw_score:
            mismatches += 1
            if mismatches <= 5:
                print(f"  MISMATCH word={r['word']!r} binned={binned_score} raw={raw_score}")
    print(f"validated {len(rows)} rows, {mismatches} mismatches")
    if mismatches:
        raise SystemExit("raw-threshold conversion does not match the binned model -- DO NOT ship this artifact")

    with open(MODEL_OUT, "w", encoding="utf-8") as fh:
        json.dump({
            "features": keep,
            "lr": CFG["lr"],
            "trees": raw_trees,
        }, fh, ensure_ascii=False)
    print(f"wrote {MODEL_OUT}")

    # golden fixture: N_FIXTURE_WORDS words' full candidate rows, feature
    # vectors + scores, for the Kotlin cross-language parity test.
    fixture_words = rng.sample(all_words, min(N_FIXTURE_WORDS, len(all_words)))
    fixture = []
    for w in fixture_words:
        for r in words[w]:
            fixture.append({
                "word": w,
                "parts": r["parts"],
                "feat": {k: r["feat"].get(k, 0.0) for k in keep},
                "x": r["x"],
                "score": r["S"],
                "label": r["label"],
            })
    with open(FIXTURE_OUT, "w", encoding="utf-8") as fh:
        json.dump(fixture, fh, ensure_ascii=False, indent=1)
    print(f"wrote {FIXTURE_OUT} ({len(fixture)} rows over {len(fixture_words)} words)")


if __name__ == "__main__":
    main()
