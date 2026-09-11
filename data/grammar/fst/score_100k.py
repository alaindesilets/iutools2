"""
Run the R2L `reference` re-ranker (the GBDT from reranker_gbdt.py, trained
here on the FULL fair-gold R2L table -- no CV holdout, this is the
deployment fit) over every word in the published 100k analyzed lexicon
(data/lexicon/decompositions/hansard-top100k-decomps.jsonl, fetched via
that dir's fetch.sh), and record each word's re-ranked TOP 2 decompositions.

Morpheme-frequency / bigram / back-off features are rebuilt from the 100k
file's own rank-1 decomps (a ~10x richer prior than the 10k Hansard cache
the CV used) and applied to BOTH the training rows and the 100k scoring
rows, so train/inference features are on the same footing. The `r2l`
feature group is dropped (it is redundant with `rank_current_sort` on an
R2L table).

Output: scratchpad/rerank_100k_top2.jsonl, one object per analyzable word:
  {rank, count, word_rom, word_syl, outcome, n_decomps,
   top1: {decomp, ids, lenient, score}, top2: {...} | null,
   margin}   (margin = score[top1] - score[top2])

Candidates are deduped to (canonical, id) sequences at their earliest
native rank and capped at the 40 best native ranks before scoring (the
reference decomp is in R2L's native top-3 ~99% of the time, so this cannot
move a real top-2). Pure standard library. Run from data/grammar/fst/.
"""
import json
import os
import random

os.environ.setdefault("RERANKER_TABLE", "scratchpad/reranker_table_r2l.jsonl")

import reranker_cv as R
import reranker_gbdt as G
from build_reranker_table import add_relative_features, features
from histogram import parse_gold

LEXICON = "../../../data/lexicon/decompositions/hansard-top100k-decomps.jsonl"
OUT = "scratchpad/rerank_100k_top2.jsonl"
CAND_CAP = 40
CFG = dict(trees=200, depth=4, lr=0.1, leaf=20, lam=1.0,
           neg=8, rowsub=0.4, featsub=0.6)
NBINS = 32
SEED = 20260908


def iter_lexicon():
    with open(LEXICON, encoding="utf-8") as fh:
        for line in fh:
            yield json.loads(line)


def dedup(decomps, lenient_flags):
    """[(ids_tuple, decomp_str, lenient_bool)] deduped by ids, earliest kept."""
    seen = set()
    out = []
    for d, len_flag in zip(decomps, lenient_flags or [False] * len(decomps)):
        ids = tuple(i for _c, i in parse_gold(d))
        if not ids or ids in seen:
            continue
        seen.add(ids)
        out.append((ids, d, bool(len_flag)))
    return out


def stats_from_lexicon():
    """build_stats() input rows: (word_rom, rank-1 decomp string)."""
    rows = []
    for rec in iter_lexicon():
        if rec["analyzer_outcome"] != "completed" or not rec["decomps"]:
            continue
        rows.append((rec["word_rom"], rec["decomps"][0]))
    return rows


def featurize(word, cand_list):
    """cand_list: [(ids, decomp_str, lenient)]  ->  list of feature rows."""
    rows = []
    for rank, (ids, dstr, lenient) in enumerate(cand_list):
        parts = parse_gold(dstr)                 # [(canonical, id), ...]
        feat = features(word, parts)
        feat["weight"] = float(lenient)
        rows.append({
            "word": word,
            "parts": [list(p) for p in parts],
            "ids": list(ids),
            "decomp": dstr,
            "lenient": lenient,
            "_sort_key": rank,
            "feat": feat,
        })
    add_relative_features(rows)
    return rows


def main():
    # --- richer freq prior, shared by train + score -----------------------
    S = R.build_stats(stats_from_lexicon())

    # --- train the deployment GBDT on the full fair gold R2L table --------
    rows, words, keep = R.load({"r2l"})
    R.recompute_stats(rows, S)
    R.set_x(rows, keep)
    edges = G.prebin(rows, keep, NBINS)          # bin edges from gold rows
    nf = len(keep)
    fair_words = sorted(w for w in words if words[w][0]["fair"])
    rng = random.Random(SEED)
    print(f"training GBDT on {len(fair_words)} fair gold words, "
          f"{len(rows)} rows, {nf} features ...", flush=True)
    trees = G.train_gbdt(rows, words, fair_words, nf, NBINS, CFG, rng, "label")
    print("trained.", flush=True)

    # --- score every analyzable 100k word --------------------------------
    n_out = n_one = n_multi = 0
    with open(OUT, "w", encoding="utf-8") as out:
        for rec in iter_lexicon():
            if rec["analyzer_outcome"] != "completed" or not rec["decomps"]:
                continue
            cands = dedup(rec["decomps"], rec.get("decomps_lenient"))[:CAND_CAP]
            frows = featurize(rec["word_rom"], cands)
            R.recompute_stats(frows, S)
            R.set_x(frows, keep)
            for r in frows:
                r["b"] = tuple(G._bin(r["x"][j], edges[j]) for j in range(nf))
            G.score_all(frows, trees, CFG["lr"])
            frows.sort(key=lambda r: -r["S"])

            def slim(r):
                return {"decomp": r["decomp"], "ids": r["ids"],
                        "lenient": r["lenient"], "score": round(r["S"], 4)}

            top1 = frows[0]
            top2 = frows[1] if len(frows) > 1 else None
            out.write(json.dumps({
                "rank": rec["rank"], "count": rec["count"],
                "word_rom": rec["word_rom"], "word_syl": rec["word_syl"],
                "outcome": rec["analyzer_outcome"], "n_decomps": len(cands),
                "top1": slim(top1),
                "top2": slim(top2) if top2 else None,
                "margin": round(top1["S"] - top2["S"], 4) if top2 else None,
            }, ensure_ascii=False) + "\n")
            n_out += 1
            if top2:
                n_multi += 1
            else:
                n_one += 1
            if n_out % 10000 == 0:
                print(f"  {n_out} scored", flush=True)

    print(f"\n{OUT}: {n_out} words  ({n_multi} with >=2 distinct decomps, "
          f"{n_one} with exactly 1)")


if __name__ == "__main__":
    main()
