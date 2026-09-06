"""
Pure-stdlib gradient-boosted decision trees for the FST decomposition
re-ranker -- does a real (nonlinear, interaction-capable) GBDT beat the
bucketed-linear model (~86% fair P@1, nested CV)?

Model:  F(x) = sum_t  lr * tree_t(x)
Loss:   pairwise logistic on the margin m = F(x_pos) - F(x_neg), per word.
        each round: accumulate per-instance grad/hess from its pairs, fit one
        histogram regression tree to the negative gradient with XGBoost-style
        Newton leaf values  (leaf = -sum g / (sum h + lambda)).
Speed:  every feature pre-binned into BINS quantile bins once; split-finding
        is histogram accumulation (O(instances) per tree level).

Grouped 10-fold CV (flat, not nested -- run reranker_nested_cv-style later if
this is promising). Reports P@1/P@3/MRR, fair + all, vs hand sort / R2L 73.2%.

  python3 reranker_gbdt.py [--drop GROUP ...] [--trees N] [--depth D]
                           [--lr F] [--bins N] [--leaf N] [--lambda F]
                           [--target reference|correct|graded]

--target : pairwise-loss objective. "reference" (default) ranks only the
           Hansard-attested decomp first; "correct" ranks the whole correct
           set first (is_correct = reference OR in all_correct_decomps);
           "graded" is 3-tier (reference > other-correct > incorrect) --
           reference first AND the rest of the correct set right behind it.
           All report reference P@1/P@3/MRR AND graded R-precision / P@min(5,N).
Run from data/grammar/fst/ after build_reranker_table.py.
"""
import math
import random
import sys

import reranker_cv as R

SEED = R.SEED
FOLDS = 10


# ------------------------------------------------------------------ binning
def prebin(rows, keep, bins):
    """rows[i]['b'] = tuple of int bin indices (one per feature)."""
    nf = len(keep)
    edges = []
    for j in range(nf):
        vals = sorted({r["x"][j] for r in rows})
        if len(vals) <= bins:
            edges.append(vals)                       # one bin per distinct value
        else:
            xs = sorted(r["x"][j] for r in rows)
            edges.append([xs[int(len(xs) * q / bins)] for q in range(1, bins)])
    for r in rows:
        r["b"] = tuple(_bin(r["x"][j], edges[j]) for j in range(nf))
    return edges


def _bin(v, es):
    lo, hi = 0, len(es)
    while lo < hi:
        mid = (lo + hi) // 2
        if v < es[mid]:
            hi = mid
        else:
            lo = mid + 1
    return lo


# ------------------------------------------------------------------ tree
class Node:
    __slots__ = ("feat", "thr", "left", "right", "val")


def build_tree(idx, g, h, B, nf, nbins, depth, max_depth, min_leaf, lam, featsub, rng):
    node = Node()
    G = sum(g[i] for i in idx)
    H = sum(h[i] for i in idx)
    node.val = -G / (H + lam)
    if depth >= max_depth or len(idx) < 2 * min_leaf:
        node.feat = None
        return node
    base = (G * G) / (H + lam)
    best = (0.0, None, None)                          # gain, feat, thr(bin)
    feats = range(nf) if featsub >= 1.0 else rng.sample(
        range(nf), max(1, int(nf * featsub)))
    for j in feats:
        hg = [0.0] * (nbins + 1)
        hh = [0.0] * (nbins + 1)
        hc = [0] * (nbins + 1)
        for i in idx:
            b = B[i][j]
            hg[b] += g[i]; hh[b] += h[i]; hc[b] += 1
        gl = hl = 0.0
        cl = 0
        maxb = max(b for b in range(nbins + 1) if hc[b])
        for b in range(maxb):
            gl += hg[b]; hl += hh[b]; cl += hc[b]
            if cl < min_leaf or (len(idx) - cl) < min_leaf:
                continue
            gr = G - gl; hr = H - hl
            gain = (gl * gl) / (hl + lam) + (gr * gr) / (hr + lam) - base
            if gain > best[0]:
                best = (gain, j, b)
    if best[1] is None or best[0] <= 1e-9:
        node.feat = None
        return node
    _, j, b = best
    node.feat, node.thr = j, b
    li = [i for i in idx if B[i][j] <= b]
    ri = [i for i in idx if B[i][j] > b]
    node.left = build_tree(li, g, h, B, nf, nbins, depth + 1, max_depth, min_leaf, lam, featsub, rng)
    node.right = build_tree(ri, g, h, B, nf, nbins, depth + 1, max_depth, min_leaf, lam, featsub, rng)
    return node


def tree_pred(node, b):
    while node.feat is not None:
        node = node.left if b[node.feat] <= node.thr else node.right
    return node.val


# ------------------------------------------------------------------ boosting
def train_gbdt(rows, words, train_words, nf, nbins, cfg, rng, target="label"):
    """target (see reranker_cv.relevance): "label" ranks only the Hansard
    reference decomp first; "is_correct" ranks the whole correct set first;
    "graded" is 3-tier (reference > other-correct > incorrect)."""
    tw = set(train_words)
    tr_rows = [r for r in rows if r["word"] in tw]
    for r in tr_rows:
        r["F"] = 0.0
    # (higher-relevance group, lower-relevance group) per word. Binary targets
    # give one pair (correct, incorrect); "graded" gives up to three
    # (reference > other-correct > incorrect) so the boosting ranks the
    # reference decomp first AND the rest of the correct set right behind it.
    pairs_by_word = []
    for wd in train_words:
        cs = words[wd]
        rel = [R.relevance(r, target) for r in cs]
        tiers = sorted({v for v in rel}, reverse=True)
        by_tier = {t: [cs[i] for i in range(len(cs)) if rel[i] == t] for t in tiers}
        for hi_i in range(len(tiers)):
            for lo_i in range(hi_i + 1, len(tiers)):
                hi, lo = by_tier[tiers[hi_i]], by_tier[tiers[lo_i]]
                if hi and lo:
                    pairs_by_word.append((hi, lo))
    trees = []
    for _ in range(cfg["trees"]):
        g = {}; h = {}
        for r in tr_rows:
            g[id(r)] = 0.0; h[id(r)] = 0.0
        for hi, lo in pairs_by_word:
            nsamp = min(len(lo), cfg["neg"])
            ns = lo if len(lo) <= cfg["neg"] else rng.sample(lo, nsamp)
            for p in hi:
                for q in ns:
                    m = p["F"] - q["F"]
                    s = 1.0 / (1.0 + math.exp(m))     # sigma(-m)
                    hs = s * (1.0 - s)
                    g[id(p)] -= s;  g[id(q)] += s
                    h[id(p)] += hs; h[id(q)] += hs
        # index list + arrays keyed by position; row-subsample for speed + reg
        idx = list(range(len(tr_rows)))
        if cfg["rowsub"] < 1.0:
            k = max(cfg["leaf"] * 4, int(len(idx) * cfg["rowsub"]))
            idx = rng.sample(idx, min(k, len(idx)))
        gg = [g[id(tr_rows[i])] for i in range(len(tr_rows))]
        hh = [h[id(tr_rows[i])] + 1e-6 for i in range(len(tr_rows))]
        B = [tr_rows[i]["b"] for i in range(len(tr_rows))]
        t = build_tree(idx, gg, hh, B, nf, nbins,
                       0, cfg["depth"], cfg["leaf"], cfg["lam"], cfg["featsub"], rng)
        for r in tr_rows:
            r["F"] += cfg["lr"] * tree_pred(t, r["b"])
        trees.append(t)
    return trees


def score_all(rows, trees, lr):
    for r in rows:
        r["S"] = lr * sum(tree_pred(t, r["b"]) for t in trees)


def evaluate(words, test_words, fair_of):
    tot = {"all": [0, 0, 0.0, 0], "fair": [0, 0, 0.0, 0]}
    graded = {"model": [0.0, 0.0, 0, 0], "hand": [0.0, 0.0, 0, 0]}   # fair only
    for wd in test_words:
        a, b, c = R.score_eval(words[wd], lambda r: r["S"])
        for k in ("all",) + (("fair",) if fair_of[wd] else ()):
            tot[k][0] += a; tot[k][1] += b; tot[k][2] += c; tot[k][3] += 1
        if fair_of[wd]:
            for key, sc in (("model", lambda r: r["S"]),
                            ("hand", lambda r: -r["feat"]["rank_current_sort"])):
                rp, pn, c1, scored = R.graded_metrics_eval(words[wd], sc)
                if scored:
                    graded[key][0] += rp; graded[key][1] += pn
                    graded[key][2] += c1; graded[key][3] += 1
    return tot, graded


def main():
    argv = sys.argv[1:]

    def opt(name, default, cast):
        return cast(argv[argv.index(name) + 1]) if name in argv else default

    cfg = dict(
        trees=opt("--trees", 200, int), depth=opt("--depth", 3, int),
        lr=opt("--lr", 0.1, float), leaf=opt("--leaf", 20, int),
        lam=opt("--lambda", 1.0, float), neg=opt("--neg", 8, int),
        rowsub=opt("--rowsub", 0.4, float), featsub=opt("--featsub", 0.6, float),
    )
    nbins = opt("--bins", 32, int)
    target_arg = opt("--target", "reference", str)
    target = {"correct": "is_correct", "graded": "graded"}.get(target_arg, "label")
    drop = {a for a in argv if not a.startswith("--")}
    for v in ("--drop",):
        drop.discard(v)
    # strip numeric option values / --target value that leaked into `drop`
    drop = {d for d in drop if d in R.FEATURE_GROUPS}

    rows, words, keep = R.load(drop)
    R.add_r2l_features(rows, R.load_r2l_decomps())
    R.recompute_stats(rows, R.build_stats(R.load_cache_rows()))
    R.set_x(rows, keep)
    prebin(rows, keep, nbins)
    nf = len(keep)

    all_words = sorted(words)
    random.Random(SEED).shuffle(all_words)
    fair_of = {w: words[w][0]["fair"] for w in all_words}
    folds = [all_words[i::FOLDS] for i in range(FOLDS)]
    rng = random.Random(SEED)

    agg = {"all": [0, 0, 0.0, 0], "fair": [0, 0, 0.0, 0]}
    gtot = {"model": [0.0, 0.0, 0, 0], "hand": [0.0, 0.0, 0, 0]}
    for fi, test in enumerate(folds):
        tr = [w for w in all_words if w not in set(test)]
        trees = train_gbdt(rows, words, tr, nf, nbins, cfg, rng, target)
        score_all([r for w in test for r in words[w]], trees, cfg["lr"])
        t, g = evaluate(words, test, fair_of)
        for k in ("all", "fair"):
            for j in range(4):
                agg[k][j] += t[k][j]
        for k in ("model", "hand"):
            for j in range(4):
                gtot[k][j] += g[k][j]
        h1, _, _, n = agg["fair"]
        print(f"fold {fi}: fair-P@1 so far {h1}/{n} ({100*h1/max(n,1):.1f}%)", flush=True)

    print(f"\nGBDT  drop={sorted(drop) or 'none'}  cfg={cfg} bins={nbins} target={target}")
    for k in ("all", "fair"):
        h1, h3, rr, n = agg[k]
        print(f"  {k:5} P@1 {h1}/{n} ({100*h1/n:.1f}%)   P@3 {100*h3/n:.1f}%   MRR {rr/n:.3f}")
    print("reference: hand sort / R2L 73.2% fair ; bucketed-linear ~86% (nested CV)")
    print("\n-- graded objective: cluster ALL correct decomps near the top (fair) --")
    print("   (scored vs is_correct = reference OR in all_correct_decomps;")
    print("    correct@1 = any grammatical reading first, looser than reference P@1 above)")
    for key, name in (("model", "GBDT re-ranker"), ("hand", "current hand sort")):
        rp, pn, c1, n = gtot[key]
        print(f"  {name:22} correct@1 {100*c1/n:.1f}%   R-precision {100*rp/n:.1f}%   "
              f"P@min(5,N) {100*pn/n:.1f}%   ({n} fair words)")


if __name__ == "__main__":
    main()
