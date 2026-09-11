"""
R2L analogue of build_reranker_table.py -- builds the offline training table
for a re-ranker over *R2L*'s (Benoit Farley's Uqailaut,
MorphologicalAnalyzer_R2L) own decomposition candidates.

Objective (doc/dev/plans/offline-dictionary-generation.md, Part 3 "Cheap
layer"): raise each word's single Hansard-attested REFERENCE decomposition
to rank 1 (`reference@1`). R2L's native rank-1 already IS the reference for
~73% of --fair gold words; a re-ranker over R2L's own candidate lists does
better, deterministically, at no API cost.

Candidates come from hansard-cache/top10k_words_benoit_decomps.jsonl -- R2L
(lenient) over the 10k most frequent Hansard word types, which already
covers every --fair gold word, so no live :cli run is needed. Features are
the SAME identity-free features()/add_relative_features() as the FST
builder, imported unchanged.

Output: scratchpad/reranker_table_r2l.jsonl -- identical schema to
scratchpad/reranker_table.jsonl, so the CV / GBDT scripts run on it
unchanged:
    RERANKER_TABLE=scratchpad/reranker_table_r2l.jsonl python3 reranker_cv.py

Per row: {word, fair, decomp, parts, label, is_correct, feat}.
  label      : 1 iff the candidate == the word's single reference decomp
               (gold `decomp_as_found_in_source`) -- the training target.
  is_correct : label OR candidate in the gold `all_correct_decomps` set.
               NB for R2L that column is by construction R2L's own output,
               so is_correct is near-vacuous here -- kept only for schema
               parity and the graded read-outs in the CV scripts.

Candidates are compared / deduped as (canonical, id) sequences (histogram.
parse_gold), NOT as {surface:canonical/id} strings: R2L emits several
surface segmentations that collapse to one morpheme sequence (e.g.
{mi:miik/1vn}{ik:k/tn-nom-d} == {mii:miik/1vn}{k:k/tn-nom-d}); a re-ranker
that ranks *decompositions* sees one candidate there, at its earliest
(best) native rank. `feat["weight"]` is 0.0 for every candidate -- the R2L
cache records lenient-ness per request, not per decomposition (a later tier
can pull `decomps_lenient` from the S1 mined lexicon).

Words with no R2L candidate, or where R2L never produced the reference
decomp, are dropped (no positive to rank toward). Pure standard library.
Run from data/grammar/fst/.
"""
import json
from collections import defaultdict

from build_reranker_table import add_relative_features, features
from gold_standard_csv_reader import is_flagged, load_gold_standard
from histogram import parse_gold

OUT = "scratchpad/reranker_table_r2l.jsonl"
R2L_CACHE = "hansard-cache/top10k_words_benoit_decomps.jsonl"
SYL_WORDS = "hansard-cache/top10k_words.txt"
ROMAN_WORDS = "hansard-cache/top10k_words_roman.txt"


def syl_to_roman():
    syl = [l.rstrip("\n") for l in open(SYL_WORDS, encoding="utf-8")]
    rom = [l.rstrip("\n") for l in open(ROMAN_WORDS, encoding="utf-8")]
    return dict(zip(syl, rom))


def load_r2l_candidates():
    """roman surface form -> [(decomp string, (canonical,id) tuple), ...] in
    R2L's native rank order, deduped by morpheme sequence (earliest rank
    kept). Empty parses (regex miss) are dropped."""
    s2r = syl_to_roman()
    out = {}
    with open(R2L_CACHE, encoding="utf-8") as fh:
        for line in fh:
            rec = json.loads(line)
            roman = s2r.get(rec["word"], rec["word"])
            seen = set()
            cands = []
            for d in rec.get("decompositions") or []:
                parts = tuple(parse_gold(d))
                if not parts or parts in seen:
                    continue
                seen.add(parts)
                cands.append((d, parts))
            out[roman] = cands
    return out


def load_gold_cases():
    """roman word -> (reference parse set, all-correct parse set, fair?).
    Same permissive population as build_reranker_table.py: both gold
    sources, `fair` = not is_flagged (misspelled / possibly-misspelled /
    borrowed / proper-name / decomp-unknown)."""
    cases = {}
    for source in ("hansard", "words_that_failed_before"):
        for word, case in load_gold_standard(source).items():
            if not case.correct_decomps:
                continue
            cases[word] = (
                {tuple(parse_gold(d)) for d in case.correct_decomps},
                {tuple(parse_gold(d)) for d in case.all_correct_decomps},
                not is_flagged(case),
            )
    return cases


def main():
    r2l = load_r2l_candidates()
    gold = load_gold_cases()

    rows = []
    kept = no_candidate = reference_absent = 0
    for word, (ref_parses, all_parses, fair) in gold.items():
        cands = r2l.get(word)
        if not cands:
            no_candidate += 1
            continue
        if not any(parts in ref_parses for _, parts in cands):
            reference_absent += 1
            continue
        kept += 1
        for rank, (decomp, parts) in enumerate(cands):
            feat = features(word, list(parts))
            feat["weight"] = 0.0
            label = int(parts in ref_parses)
            rows.append({
                "word": word,
                "fair": fair,
                "decomp": decomp,
                "parts": [list(p) for p in parts],
                "label": label,
                "is_correct": int(label or parts in all_parses),
                "_sort_key": rank,
                "feat": feat,
            })

    add_relative_features(rows)
    with open(OUT, "w", encoding="utf-8") as fh:
        for r in rows:
            r.pop("_sort_key", None)
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")

    n_words = len({r["word"] for r in rows})
    n_fair = len({r["word"] for r in rows if r["fair"]})
    pos = sum(r["label"] for r in rows)
    corr = sum(r["is_correct"] for r in rows)
    # native-rank-1 baseline (the number to beat): reference decomp is R2L's
    # own first distinct candidate.
    by_word = defaultdict(list)
    for r in rows:
        by_word[r["word"]].append(r)
    native_ref1 = sum(
        1 for g in by_word.values()
        if next((x for x in sorted(g, key=lambda r: r["feat"]["rank_current_sort"])), {}).get("label")
    )
    native_ref1_fair = sum(
        1 for g in by_word.values() if g[0]["fair"]
        and next((x for x in sorted(g, key=lambda r: r["feat"]["rank_current_sort"])), {}).get("label")
    )
    print(f"{OUT}: {len(rows)} rows, {n_words} words ({n_fair} fair), "
          f"{pos} reference (label=1) / {corr} correct-set candidates")
    print(f"  dropped: {no_candidate} words no R2L candidate, "
          f"{reference_absent} words R2L never produced the reference decomp")
    print(f"  R2L native reference@1: {native_ref1}/{n_words} all, "
          f"{native_ref1_fair}/{n_fair} fair "
          f"({100*native_ref1_fair/max(n_fair,1):.1f}%)  <- baseline to beat")


if __name__ == "__main__":
    main()
