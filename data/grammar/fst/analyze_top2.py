"""
Characterise how a word's re-ranked TOP-1 and TOP-2 decompositions differ,
across the 100k analyzed lexicon -- input scratchpad/rerank_100k_top2.jsonl
(from score_100k.py).

Purpose (doc/dev/plans/offline-dictionary-generation.md): pick a subset of
the 100k words whose dictionary entries would *semantically* cover the whole
100k. The top-1/top-2 relation says how load-bearing the re-ranker's rank-1
pick is for headword identity.

Two dimensions:

A. SEGMENTATION -- compare the two decomps as tuples of canonical morpheme
   forms (ignoring the grammatical id tags):
     same_canon        identical canonical tuple -> same morphemes, R2L only
                       labels them differently (deriv-vs-infl, case, dialect
                       root tag). Headword NOT at stake.
     root_prefix       one root canonical is a proper prefix of the other
                       (lexicalised root vs its compositional etymology,
                       e.g. nunavut vs nuna+vut, kiinaujaq vs kiinaq+u+jaq).
     same_root_canon   same first canonical, the rest of the split differs.
     diff_root_canon   different first canonical, neither prefixes the other
                       -> genuinely two candidate lexemes.

B. GRAMMATICAL delta (independent of A): inflection-class change
   (none/nominal/verbal), case/mode change within a class, derivation<->
   inflection reanalysis of the final piece, enclitic-set change, and
   "one decomp = the other + k morphemes".

For the covering-subset question the headline is: for how many words is the
STEM (headword) the same across top-1/top-2 (choice is grammatical noise)
vs genuinely different (choice needs corpus/LLM disambiguation), and how
confident (score margin) the re-ranker is in the latter case.

Run from data/grammar/fst/.
"""
import json
import re
from collections import Counter, defaultdict

IN = "scratchpad/rerank_100k_top2.jsonl"
LDATA = "../linguistic-data"

ENDING_RE = re.compile(r"^t[nvd]-|^t[ap]d-")
ENCLITIC_RE = re.compile(r"^\d+q$")
DERIV_RE = re.compile(r"^\d+[a-z]{2,}$")
SEG_RE = re.compile(r"\{([^:}]+):([^/}]+)/([^}]+)\}")


# --- glosses (best-effort, printed examples only) ------------------------
def _csv_rows(path):
    import csv
    with open(path, encoding="utf-8", newline="") as fh:
        yield from csv.DictReader(fh)


def load_glosses():
    g = {}
    for fn in ("RootsSpalding.csv", "RootsSchneider.csv", "WordsRelatedToRoots.csv"):
        try:
            for r in _csv_rows(f"{LDATA}/{fn}"):
                g.setdefault(f"{r['morpheme']}/{r.get('nb','1')}{r.get('type','')}",
                             (r.get('engMean') or '').strip().replace("\n", " ")[:44])
        except FileNotFoundError:
            pass
    try:
        for r in _csv_rows(f"{LDATA}/Suffixes.csv"):
            g.setdefault(f"{r['morpheme']}/{r['nb']}{r['type']}",
                         (r.get('engMean') or '').strip().replace("\n", " ")[:44])
    except FileNotFoundError:
        pass
    return g


def render(decomp, G):
    out = []
    for _surface, canon, mid in SEG_RE.findall(decomp):
        if ENDING_RE.match(mid):
            out.append(f"{canon}·{mid}")
        elif ENCLITIC_RE.match(mid):
            out.append(f"={canon}")
        else:
            m = G.get(f"{canon}/{mid}")
            out.append(f"{canon}“{m}”" if m else canon)
    return " + ".join(out)


# --- structure ---------------------------------------------------------------
def segs(decomp):
    """[(canonical, id), ...]"""
    return [(c, i) for _s, c, i in SEG_RE.findall(decomp)]


def canon_tuple(decomp):
    return tuple(c for c, _i in segs(decomp))


def stem_sig(pairs):
    """canonical tuple from root through last derivational suffix: drop
    trailing enclitics, cut before the first inflectional ending."""
    ids = [i for _c, i in pairs]
    end = len(ids)
    while end and ENCLITIC_RE.match(ids[end - 1]):
        end -= 1
    cut = end
    for k in range(end):
        if ENDING_RE.match(ids[k]):
            cut = k
            break
    return tuple(c for c, _i in pairs[:cut])


def ending_class(pairs):
    for _c, i in pairs:
        if ENDING_RE.match(i):
            if i.startswith("tv-"):
                return "verbal"
            return "nominal"
    return "none"


def ending_id(pairs):
    for _c, i in pairs:
        if ENDING_RE.match(i):
            return i
    return None


def enclitics(pairs):
    return tuple(c for c, i in pairs if ENCLITIC_RE.match(i))


def seg_relation(d1, d2):
    c1, c2 = canon_tuple(d1), canon_tuple(d2)
    if c1 == c2:
        return "same_canon"
    r1, r2 = c1[0], c2[0]
    if r1 != r2 and (r1.startswith(r2) or r2.startswith(r1)):
        return "root_prefix"
    if r1 == r2:
        return "same_root_canon"
    return "diff_root_canon"


def gram_delta(d1, d2):
    p1, p2 = segs(d1), segs(d2)
    tags = []
    e1, e2 = ending_class(p1), ending_class(p2)
    if e1 != e2:
        tags.append(f"infl-class {e1}->{e2}")
    elif ending_id(p1) != ending_id(p2) and e1 != "none":
        tags.append(f"{e1}-ending case/mode change")
    i1 = [i for _c, i in p1]
    i2 = [i for _c, i in p2]
    # derivation<->inflection reanalysis of the LAST non-enclitic piece
    def last_content(ids):
        ids = [i for i in ids if not ENCLITIC_RE.match(i)]
        return ids[-1] if ids else None
    l1, l2 = last_content(i1), last_content(i2)
    if l1 and l2 and canon_tuple(d1)[:0] == () and \
       ((DERIV_RE.match(l1) and ENDING_RE.match(l2)) or
            (ENDING_RE.match(l1) and DERIV_RE.match(l2))):
        tags.append("deriv<->infl reanalysis of final piece")
    if enclitics(p1) != enclitics(p2):
        tags.append("enclitic set differs")
    if tuple(i1[:len(i2)]) == tuple(i2) or tuple(i2[:len(i1)]) == tuple(i1):
        k = abs(len(i1) - len(i2))
        if k:
            tags.append(f"+{k} morpheme(s)")
    return tags or ["other / segmentation only"]


def band(rank):
    return ("top1k" if rank <= 1000 else "top10k" if rank <= 10000 else
            "top50k" if rank <= 50000 else "tail")


def pct(x, n):
    return f"{100*x/n:.1f}%"


def main():
    recs = [json.loads(l) for l in open(IN, encoding="utf-8")]
    multi = [r for r in recs if r["top2"]]
    G = load_glosses()
    n = len(multi)

    print(f"{len(recs)} analyzable words; {n} have >=2 distinct decomps "
          f"({pct(n, len(recs))}), {len(recs)-n} have exactly 1.\n")

    seg = Counter()
    seg_by_band = defaultdict(Counter)
    stem_same = 0
    gram = Counter()
    margins = defaultdict(list)
    len1 = len2 = 0
    ex = defaultdict(list)

    for r in multi:
        d1, d2 = r["top1"]["decomp"], r["top2"]["decomp"]
        sr = seg_relation(d1, d2)
        seg[sr] += 1
        seg_by_band[band(r["rank"])][sr] += 1
        margins[sr].append(r["margin"])
        same_stem = stem_sig(segs(d1)) == stem_sig(segs(d2))
        stem_same += same_stem
        for t in gram_delta(d1, d2):
            gram[t] += 1
        len1 += r["top1"]["lenient"]
        len2 += r["top2"]["lenient"]
        key = (sr, "stem=" if same_stem else "stem!=")
        if len(ex[key]) < 10 and r["rank"] <= 30000 and not r["top1"]["lenient"]:
            ex[key].append(r)

    ORDER = ["same_canon", "root_prefix", "same_root_canon", "diff_root_canon"]
    LABEL = {
        "same_canon":      "same morphemes, only the id labels differ",
        "root_prefix":     "lexicalised root vs its compositional etymology",
        "same_root_canon": "same root canonical, rest of the split differs",
        "diff_root_canon": "different root -> genuinely two lexemes",
    }
    print("=== A. segmentation relation of top-1 vs top-2 ===")
    for k in ORDER:
        mm = sorted(margins[k])
        med = mm[len(mm) // 2]
        p10 = mm[len(mm) // 10]
        print(f"  {LABEL[k]:50} {seg[k]:6d} ({pct(seg[k], n)})   "
              f"margin med {med:+.2f}  p10 {p10:+.2f}")

    # headword contested = top-1/top-2 disagree on the ROOT canonical
    # (first morpheme). Same-root disagreements are grammatical labelling or a
    # moved derivation/inflection boundary -> same headword family.
    contested_margins = [r["margin"] for r in multi
                         if seg_relation(r["top1"]["decomp"], r["top2"]["decomp"])
                         == "diff_root_canon"]
    safe = n - len(contested_margins)
    print(f"\n  HEADWORD SAFE  (top-1/top-2 share the root canonical)            : "
          f"{safe:6d}  ({pct(safe, n)})")
    print(f"  headword CONTESTED (different root canonical)                     : "
          f"{len(contested_margins):6d}  ({pct(len(contested_margins), n)})   "
          f"= {pct(len(contested_margins), len(recs))} of all analyzable")
    cm = sorted(contested_margins)
    for lo, hi in [(0.0, 0.5), (0.5, 1.0), (1.0, 2.0), (2.0, 4.0), (4.0, 99)]:
        c = sum(1 for m in cm if lo <= m < hi)
        print(f"      margin [{lo:>4.1f},{hi:>4.1f})  {c:6d}  ({pct(c, len(cm))} of contested)")
    nt = sum(1 for m in cm if m < 0.5)
    print(f"    near-tie contested (margin < 0.5) = {nt}  "
          f"({pct(nt, len(recs))} of all analyzable) -- the real disambiguation residual;"
          f" inspection shows most are lexicalised-vs-compositional, not two senses.")

    print("\n=== segmentation relation by frequency band (row %) ===")
    print(f"  {'band':7}" + "".join(f"{k:>18}" for k in ORDER))
    for b in ("top1k", "top10k", "top50k", "tail"):
        cc = seg_by_band[b]
        t = sum(cc.values()) or 1
        print(f"  {b:7}" + "".join(f"{pct(cc[k], t):>18}" for k in ORDER))

    print("\n=== B. grammatical delta (top-1 -> top-2), multi-label over "
          f"{n} words ===")
    for k, v in gram.most_common():
        print(f"  {k:42} {v:6d}  ({pct(v, n)})")

    print(f"\n=== lenient ('guessed dropped final consonant') ===")
    print(f"  top-1 lenient : {len1} ({pct(len1, n)})    top-2 lenient : {len2} ({pct(len2, n)})")

    print("\n=== examples (rank<=30000, non-lenient top-1;  “gloss” from linguistic-data) ===")
    for k in ORDER:
        for tag in ("stem=", "stem!="):
            rows = ex[(k, tag)]
            if not rows:
                continue
            print(f"\n-- {LABEL[k]}  [{tag}] --")
            for r in rows:
                print(f"  {r['word_rom']:20} #{r['rank']:<6} margin {r['margin']:+.2f}")
                print(f"      1: {render(r['top1']['decomp'], G)}")
                print(f"      2: {render(r['top2']['decomp'], G)}")


if __name__ == "__main__":
    main()
