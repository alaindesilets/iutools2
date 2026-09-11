"""
Re-sort every word's decomps list in the published 100k analyzed lexicon
(data/lexicon/decompositions/hansard-top100k-decomps.jsonl) so it reads
best-first per the R2L `reference` re-ranker (the GBDT from
reranker_gbdt.py), instead of R2L's native emission order.

This is a superset of what score_100k.py already does: same deployment
fit (trained on the full fair-gold R2L table, frequency/bigram priors
rebuilt from the 100k file's own rank-1 decomps), but instead of writing
a slim top-2 summary to scratchpad/, it re-orders and REWRITES the
"decomps" / "decomps_lenient" fields of the lexicon file itself, in
place -- every decomp is kept (nothing dropped, ties broken by native
rank), just reordered. All other fields (rank, count, word_rom, word_syl,
analyzer_outcome, n_decomps, ...) pass through unchanged.

Written for the iutools2-dictionary spike (see
data/lexicon/iutools2-dictionary/README.md): Phase 1 needs each word's
FULL candidate list in re-ranked order, not just the top-2 score_100k.py
already exports. A fresh GitHub release of the dataset is expected once
this has run (see AGENTS.md on why the published asset is normally
treated as immutable).

Run from data/grammar/fst/. Writes to a temp file, then atomically
replaces the lexicon file so a crash mid-run can't leave a truncated
dataset.
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
CFG = dict(trees=200, depth=4, lr=0.1, leaf=20, lam=1.0,
           neg=8, rowsub=0.4, featsub=0.6)
NBINS = 32
SEED = 20260908


def iter_lexicon():
    with open(LEXICON, encoding="utf-8") as fh:
        for line in fh:
            yield json.loads(line)


def stats_from_lexicon():
    """build_stats() input rows: (word_rom, rank-1 decomp string)."""
    rows = []
    for rec in iter_lexicon():
        if rec["analyzer_outcome"] != "completed" or not rec["decomps"]:
            continue
        rows.append((rec["word_rom"], rec["decomps"][0]))
    return rows


def featurize(word, decomps, lenient_flags):
    """decomps: every decomp string, native order, NOTHING dropped."""
    rows = []
    for rank, (dstr, lenient) in enumerate(zip(decomps, lenient_flags)):
        parts = parse_gold(dstr)
        feat = features(word, parts)
        feat["weight"] = float(lenient)
        rows.append({
            "word": word, "decomp": dstr, "lenient": lenient,
            "parts": [list(p) for p in parts],
            "_sort_key": rank, "feat": feat,
        })
    add_relative_features(rows)
    return rows


def train_deployment_model():
    S = R.build_stats(stats_from_lexicon())
    rows, words, keep = R.load({"r2l"})
    R.recompute_stats(rows, S)
    R.set_x(rows, keep)
    edges = G.prebin(rows, keep, NBINS)
    nf = len(keep)
    fair_words = sorted(w for w in words if words[w][0]["fair"])
    rng = random.Random(SEED)
    print(f"training GBDT on {len(fair_words)} fair gold words, "
          f"{len(rows)} rows, {nf} features ...", flush=True)
    trees = G.train_gbdt(rows, words, fair_words, nf, NBINS, CFG, rng, "label")
    print("trained.", flush=True)
    return S, keep, edges, nf, trees


def main():
    S, keep, edges, nf, trees = train_deployment_model()

    tmp = LEXICON + ".resorting.tmp"
    n_total = n_resorted = n_passthrough = 0
    with open(LEXICON, encoding="utf-8") as fin, \
         open(tmp, "w", encoding="utf-8") as fout:
        for line in fin:
            rec = json.loads(line)
            n_total += 1
            if rec["analyzer_outcome"] != "completed" or not rec.get("decomps"):
                fout.write(line if line.endswith("\n") else line + "\n")
                n_passthrough += 1
                continue

            decomps = rec["decomps"]
            lenient = rec.get("decomps_lenient") or [False] * len(decomps)
            frows = featurize(rec["word_rom"], decomps, lenient)
            R.recompute_stats(frows, S)
            R.set_x(frows, keep)
            for r in frows:
                r["b"] = tuple(G._bin(r["x"][j], edges[j]) for j in range(nf))
            G.score_all(frows, trees, CFG["lr"])
            # stable sort: ties keep native order (native rank is _sort_key)
            frows.sort(key=lambda r: (-r["S"], r["_sort_key"]))

            rec["decomps"] = [r["decomp"] for r in frows]
            rec["decomps_lenient"] = [r["lenient"] for r in frows]
            fout.write(json.dumps(rec, ensure_ascii=False) + "\n")
            n_resorted += 1
            if n_total % 10000 == 0:
                print(f"  {n_total} processed ({n_resorted} resorted)", flush=True)

    os.replace(tmp, LEXICON)
    print(f"\n{LEXICON}: {n_total} records "
          f"({n_resorted} resorted, {n_passthrough} passed through unchanged)")


if __name__ == "__main__":
    main()
