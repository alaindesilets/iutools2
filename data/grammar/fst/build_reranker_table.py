"""
Builds the offline training table for the FST decomposition re-ranker
experiment (see the discussion notes / project memory).

For every gold-standard word: enumerate the FST's candidate decompositions
(lenient, min-weight dedup -- the same set MorphologicalAnalyzer_FST sees),
label each candidate 1 if it matches a gold parse else 0, and extract a set
of IDENTITY-FREE features -- nothing that references *which* morpheme, only
decomposition shape, grammatical-category codes, and faithfulness to the
surface string. (Morpheme-frequency features are a deliberate later escalation
tier, kept out of this baseline.)

Output: scratchpad/reranker_table.jsonl, one JSON object per (word, candidate):
  {"word", "fair" (bool), "decomp", "label" (0/1), "feat": {...}}

Words whose correct parse the FST never produces are dropped (no positive to
rank toward). Pure standard library.

Run from data/grammar/fst/.
"""
import json
import re
from collections import defaultdict

from affix_frequency import load_words
from benoit_sort import sort_with_frequency_tiebreak
from histogram import hfst_analyses_weighted, parse_hfst_analysis

OUT = "scratchpad/reranker_table.jsonl"

# --- morpheme-id category classification (codes only, no identities) --------
ENDING_RE = re.compile(r"^(tn|tv|tad|tpd)-")
DERIV_RE = re.compile(r"^\d+(vn|nv|vv|nn)$")
ROOT_RE = re.compile(r"^(\d+[a-z]+|rad-|pd-|ad-|q)")


def morph_category(mid):
    """'root' | 'deriv' | 'ending' | 'other', plus for deriv the in/out
    category letters (e.g. '1vn' -> ('deriv', 'v', 'n'))."""
    if ENDING_RE.match(mid):
        return ("ending", None, None)
    m = DERIV_RE.match(mid)
    if m:
        return ("deriv", m.group(1)[0], m.group(1)[1])
    if re.match(r"^\d+[vn]$", mid):
        return ("root", None, mid[-1])
    if re.match(r"^\d+[a-z]+$", mid) or mid.startswith(("rad-", "pd-", "ad-")):
        return ("root", None, "n")  # adverb/conj/pronoun/demonstrative roots
    return ("other", None, None)


def levenshtein(a, b):
    if a == b:
        return 0
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def features(word, parts):
    """parts: [(canonical, id), ...]  ->  dict of identity-free features."""
    canons = [c for c, _ in parts]
    ids = [i for _, i in parts]
    cats = [morph_category(i) for i in ids]

    n = len(parts)
    root_canon_len = len(canons[0]) if canons else 0
    concat = "".join(canons)

    # running grammatical category + violations across derivational chain
    cur_cat = cats[0][2] if cats else "n"
    switches = viol = n_deriv = 0
    for kind, cin, cout in cats[1:]:
        if kind == "deriv":
            n_deriv += 1
            if cin != cur_cat:
                viol += 1
            if cout != cur_cat:
                switches += 1
            cur_cat = cout

    ed = levenshtein(concat, word)
    # Identity-derived features (frequency / bigram / OOV back-off) are NOT
    # computed here -- reranker_cv.py fills them from a count table built per
    # CV fold (see its build_stats/recompute_stats), so --loo can exclude the
    # eval fold's words. This file stores only identity-free features + parts.
    base = {
        "n_morphemes": n,
        "n_nonroot": n - 1,
        "n_deriv": n_deriv,
        "n_ending": sum(1 for k, _, _ in cats if k == "ending"),
        "n_vv": sum(1 for i in ids if re.match(r"^\d+vv$", i)),
        "n_nn": sum(1 for i in ids if re.match(r"^\d+nn$", i)),
        "n_vn": sum(1 for i in ids if re.match(r"^\d+vn$", i)),
        "n_nv": sum(1 for i in ids if re.match(r"^\d+nv$", i)),
        "cat_switches": switches,
        "type_violations": viol,
        "ends_with_ending": int(bool(cats) and cats[-1][0] == "ending"),
        "ends_with_bare_deriv": int(bool(cats) and cats[-1][0] == "deriv"),
        "root_canon_len": root_canon_len,
        "mean_canon_len": sum(len(c) for c in canons) / n,
        "min_canon_len": min(len(c) for c in canons),
        "n_thin_canon": sum(1 for c in canons if len(c) <= 1),
        "wordlen_per_morph": len(word) / n,
        "n_consec_dup_id": sum(1 for a, b in zip(ids, ids[1:]) if a == b),
        "edit_dist": ed,
        "edit_dist_norm": ed / max(len(word), 1),
        "concat_len_minus_word": len(concat) - len(word),
        "first_canon_is_prefix": int(word.startswith(canons[0]) if canons else 0),
    }
    return base


def add_relative_features(rows):
    """Per-word rank (ascending, 0 = best) of a few key features + the
    candidate's position under the current 5-key hand sort."""
    by_word = defaultdict(list)
    for r in rows:
        by_word[r["word"]].append(r)
    for word, group in by_word.items():
        group.sort(key=lambda r: r["_sort_key"])
        for pos, r in enumerate(group):
            r["feat"]["rank_current_sort"] = pos
        for key in ("edit_dist", "n_morphemes", "root_canon_len", "type_violations"):
            order = sorted(range(len(group)),
                           key=lambda k: (group[k]["feat"][key] if key != "root_canon_len"
                                          else -group[k]["feat"][key]))
            for rank, k in enumerate(order):
                group[k]["feat"][f"{key}_rank_in_word"] = rank
        for r in group:
            r["feat"]["n_candidates"] = len(group)


def main():
    fair_words = {w for w, _ in load_words(exclude_flagged=True)}
    grouped = defaultdict(list)
    for w, m in load_words():           # permissive superset
        grouped[w].append(list(m))

    rows = []
    kept = dropped = 0
    for word, gold_parses in grouped.items():
        pairs = dedup_min_weight(hfst_analyses_weighted(word, lenient=True))
        if not pairs:
            dropped += 1
            continue
        ranked = sort_with_frequency_tiebreak(pairs)   # (analysis, weight)
        sort_pos = {a: i for i, (a, _) in enumerate(ranked)}
        parsed = [(a, w_, parse_hfst_analysis(a)) for a, w_ in pairs]
        if not any(p in gold_parses for _, _, p in parsed):
            dropped += 1
            continue
        kept += 1
        for analysis, weight, parts in parsed:
            feat = features(word, parts)
            feat["weight"] = float(weight)          # 0 strict / 1 lenient (sort key 1)
            rows.append({
                "word": word,
                "fair": word in fair_words,
                "decomp": analysis,
                "parts": parts,                     # [[canonical, id], ...] for per-fold freq recompute
                "label": int(parts in gold_parses),
                "_sort_key": sort_pos[analysis],
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
    print(f"{OUT}: {len(rows)} rows, {n_words} words ({n_fair} fair), "
          f"{pos} positive candidates; dropped {dropped} words (no correct candidate)")


def dedup_min_weight(pairs):
    mw = {}
    for a, w in pairs:
        if a not in mw or w < mw[a]:
            mw[a] = w
    return list(mw.items())


if __name__ == "__main__":
    main()
