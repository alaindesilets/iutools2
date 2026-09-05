"""
Error analysis for the settled GBDT re-ranker (trees 150, depth 4, lr 0.1,
FST-only / --drop r2l).  Flat 10-fold CV so every word is held out once;
for each fair word records where the first correct candidate landed, plus
the feature vectors of the model's pick and of the correct parse.

Prints:
  * miss taxonomy (rank-2 near-miss vs rank-3 vs out-of-top-3)
  * miss-rate conditioned on freq_root==0 / edit_dist>=3 / >=3 morphemes /
    >=20 candidates
  * what the model's wrong pick systematically has that the correct parse
    lacks (mean feature deltas over misses)
  * overlap with the hand-sort's own misses (shared vs GBDT-only regressions)
Full per-word detail -> scratchpad/gbdt_errors.jsonl

  python3 reranker_gbdt_errors.py

Run from data/grammar/fst/ after build_reranker_table.py.
"""
import json
import random
import statistics

import reranker_cv as R
import reranker_gbdt as G

SEED = R.SEED
FOLDS = 10
NBINS = 32
CFG = dict(trees=150, depth=4, lr=0.1, leaf=20, lam=1.0,
           neg=8, rowsub=0.4, featsub=0.6)


def main():
    rows, words, keep = R.load({"r2l"})
    R.add_r2l_features(rows, R.load_r2l_decomps())
    R.recompute_stats(rows, R.build_stats(R.load_cache_rows()))
    R.set_x(rows, keep)
    G.prebin(rows, keep, NBINS)
    nf = len(keep)

    all_words = sorted(words)
    random.Random(SEED).shuffle(all_words)
    folds = [all_words[i::FOLDS] for i in range(FOLDS)]
    rng = random.Random(SEED)

    recs = []
    for fi, test in enumerate(folds):
        tr = [w for w in all_words if w not in set(test)]
        trees = G.train_gbdt(rows, words, tr, nf, NBINS, CFG, rng)
        G.score_all([r for w in test for r in words[w]], trees, CFG["lr"])
        for w in test:
            cs = words[w]
            if not cs[0]["fair"]:
                continue
            mh1, mrank, mtop, corr = R.eval_detail(cs, lambda r: r["S"])
            ch1, crank, _, _ = R.eval_detail(
                cs, lambda r: -r["feat"]["rank_current_sort"])
            recs.append({
                "word": w, "n_cand": len(cs),
                "model_hit1": mh1, "model_rank": mrank,
                "cur_hit1": ch1, "cur_rank": crank,
                "pick": mtop["feat"], "pick_correct": mtop["label"],
                "correct": corr["feat"] if corr else None,
            })
        print(f"fold {fi} done ({len(recs)} fair words so far)", flush=True)

    with open("scratchpad/gbdt_errors.jsonl", "w", encoding="utf-8") as fh:
        for r in recs:
            fh.write(json.dumps(r) + "\n")

    n = len(recs)
    miss = [r for r in recs if not r["model_hit1"]]
    hit = n - len(miss)
    print(f"\n{n} fair words | GBDT P@1 {hit}/{n} ({100*hit/n:.1f}%) | "
          f"misses {len(miss)}")

    # --- miss taxonomy by where the correct parse landed -------------------
    by_rank = {"rank2": 0, "rank3": 0, "out_top3": 0}
    for r in miss:
        if r["model_rank"] == 1:
            by_rank["rank2"] += 1
        elif r["model_rank"] == 2:
            by_rank["rank3"] += 1
        else:
            by_rank["out_top3"] += 1
    print("\nwhere the correct parse landed on a miss:")
    for k, v in by_rank.items():
        print(f"  {k:9} {v:3d}  ({100*v/len(miss):.0f}%)")

    # --- miss rate conditioned on a property -----------------------------
    def cond(name, fn):
        sub = [r for r in recs if fn(r)]
        if not sub:
            print(f"  {name:22} n=0")
            return
        m = sum(1 for r in sub if not r["model_hit1"])
        print(f"  {name:22} n={len(sub):3d}  miss {m:3d}  ({100*m/len(sub):.0f}%)"
              f"   [{100*len(sub)/n:.0f}% of words]")
    print("\nmiss rate conditioned on the CORRECT parse's properties:")
    cond("freq_root == 0", lambda r: r["correct"] and r["correct"].get("freq_root", 0) == 0)
    cond("freq_root > 0", lambda r: r["correct"] and r["correct"].get("freq_root", 0) > 0)
    cond("edit_dist >= 3", lambda r: r["correct"] and r["correct"].get("edit_dist", 0) >= 3)
    cond("edit_dist <= 1", lambda r: r["correct"] and r["correct"].get("edit_dist", 0) <= 1)
    cond(">= 3 morphemes", lambda r: r["correct"] and r["correct"].get("n_morphemes", 0) >= 3)
    cond(">= 20 candidates", lambda r: r["n_cand"] >= 20)
    cond("hand sort also wrong", lambda r: not r["cur_hit1"])
    cond("hand sort correct", lambda r: r["cur_hit1"])

    # --- what the wrong pick has that the correct parse lacks ------------
    keys = ["freq_root", "freq_sum", "freq_mean", "n_zero_freq", "edit_dist",
            "edit_dist_norm", "n_morphemes", "weight", "rank_current_sort",
            "bigram_mean", "bigram_nzero", "type_violations", "cat_switches",
            "concat_len_minus_word"]
    print("\nmean(pick - correct) over the misses where pick != correct:")
    pairs = [r for r in miss if not r["pick_correct"] and r["correct"]]
    for k in keys:
        d = [r["pick"].get(k, 0) - r["correct"].get(k, 0) for r in pairs]
        if d:
            print(f"  {k:22} {statistics.mean(d):+8.3f}   "
                  f"(pick>corr in {100*sum(1 for x in d if x>0)/len(d):3.0f}% of misses)")

    # --- regressions vs the hand sort ----------------------------------
    gbdt_only = [r for r in miss if r["cur_hit1"]]
    shared = [r for r in miss if not r["cur_hit1"]]
    recovered = [r for r in recs if r["model_hit1"] and not r["cur_hit1"]]
    print(f"\nvs hand sort:  GBDT-only regressions {len(gbdt_only)}  |  "
          f"shared misses {len(shared)}  |  GBDT recovers {len(recovered)}")
    print("GBDT-only regressions (correct parse rank under GBDT):")
    for r in sorted(gbdt_only, key=lambda r: r["model_rank"]):
        print(f"  {r['word']:20} rank {r['model_rank']:2d} / {r['n_cand']:2d} cand"
              f"  edit={r['correct'].get('edit_dist','?')}"
              f"  freq_root={r['correct'].get('freq_root','?')}")


if __name__ == "__main__":
    main()
