"""
Nested cross-validation for the FST decomposition re-ranker -- an honest
number where the held-out test fold influenced NO decision (not even the
bucket count / L2, which round 3 picked by eyeballing the CV).

Outer: 10-fold grouped by word (the real test).
Inner: 3-fold grouped on each outer-train set, picks (buckets, L2) from a
       small grid by mean fair-P@1, then the model is retrained on the full
       outer-train set with that choice and scored on the outer-test fold.

Fixed morpheme-frequency table (round 1 showed the per-fold --loo rebuild
moves the number ~0.2 pt -- not worth the nested-CV compute blow-up).
Negatives are subsampled (~8 per positive) for speed.

  python3 reranker_nested_cv.py [--drop GROUP ...]

Run from data/grammar/fst/ after build_reranker_table.py.
"""
import math
import random
import sys

import reranker_cv as R

SEED = R.SEED
FOLDS = 10
INNER = 3
EPOCHS = 30
LR = 0.05
GRID = [(b, l2) for b in (0, 8, 12) for l2 in (1e-4, 1e-3)]
NEG_PER_POS = 8


def train_param(words, train_words, n, l2, rng):
    w = [0.0] * n
    pairs = []
    for wd in train_words:
        cs = words[wd]
        pos = [r for r in cs if r["label"] == 1]
        neg = [r for r in cs if r["label"] == 0]
        for p in pos:
            for q in (neg if len(neg) <= NEG_PER_POS else rng.sample(neg, NEG_PER_POS)):
                pairs.append((p["xn"], q["xn"]))
    for _ in range(EPOCHS):
        rng.shuffle(pairs)
        for xp, xq in pairs:
            d = sum(w[i] * (xp[i] - xq[i]) for i in range(n))
            g = -1.0 / (1.0 + math.exp(d))
            for i in range(n):
                w[i] -= LR * (g * (xp[i] - xq[i]) + l2 * w[i])
    return w


def fit(rows, words, keep, train_words, buckets, l2, rng):
    train_rows = [r for r in rows if r["word"] in set(train_words)]
    R.make_xn(rows, train_rows, keep, buckets)   # sets r["xn"] for every row
    n = len(rows[0]["xn"])
    return train_param(words, train_words, n, l2, rng), n


def score(words, w, n, test_words):
    h1 = h3 = 0
    rr = 0.0
    for wd in test_words:
        a, b, c = R.score_eval(words[wd],
                               lambda r: sum(w[i] * r["xn"][i] for i in range(n)))
        h1 += a; h3 += b; rr += c
    return h1, h3, rr, len(test_words)


def graded_score(words, test_words, scorer):
    """correct@1 / R-precision / P@min(5,N) sums for the "cluster ALL correct
    decomps near the top" objective (see R.graded_metrics_eval). `scorer` is
    the model score fn `lambda r: sum(w[i]*r["xn"][i] ...)` or the hand-sort
    baseline `lambda r: -r["feat"]["rank_current_sort"]`."""
    rp = pn = c1 = 0.0
    cnt = 0
    for wd in test_words:
        a, b, c, scored = R.graded_metrics_eval(words[wd], scorer)
        if scored:
            rp += a; pn += b; c1 += c; cnt += 1
    return rp, pn, c1, cnt


def main():
    argv = sys.argv[1:]
    seed = int(argv[argv.index("--seed") + 1]) if "--seed" in argv else SEED
    drop = {a for a in argv if not a.startswith("--")} - {str(seed)}
    drop.discard("--drop")
    rows, words, keep = R.load(drop)
    R.add_r2l_features(rows, R.load_r2l_decomps())
    R.recompute_stats(rows, R.build_stats(R.load_cache_rows()))
    R.set_x(rows, keep)                    # r["x"] raw feature vector, fixed (no --loo)

    all_words = sorted(words)
    random.Random(seed).shuffle(all_words)
    fair_of = {w: words[w][0]["fair"] for w in all_words}
    outer = [all_words[i::FOLDS] for i in range(FOLDS)]

    tot = {"all": [0, 0, 0.0, 0], "fair": [0, 0, 0.0, 0]}
    # graded objective, fair only: [r_prec_sum, p_at_n_sum, correct@1_sum, n]
    graded = {"model": [0.0, 0.0, 0, 0], "hand": [0.0, 0.0, 0, 0]}
    picks = []
    rng = random.Random(seed)

    for oi, test in enumerate(outer):
        tr = [w for w in all_words if w not in set(test)]
        inner = [tr[i::INNER] for i in range(INNER)]
        best, best_score = None, -1.0
        for (buckets, l2) in GRID:
            s = tot_n = 0
            for ii in range(INNER):
                iva = inner[ii]
                itr = [w for w in tr if w not in set(iva)]
                iva_fair = [w for w in iva if fair_of[w]]
                w, n = fit(rows, words, keep, itr, buckets, l2, rng)
                s += score(words, w, n, iva_fair)[0]
                tot_n += len(iva_fair)
            sc = s / tot_n
            if sc > best_score:
                best_score, best = sc, (buckets, l2)
        picks.append(best)
        # retrain on full outer-train with the winning hyperparams, score outer-test once
        w, n = fit(rows, words, keep, tr, *best, rng)
        h1, h3, rr, nn = score(words, w, n, test)
        tot["all"][0] += h1; tot["all"][1] += h3; tot["all"][2] += rr; tot["all"][3] += nn
        tf = [x for x in test if fair_of[x]]
        fh1, fh3, frr, fn = score(words, w, n, tf)
        tot["fair"][0] += fh1; tot["fair"][1] += fh3; tot["fair"][2] += frr; tot["fair"][3] += fn
        for key, sc in (("model", lambda r: sum(w[i] * r["xn"][i] for i in range(n))),
                        ("hand", lambda r: -r["feat"]["rank_current_sort"])):
            grp, gpn, gc1, gn = graded_score(words, tf, sc)
            graded[key][0] += grp; graded[key][1] += gpn
            graded[key][2] += gc1; graded[key][3] += gn
        print(f"outer {oi}: picked buckets={best[0]} L2={best[1]}  "
              f"inner-fair-P@1={best_score:.3f}  outer-fair-P@1 so far "
              f"{tot['fair'][0]}/{tot['fair'][3]} ({100*tot['fair'][0]/max(tot['fair'][3],1):.1f}%)",
              flush=True)

    print(f"\nNESTED CV  (drop={sorted(drop) or 'none'})")
    print(f"hyperparam picks per outer fold: {picks}")
    for k in ("all", "fair"):
        h1, h3, rr, n = tot[k]
        print(f"  {k:5} P@1 {h1}/{n} ({100*h1/n:.1f}%)   P@3 {100*h3/n:.1f}%   MRR {rr/n:.3f}")
    print("reference: hand sort / R2L = 73.2% fair")

    print("\n-- graded objective: cluster ALL correct decomps near the top (fair) --")
    print("   (scored vs is_correct = reference OR in all_correct_decomps;")
    print("    correct@1 = any grammatical reading first, looser than P@1 above)")
    for key, name in (("model", "linear re-ranker"), ("hand", "current hand sort")):
        rp, pn, c1, n = graded[key]
        print(f"  {name:22} correct@1 {100*c1/n:.1f}%   R-precision {100*rp/n:.1f}%   "
              f"P@min(5,N) {100*pn/n:.1f}%   ({n} fair words)")


if __name__ == "__main__":
    main()
