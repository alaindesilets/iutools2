"""
Grouped 10-fold CV for the FST decomposition re-ranker baseline.

Model: linear score s = w.x, trained with PAIRWISE logistic loss (for each
word, every (correct, incorrect) candidate pair contributes
log(1+exp(-(s_correct - s_incorrect)))), plain SGD + L2. Pure standard
library -- a baseline to see whether a learned re-ranker can beat the
current 5-key hand sort (which already ~= R2L: 671/919 fair
first-decomposition-correct); LightGBM lambdarank is worth installing only
if this is promising.

  python3 reranker_cv.py [--drop GROUP ...] [--loo]

--drop : ablate FEATURE_GROUPS.
--loo  : rebuild the morpheme-frequency table PER FOLD from the 10k Hansard
         cache with that fold's gold words removed, and recompute the freq
         features -- removes the "the freq prior already saw the eval word"
         optimism (981 of 985 gold words are in that 10k cache).

Reports P@1 / P@3 / MRR over held-out folds, overall + fair-only, vs the
current sort and an oracle ceiling. Run from tools/fst/ after
build_reranker_table.py.
"""
import json
import math
import random
import re
import sys
from collections import Counter

TABLE = "scratchpad/reranker_table.jsonl"
FOLDS = 10
EPOCHS = 40
LR = 0.05
L2 = 1e-4
SEED = 20260902

FEATURE_GROUPS = {
    "shape": ["n_morphemes", "n_nonroot", "n_deriv", "n_ending", "n_consec_dup_id",
              "wordlen_per_morph", "root_canon_len", "mean_canon_len", "min_canon_len",
              "n_thin_canon"],
    "cat": ["n_vv", "n_nn", "n_vn", "n_nv", "cat_switches", "type_violations",
            "ends_with_ending", "ends_with_bare_deriv"],
    "surface": ["edit_dist", "edit_dist_norm", "concat_len_minus_word",
                "first_canon_is_prefix"],
    "relative": ["rank_current_sort", "n_candidates", "edit_dist_rank_in_word",
                 "n_morphemes_rank_in_word", "root_canon_len_rank_in_word",
                 "type_violations_rank_in_word"],
    "weight": ["weight"],
    "freq": ["freq_sum", "freq_mean", "freq_min", "freq_root", "n_zero_freq"],
    "bigram": ["bigram_min", "bigram_mean", "bigram_nzero", "bigram_root_first"],
    "backoff": ["root_oov", "root_id_freq", "root_charbi"],
    "r2l": ["r2l_available", "r2l_top1", "r2l_produced", "r2l_rank_inv"],
}
_DECOMP_RE = re.compile(r"\{([^:}]+):([^/}]+)/([^}]+)\}")


# --- per-word lookup: what did R2L (Uqailaut) produce for this word? -------
# Not a corpus statistic and not trained on the gold -> no --loo rebuild.
def load_r2l_decomps():
    syl = [l.rstrip("\n") for l in open("hansard-cache/top10k_words.txt", encoding="utf-8")]
    rom = [l.rstrip("\n") for l in open("hansard-cache/top10k_words_roman.txt", encoding="utf-8")]
    s2r = dict(zip(syl, rom))
    out = {}
    with open("hansard-cache/top10k_words_benoit_decomps.jsonl", encoding="utf-8") as fh:
        for line in fh:
            rec = json.loads(line)
            decs = rec.get("decompositions") or []
            out[s2r.get(rec["word"], rec["word"])] = [
                [[c, i] for _s, c, i in _DECOMP_RE.findall(d)] for d in decs
            ]
    return out


def add_r2l_features(rows, r2l):
    for r in rows:
        decs = r2l.get(r["word"])
        if decs is None:
            r["feat"].update(r2l_available=0, r2l_top1=0, r2l_produced=0, r2l_rank_inv=0.0)
            continue
        try:
            rank = decs.index(r["parts"])
        except ValueError:
            rank = -1
        r["feat"].update(
            r2l_available=1,
            r2l_top1=int(bool(decs) and decs[0] == r["parts"]),
            r2l_produced=int(rank >= 0),
            r2l_rank_inv=(1.0 / (rank + 1)) if rank >= 0 else 0.0,
        )


# --- identity-derived count tables (frequency / bigram / OOV back-off) -----
def build_stats(cache_rows, exclude=frozenset()):
    ci, big, prev, root_id, char_bi = (Counter() for _ in range(5))
    for rom, dec in cache_rows:
        if rom in exclude:
            continue
        parts = [(c, i) for _s, c, i in _DECOMP_RE.findall(dec)]
        if not parts:
            continue
        for c, i in parts:
            ci[(c, i)] += 1
        ids = [i for _, i in parts]
        for a, b in zip(ids, ids[1:]):
            big[(a, b)] += 1
            prev[a] += 1
        root_id[ids[0]] += 1
        rc = parts[0][0]
        for cb in (rc[k:k + 2] for k in range(len(rc) - 1)):
            char_bi[cb] += 1
    return {"ci": ci, "big": big, "prev": prev, "root_id": root_id, "char_bi": char_bi}


def recompute_stats(rows, S):
    ci, big, prev, root_id, char_bi = (S[k] for k in ("ci", "big", "prev", "root_id", "char_bi"))
    for r in rows:
        parts = r["parts"]
        n = len(parts) or 1
        fq = [ci.get((c, i), 0) for c, i in parts]
        ids = [i for _, i in parts]
        trans = [big.get((a, b), 0) / prev[a] if prev.get(a) else 0.0
                 for a, b in zip(ids, ids[1:])]
        rc = parts[0][0] if parts else ""
        rcb = [char_bi.get(rc[k:k + 2], 0) for k in range(len(rc) - 1)]
        r["feat"].update({
            "freq_sum": sum(fq), "freq_mean": sum(fq) / n, "freq_min": min(fq) if fq else 0,
            "freq_root": fq[0] if fq else 0, "n_zero_freq": sum(1 for v in fq if v == 0),
            "bigram_min": min(trans) if trans else 1.0,
            "bigram_mean": sum(trans) / len(trans) if trans else 1.0,
            "bigram_nzero": sum(1 for t in trans if t == 0.0),
            "bigram_root_first": trans[0] if trans else 1.0,
            "root_oov": int((fq[0] if fq else 0) == 0),
            "root_id_freq": root_id.get(ids[0], 0) if ids else 0,
            "root_charbi": sum(rcb) / len(rcb) if rcb else 0.0,
        })


# Within-word relative frequency -- TRIED AND REJECTED (see
# reranker-experiment.md, "Piste 1"). The hypothesis was that telling the
# model how *decisive* a candidate's frequency lead is, relative to the
# other candidates of the same word, would stop it chasing a marginal lead
# (the residual bias the error analysis found). Every ablation came out
# <= baseline (GBDT 91.3 -> 91.1, linear+buckets 86.8 -> 86.3). Kept here,
# but NOT in FEATURE_GROUPS and NOT called by recompute_stats: to re-test,
# add RELFREQ_GROUP to FEATURE_GROUPS and call add_relfreq_features(rows) at
# the end of recompute_stats.
RELFREQ_GROUP = {
    "relfreq": ["freq_root_rank", "freq_sum_rank", "freq_root_rel", "freq_sum_rel",
                "freq_root_is_uniq_max", "freq_root_margin", "freq_root_trap"],
}


def add_relfreq_features(rows):
    """Within-word relative frequency signal (opt-in -- see RELFREQ_GROUP).
    Call at the end of recompute_stats so --loo rebuilds it per fold too --
    depends on the freq_root / freq_sum written there. For each word:
      *_rank            0-based rank, DESC (0 = highest-frequency candidate)
      *_rel             value / max value among the word's candidates (0..1)
      freq_root_is_uniq_max  1 iff this candidate alone holds the top freq_root
      freq_root_margin  (top freq_root - 2nd distinct) / top   -- only the
                        leader gets a positive value; how decisive its lead is
      freq_root_trap    1 iff this candidate is BOTH the unique min edit_dist
                        AND the unique max freq_root -- the configuration the
                        error analysis showed the ranker falls for
    """
    by_word = {}
    for r in rows:
        by_word.setdefault(r["word"], []).append(r)
    for group in by_word.values():
        for key in ("freq_root", "freq_sum"):
            vals = [g["feat"].get(key, 0.0) for g in group]
            mx = max(vals) if vals else 0.0
            order = sorted(range(len(group)), key=lambda k: -vals[k])
            for rank, k in enumerate(order):
                group[k]["feat"][f"{key}_rank"] = rank
            for g, v in zip(group, vals):
                g["feat"][f"{key}_rel"] = v / mx if mx else 0.0
        froots = [g["feat"].get("freq_root", 0.0) for g in group]
        eds = [g["feat"].get("edit_dist", 0) for g in group]
        mx = max(froots) if froots else 0.0
        n_at_max = sum(1 for v in froots if v == mx)
        distinct_desc = sorted(set(froots), reverse=True)
        second = distinct_desc[1] if len(distinct_desc) > 1 else 0.0
        # decisiveness of the top freq_root, gap to the next DISTINCT level
        # (ending-variant pairs tie at the same freq_root, so don't demand
        # strict uniqueness for the margin -- only for the "uniq max" flag)
        margin = (mx - second) / mx if mx else 0.0
        min_ed = min(eds) if eds else 0
        for g in group:
            fr = g["feat"].get("freq_root", 0.0)
            at_max = fr == mx and mx > 0
            g["feat"]["freq_root_is_uniq_max"] = int(at_max and n_at_max == 1)
            g["feat"]["freq_root_margin"] = margin if at_max else 0.0
            g["feat"]["freq_root_trap"] = int(
                at_max and g["feat"].get("edit_dist", 0) == min_ed)


def load(drop):
    rows = [json.loads(l) for l in open(TABLE, encoding="utf-8")]
    keep = [f for g, fs in FEATURE_GROUPS.items() if g not in drop for f in fs]
    words = {}
    for r in rows:
        words.setdefault(r["word"], []).append(r)
    return rows, words, keep


def set_x(rows, keep):
    for r in rows:
        r["x"] = [float(r["feat"].get(k, 0.0)) for k in keep]


def make_xn(rows, train_rows, keep, buckets):
    """Standardize x -> xn. If buckets>0, replace each feature that has many
    distinct values (continuous) with `buckets` one-hot quantile indicators
    (edges from train_rows only); small-cardinality features stay linear.
    Lets the linear model approximate non-linear feature responses."""
    n = len(keep)
    if buckets <= 0:
        mean, std = standardize(train_rows, n)
        for r in rows:
            r["xn"] = [(r["x"][i] - mean[i]) / std[i] for i in range(n)]
        return
    cont = [i for i in range(n) if len({r["x"][i] for r in train_rows}) > 6]
    lin = [i for i in range(n) if i not in cont]
    edges = {}
    for i in cont:
        vals = sorted(r["x"][i] for r in train_rows)
        edges[i] = [vals[int(len(vals) * q / buckets)] for q in range(1, buckets)]
    lm = [sum(r["x"][i] for r in train_rows) / len(train_rows) for i in lin]
    ls = [max(1e-9, (sum((r["x"][i] - lm[k]) ** 2 for r in train_rows) / len(train_rows)) ** 0.5)
          for k, i in enumerate(lin)]
    for r in rows:
        xn = [(r["x"][i] - lm[k]) / ls[k] for k, i in enumerate(lin)]
        for i in cont:
            b = sum(1 for e in edges[i] if r["x"][i] >= e)
            xn.extend(1.0 if j == b else 0.0 for j in range(buckets))
        r["xn"] = xn


# --- --loo: per-fold morpheme-frequency table ------------------------------
def load_cache_rows():
    syl = [l.rstrip("\n") for l in open("hansard-cache/top10k_words.txt", encoding="utf-8")]
    rom = [l.rstrip("\n") for l in open("hansard-cache/top10k_words_roman.txt", encoding="utf-8")]
    syl2rom = dict(zip(syl, rom))
    out = []
    with open("hansard-cache/top10k_words_benoit_decomps.jsonl", encoding="utf-8") as fh:
        for line in fh:
            rec = json.loads(line)
            decs = rec.get("decompositions")
            if not decs:
                continue
            out.append((syl2rom.get(rec["word"], rec["word"]), decs[0]))
    return out


# --- model ---------------------------------------------------------------
def standardize(rows, n):
    cols = [[r["x"][i] for r in rows] for i in range(n)]
    mean = [sum(c) / len(c) for c in cols]
    std = [max(1e-9, (sum((v - mean[i]) ** 2 for v in cols[i]) / len(cols[i])) ** 0.5)
           for i in range(n)]
    return mean, std


def train(words, train_words, n):
    w = [0.0] * n
    pairs = []
    for wd in train_words:
        cs = words[wd]
        for p in (r for r in cs if r["label"] == 1):
            for q in (r for r in cs if r["label"] == 0):
                pairs.append((p["xn"], q["xn"]))
    rng = random.Random(SEED)
    for _ in range(EPOCHS):
        rng.shuffle(pairs)
        for xp, xq in pairs:
            d = sum(w[i] * (xp[i] - xq[i]) for i in range(n))
            g = -1.0 / (1.0 + math.exp(d))
            for i in range(n):
                w[i] -= LR * (g * (xp[i] - xq[i]) + L2 * w[i])
    return w


def train_listwise(words, train_words, n):
    """Softmax cross-entropy over each word's candidate set toward its
    correct candidate(s). SGD + L2."""
    w = [0.0] * n
    groups = [[(r["xn"], r["label"]) for r in words[wd]] for wd in train_words]
    rng = random.Random(SEED)
    for _ in range(EPOCHS):
        rng.shuffle(groups)
        for g in groups:
            s = [sum(w[i] * x[i] for i in range(n)) for x, _ in g]
            m = max(s)
            ex = [math.exp(v - m) for v in s]
            Z = sum(ex)
            soft = [e / Z for e in ex]
            npos = sum(lab for _, lab in g) or 1
            for j, (x, lab) in enumerate(g):
                gj = soft[j] - (lab / npos)          # dCE/ds_j
                for i in range(n):
                    w[i] -= LR * (gj * x[i] + L2 * w[i])
    return w


def score_eval(cs, scorer):
    order = sorted(cs, key=lambda r: -scorer(r))
    ranks = [i for i, r in enumerate(order) if r["label"] == 1]
    if not ranks:
        return 0, 0, 0.0
    b = min(ranks)
    return int(b == 0), int(b < 3), 1.0 / (b + 1)


def eval_detail(cs, scorer):
    """(hit1, rank_of_first_correct 0-based, top_pick_row, correct_row)."""
    order = sorted(cs, key=lambda r: -scorer(r))
    ranks = [i for i, r in enumerate(order) if r["label"] == 1]
    b = min(ranks) if ranks else len(order)
    correct = next((r for r in order if r["label"] == 1), None)
    return int(b == 0), b, order[0], correct


def main():
    args = sys.argv[1:]
    loo = "--loo" in args
    dump = [] if "--dump" in args else None
    ensemble = "--ensemble" in args
    drop = set(args[args.index("--drop") + 1:]) if "--drop" in args else set()
    listwise = "--listwise" in args
    buckets = int(args[args.index("--buckets") + 1]) if "--buckets" in args else 0
    for f in ("--loo", "--dump", "--ensemble", "--listwise", "--buckets", str(buckets)):
        drop.discard(f)

    rows, words, keep = load(drop)
    all_words = sorted(words)
    random.Random(SEED).shuffle(all_words)
    folds = [all_words[i::FOLDS] for i in range(FOLDS)]
    cache_rows = load_cache_rows()
    add_r2l_features(rows, load_r2l_decomps())
    if not loo:                       # fixed table (all cache words) once
        recompute_stats(rows, build_stats(cache_rows))
    trainer = train_listwise if listwise else train

    agg = {k: [0, 0, 0.0, 0] for k in ("m_all", "m_fair", "c_all", "c_fair")}
    LAMBDAS = [0.0, 0.05, 0.1, 0.15, 0.2, 0.3, 0.5, 1.0, 2.0]
    TOPK = [1, 2, 3, 4, 5, 6, 8, 12, 999]
    ens_fuse = {lam: 0 for lam in LAMBDAS}   # rank-fusion P@1 (fair)
    ens_topk = {k: 0 for k in TOPK}          # rerank hand-sort top-k (fair)
    ens_n = 0

    def bump(k, h1, h3, rr):
        agg[k][0] += h1; agg[k][1] += h3; agg[k][2] += rr; agg[k][3] += 1

    for test in folds:
        test_set = set(test)
        if loo:
            recompute_stats(rows, build_stats(cache_rows, test_set))
        set_x(rows, keep)
        train_rows = [r for r in rows if r["word"] not in test_set]
        make_xn(rows, train_rows, keep, buckets)
        wv = trainer(words, [w for w in all_words if w not in test_set], len(rows[0]["xn"]))
        for w in test:
            cs = words[w]
            fair = cs[0]["fair"]
            mscore = lambda r: sum(wv[i] * r["xn"][i] for i in range(len(wv)))
            h = score_eval(cs, mscore)
            bump("m_all", *h)
            if fair:
                bump("m_fair", *h)
            c = score_eval(cs, lambda r: -r["feat"]["rank_current_sort"])
            bump("c_all", *c)
            if fair:
                bump("c_fair", *c)
            if ensemble and fair:
                ens_n += 1
                # model rank within word (0 = best by model)
                by_m = sorted(range(len(cs)), key=lambda k: -mscore(cs[k]))
                mrank_of = {k: i for i, k in enumerate(by_m)}
                hrank_of = {k: cs[k]["feat"]["rank_current_sort"] for k in range(len(cs))}
                for lam in LAMBDAS:
                    pick = min(range(len(cs)),
                               key=lambda k: mrank_of[k] + lam * hrank_of[k])
                    ens_fuse[lam] += cs[pick]["label"]
                hand_order = sorted(range(len(cs)), key=lambda k: hrank_of[k])
                for k in TOPK:
                    window = hand_order[:k]
                    pick = max(window, key=lambda j: mscore(cs[j]))
                    ens_topk[k] += cs[pick]["label"]
            if dump is not None:
                mh1, mrank, mtop, corr = eval_detail(cs, mscore)
                ch1, crank, ctop, _ = eval_detail(cs, lambda r: -r["feat"]["rank_current_sort"])
                dump.append({
                    "word": w, "fair": fair, "n_cand": len(cs),
                    "model_hit1": mh1, "model_rank": mrank,
                    "cur_hit1": ch1, "cur_rank": crank,
                    "correct": corr["feat"] if corr else None,
                    "model_pick": mtop["feat"], "model_pick_correct": mtop["label"],
                    "cur_pick": ctop["feat"], "cur_pick_correct": ctop["label"],
                })

    def line(name, a):
        h1, h3, rr, n = a
        print(f"  {name:12} P@1 {h1:4d}/{n} ({100*h1/n:5.1f}%)   P@3 {100*h3/n:5.1f}%   MRR {rr/n:.3f}")

    n_fair = sum(1 for w in all_words if words[w][0]["fair"])
    print(f"\n{len(all_words)} words ({n_fair} fair), grouped {FOLDS}-fold CV | "
          f"drop={sorted(drop) or 'none'} | loo={loo}")
    print("\n-- learned re-ranker (pairwise-logistic) --")
    line("all", agg["m_all"]); line("fair", agg["m_fair"])
    print("\n-- current 5-key hand sort --")
    line("all", agg["c_all"]); line("fair", agg["c_fair"])
    print("\nreference: R2L 673/919 fair first-correct (73.2%)")

    if ensemble:
        print(f"\n-- ensemble (fair, {ens_n} words; pure model {agg['m_fair'][0]}, "
              f"hand sort {agg['c_fair'][0]}) --")
        print("  rank-fusion  fused = model_rank + lambda*hand_rank:")
        for lam in LAMBDAS:
            v = ens_fuse[lam]
            print(f"    lambda={lam:<4}  P@1 {v}/{ens_n} ({100*v/ens_n:5.1f}%)")
        print("  rerank hand-sort top-k by model score:")
        for k in TOPK:
            v = ens_topk[k]
            kk = "all" if k == 999 else str(k)
            print(f"    k={kk:<4}       P@1 {v}/{ens_n} ({100*v/ens_n:5.1f}%)")

    if dump is not None:
        with open("scratchpad/reranker_cv_detail.jsonl", "w", encoding="utf-8") as fh:
            for d in dump:
                fh.write(json.dumps(d, ensure_ascii=False) + "\n")
        print(f"\nwrote scratchpad/reranker_cv_detail.jsonl ({len(dump)} words)")


if __name__ == "__main__":
    main()
