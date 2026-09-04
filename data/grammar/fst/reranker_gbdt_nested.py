"""
Nested cross-validation for the stdlib mini-GBDT re-ranker -- the honest,
hyperparameter-clean number to compare against the bucketed-linear nested
result (~86% fair P@1).

Outer: 10-fold grouped by word (the real test).
Inner: 3-fold grouped on each outer-train set, picks (depth, trees) from a
       small grid by mean fair-P@1; the model is then retrained on the full
       outer-train set with that choice and scored on the outer-test fold.

The GBDT sweep (scratchpad/gbdt_sweep3.log) showed a broad plateau at
depth 4 / lr 0.1 / ~150 trees, so the inner grid only varies the one knob
that actually moved the number (tree depth, and trees to go with it);
lr/leaf/lambda/bins/subsampling are fixed at the settled values.

Binning edges are recomputed from the training rows of each fit (no label
leak, but keeps the "nested" claim honest). Negatives subsampled ~8/pos.

  python3 reranker_gbdt_nested.py [--drop GROUP ...] [--seed N]

Run from data/grammar/fst/ after build_reranker_table.py.
"""
import random
import sys

import reranker_cv as R
import reranker_gbdt as G

SEED = R.SEED
FOLDS = 10
INNER = 3

# (depth, trees) grid -- the only hyperparam that moved the sweep number.
GRID = [(3, 120), (4, 150), (4, 200)]

BASE_CFG = dict(lr=0.1, leaf=20, lam=1.0, neg=8, rowsub=0.4, featsub=0.6)
NBINS = 32


def edges_from(train_rows, keep, bins):
    """Quantile bin edges per feature, from train_rows only."""
    nf = len(keep)
    edges = []
    for j in range(nf):
        vals = sorted({r["x"][j] for r in train_rows})
        if len(vals) <= bins:
            edges.append(vals)
        else:
            xs = sorted(r["x"][j] for r in train_rows)
            edges.append([xs[int(len(xs) * q / bins)] for q in range(1, bins)])
    return edges


def assign_bins(rows, edges):
    nf = len(edges)
    for r in rows:
        r["b"] = tuple(G._bin(r["x"][j], edges[j]) for j in range(nf))


def fit(rows, words, keep, train_words, depth, trees, rng):
    """Bin on train rows only, train the GBDT, return its trees + lr."""
    train_set = set(train_words)
    train_rows = [r for r in rows if r["word"] in train_set]
    assign_bins(rows, edges_from(train_rows, keep, NBINS))
    cfg = dict(BASE_CFG, depth=depth, trees=trees)
    tr = G.train_gbdt(rows, words, list(train_words), len(keep), NBINS, cfg, rng)
    return tr, cfg["lr"]


def score(words, trees, lr, test_words):
    G.score_all([r for w in test_words for r in words[w]], trees, lr)
    h1 = h3 = 0
    rr = 0.0
    for wd in test_words:
        a, b, c = R.score_eval(words[wd], lambda r: r["S"])
        h1 += a; h3 += b; rr += c
    return h1, h3, rr


def main():
    argv = sys.argv[1:]
    seed = int(argv[argv.index("--seed") + 1]) if "--seed" in argv else SEED
    drop = {a for a in argv if not a.startswith("--")} - {str(seed)}
    drop.discard("--drop")
    drop = {d for d in drop if d in R.FEATURE_GROUPS}

    rows, words, keep = R.load(drop)
    R.add_r2l_features(rows, R.load_r2l_decomps())
    R.recompute_stats(rows, R.build_stats(R.load_cache_rows()))
    R.set_x(rows, keep)

    all_words = sorted(words)
    random.Random(seed).shuffle(all_words)
    fair_of = {w: words[w][0]["fair"] for w in all_words}
    outer = [all_words[i::FOLDS] for i in range(FOLDS)]
    rng = random.Random(seed)

    tot = {"all": [0, 0, 0.0, 0], "fair": [0, 0, 0.0, 0]}
    picks = []

    for oi, test in enumerate(outer):
        tr = [w for w in all_words if w not in set(test)]
        inner = [tr[i::INNER] for i in range(INNER)]
        best, best_score = None, -1.0
        for (depth, trees) in GRID:
            s = tot_n = 0
            for ii in range(INNER):
                iva = [w for w in inner[ii] if fair_of[w]]
                itr = [w for w in tr if w not in set(inner[ii])]
                trees_i, lr_i = fit(rows, words, keep, itr, depth, trees, rng)
                s += score(words, trees_i, lr_i, iva)[0]
                tot_n += len(iva)
            sc = s / tot_n
            if sc > best_score:
                best_score, best = sc, (depth, trees)
        picks.append(best)

        trees_o, lr_o = fit(rows, words, keep, tr, *best, rng)
        tf = [w for w in test if fair_of[w]]
        h1, h3, rr = score(words, trees_o, lr_o, test)
        fh1, fh3, frr = score(words, trees_o, lr_o, tf)
        tot["all"][0] += h1; tot["all"][1] += h3; tot["all"][2] += rr
        tot["all"][3] += len(test)
        tot["fair"][0] += fh1; tot["fair"][1] += fh3; tot["fair"][2] += frr
        tot["fair"][3] += len(tf)
        print(f"outer {oi}: picked depth={best[0]} trees={best[1]}  "
              f"inner-fair-P@1={best_score:.3f}  outer-fair-P@1 so far "
              f"{tot['fair'][0]}/{tot['fair'][3]} "
              f"({100*tot['fair'][0]/max(tot['fair'][3],1):.1f}%)", flush=True)

    print(f"\nGBDT NESTED CV  (drop={sorted(drop) or 'none'}  seed={seed})")
    print(f"hyperparam picks per outer fold: {picks}")
    for k in ("all", "fair"):
        h1, h3, rr, n = tot[k]
        print(f"  {k:5} P@1 {h1}/{n} ({100*h1/n:.1f}%)   P@3 {100*h3/n:.1f}%   "
              f"MRR {rr/n:.3f}")
    print("reference: hand sort / R2L 73.2% fair ; bucketed-linear ~86% (nested CV) ;"
          " GBDT flat CV ~91.3% fair")


if __name__ == "__main__":
    main()
