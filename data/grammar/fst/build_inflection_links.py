"""
Walk the 100k analyzed lexicon and link every word to the BASE form of its
paradigm, so a later step can run Guess Meaning once per paradigm and
rule-propagate the meaning to the inflected forms (see the spike
scratchpad/spike_inflect_meaning.py and doc/dev/plans/offline-dictionary-
generation.md Part 2/3).

Grouping key = the re-ranked top-1 decomposition's STEM SIGNATURE: the
(canonical, id) tuple from the root through the last derivational suffix
(drop trailing enclitics, then cut before the first inflectional ending).
By (canonical, id): homograph-safe (uqaq/1v "speak" != uqaq/1n "tongue")
and surface-allomorph-merging ({ministu:minista/1n} == {minista:minista/1n}).

Base form of a cluster, in priority order:
  tier 0  no inflectional ending, no enclitic   (the citation form)
  tier 1  no ending, enclitic(s) only
  tier 2  inflected
pick the most frequent (lowest `rank`) member of the first non-empty tier.

NO LOOPS by construction: every cluster is a depth-1 star -- all members
point to the one base, the base points to nothing. The script asserts this
(base has base_word == null; every non-base's base resolves in one hop;
no word is its own base).

Fields added to each record:
  stem_key          "canon/id+canon/id+..."  (null if the word has no decomp)
  paradigm_size     number of 100k word forms sharing this stem
  is_inflection     true  -> this form is an inflection of `base_word`
                    false -> this form IS the paradigm's base
  base_word         word_rom of the base form   (null if is_inflection false)
  base_word_syl     word_syl of the base form   (unambiguous pointer)
  base_rank         frequency rank of the base form
  base_is_inflected true if the cluster had no uninflected citation form
  base_relation     for inflections only: how this form differs from the base
                      enclitic-only | nominal-inflection | verbal-agreement
                      | verbal-mood-change | verbal-valency-change
                      | verbal-inflection   (base is a citation form)
                    -- the meaning-propagation step treats nominal-inflection
                       and verbal-agreement as rule-safe, the rest as
                       "needs a mood template or an LLM fallback".

Input : ../../../data/lexicon/decompositions/hansard-top100k-decomps.jsonl
        scratchpad/rerank_100k_top2.jsonl   (re-ranked top-1 per word)
Output: scratchpad/hansard-top100k-decomps.with-base.jsonl
Run from data/grammar/fst/.
"""
import json
import re
from collections import Counter, defaultdict

LEXICON = "../../../data/lexicon/decompositions/hansard-top100k-decomps.jsonl"
RERANK = "scratchpad/rerank_100k_top2.jsonl"
OUT = "scratchpad/hansard-top100k-decomps.with-base.jsonl"

ENDING_RE = re.compile(r"^t[nvd]-|^t[ap]d-")
ENCL_RE = re.compile(r"^\d+q$")
SEG_RE = re.compile(r"\{([^:}]+):([^/}]+)/([^}]+)\}")


def segs(decomp):
    return [(c, i) for _s, c, i in SEG_RE.findall(decomp)]


def stem_pairs(pairs):
    ids = [i for _c, i in pairs]
    e = len(ids)
    while e and ENCL_RE.match(ids[e - 1]):
        e -= 1
    cut = e
    for k in range(e):
        if ENDING_RE.match(ids[k]):
            cut = k
            break
    return tuple(pairs[:cut])


def ending_ids(pairs):
    return [i for _c, i in pairs if ENDING_RE.match(i)]


def enclitic_ids(pairs):
    return [i for _c, i in pairs if ENCL_RE.match(i)]


def ending_kind(eids):
    if not eids:
        return ("bare",)
    e = eids[-1]
    if e.startswith("tv-"):
        p = e.split("-")
        return ("v", p[1], "tr" if len(p) >= 4 else "itr")
    return ("n",)


def relation(base_e, mem_e):
    bk, mk = ending_kind(base_e), ending_kind(mem_e)
    if not mem_e and not base_e:
        return "enclitic-only"
    if mk[0] == "v" and bk[0] == "v":
        if mk[1] != bk[1]:
            return "verbal-mood-change"
        if mk[2] != bk[2]:
            return "verbal-valency-change"
        return "verbal-agreement"
    if mk[0] == "v":
        return "verbal-inflection"
    return "nominal-inflection"


def tier(pairs):
    has_end = bool(ending_ids(pairs))
    has_enc = bool(enclitic_ids(pairs))
    if not has_end and not has_enc:
        return 0
    if not has_end:
        return 1
    return 2


def main():
    # re-ranked top-1 per word, keyed by frequency rank (unique)
    top1 = {}
    for line in open(RERANK, encoding="utf-8"):
        r = json.loads(line)
        top1[r["rank"]] = r["top1"]["decomp"]

    # cluster analyzable words by stem signature
    clusters = defaultdict(list)          # stem_key -> [rank, ...]
    info = {}                             # rank -> dict
    for line in open(LEXICON, encoding="utf-8"):
        rec = json.loads(line)
        d = top1.get(rec["rank"])
        if not d:
            continue
        p = segs(d)
        sp = stem_pairs(p)
        key = "+".join(f"{c}/{i}" for c, i in sp) or "(none)"
        info[rec["rank"]] = {
            "rank": rec["rank"], "word_rom": rec["word_rom"],
            "word_syl": rec["word_syl"], "decomp": d,
            "ending": ending_ids(p), "tier": tier(p), "key": key,
        }
        clusters[key].append(rec["rank"])

    # choose a base per cluster + assign links
    link = {}                            # rank -> fields dict
    for key, ranks in clusters.items():
        members = sorted((info[r] for r in ranks), key=lambda m: m["rank"])
        best_tier = min(m["tier"] for m in members)
        base = min((m for m in members if m["tier"] == best_tier),
                   key=lambda m: m["rank"])
        base_is_inflected = best_tier == 2
        for m in members:
            is_base = m["rank"] == base["rank"]
            same_str = m["word_rom"] == base["word_rom"] and not is_base
            if is_base or same_str:
                link[m["rank"]] = dict(
                    stem_key=key, paradigm_size=len(members),
                    is_inflection=False, base_word=None, base_word_syl=None,
                    base_rank=None, base_is_inflected=base_is_inflected,
                    base_relation=None)
            else:
                link[m["rank"]] = dict(
                    stem_key=key, paradigm_size=len(members),
                    is_inflection=True, base_word=base["word_rom"],
                    base_word_syl=base["word_syl"], base_rank=base["rank"],
                    base_is_inflected=base_is_inflected,
                    base_relation=relation(base["ending"], m["ending"]))

    # --- loop / integrity checks ---------------------------------------
    base_rank_of = {r: f["base_rank"] for r, f in link.items()}
    for r, f in link.items():
        if not f["is_inflection"]:
            assert f["base_word"] is None
            continue
        b = f["base_rank"]
        assert b is not None and b != r, f"self or missing base for {r}"
        assert not link[b]["is_inflection"], f"chain: {r} -> {b} -> ..."
        assert base_rank_of.get(b) is None
    print("integrity OK: every link resolves to a base in exactly one hop; "
          "no cycles, no chains, no self-references.")

    # --- write augmented file ---------------------------------------------
    n = infl = base_infl = nolink = 0
    rels = Counter()
    psize = Counter()
    with open(LEXICON, encoding="utf-8") as fin, \
         open(OUT, "w", encoding="utf-8") as fout:
        for line in fin:
            rec = json.loads(line)
            f = link.get(rec["rank"])
            if f is None:
                rec.update(stem_key=None, paradigm_size=0, is_inflection=False,
                           base_word=None, base_word_syl=None, base_rank=None,
                           base_is_inflected=False, base_relation=None)
                nolink += 1
            else:
                rec.update(f)
                infl += f["is_inflection"]
                if not f["is_inflection"]:
                    psize[f["paradigm_size"]] += 1
                    base_infl += f["base_is_inflected"] and f["paradigm_size"] > 1
                else:
                    rels[f["base_relation"]] += 1
            fout.write(json.dumps(rec, ensure_ascii=False) + "\n")
            n += 1

    print(f"\n{OUT}")
    print(f"  {n} records; {n-nolink} analyzable, {nolink} with no decomp "
          f"(stem_key=null, is_inflection=false)")
    print(f"  inflections of another word : {infl} "
          f"({100*infl/(n-nolink):.1f}% of analyzable)")
    print(f"  paradigm bases              : {n-nolink-infl}")
    print(f"    of which the base is itself an inflected form (no citation "
          f"form attested) : {base_infl}")
    print(f"\n  base_relation of the inflections:")
    for k, v in rels.most_common():
        print(f"    {k:24} {v:6d}  ({100*v/infl:.1f}%)")
    safe = rels["nominal-inflection"] + rels["verbal-agreement"] + rels["enclitic-only"]
    print(f"    -> rule-safe (case/number/person/enclitic) : {safe} "
          f"({100*safe/infl:.0f}%);  needs mood-template / LLM : {infl-safe} "
          f"({100*(infl-safe)/infl:.0f}%)")
    print(f"\n  paradigm-size distribution (bases): "
          + ", ".join(f"{s}:{psize[s]}" for s in sorted(psize)[:10]) + " ...")
    big = sorted(clusters.items(), key=lambda kv: -len(kv[1]))[:8]
    print("  biggest paradigms: "
          + "; ".join(f"{k} x{len(v)}" for k, v in big))


if __name__ == "__main__":
    main()
