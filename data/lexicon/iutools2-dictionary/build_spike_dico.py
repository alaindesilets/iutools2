"""
Phase 1 of the iutools2-dictionary spike (see README.md): build a small,
structural-only (no LLM) words.jsonl to sanity-check the schema and the
"potential stem" idea on real data before spending any LLM budget.

1. Sample 100 words uniformly at random from the (already GBDT-resorted,
   see resort_100k_decomps.py) 100k analyzed lexicon -- true random, not
   frequency-weighted, so it mixes frequent words and long-tail ones.
2. For each seed word's top-2 (re-ranked) decomps, derive a potential
   stem signature (root through the last derivational suffix -- same
   definition as build_inflection_links.py's stem_pairs()). Not attested;
   just a structural hypothesis at this stage.
3. Find every word in the 100k whose OWN top-2 decomps share that same
   potential stem (symmetric criterion), via a pre-built index over the
   whole lexicon.
4. Emit every word found (seeds + matches) into
   data/lexicon/iutools2-dictionary/words.jsonl, with only the fields
   derivable without an LLM call: word_roman, word_syll, rank, count,
   decomps (each entry's llm_attested left null -- not yet known),
   top_decomp, top_decomp_is_lenient, some_lenient_decomps_included,
   several_decomps_are_llm_attested (null, same reason),
   definitions_found_in (null). No "stems" field: which stem is actually
   attested is exactly what Phase 2 (an LLM pass) is for.

Run from data/lexicon/iutools2-dictionary/.
"""
import json
import random
import re

LEXICON = "../decompositions/hansard-top100k-decomps.jsonl"
OUT = "words.jsonl"
SEED = 20260911

ENDING_RE = re.compile(r"^t[nvd]-|^t[ap]d-")
ENCL_RE = re.compile(r"^\d+q$")
SEG_RE = re.compile(r"\{([^:}]+):([^/}]+)/([^}]+)\}")


def parse(decomp):
    """decomp string -> [(canonical, id), ...]."""
    return [(c, i) for _s, c, i in SEG_RE.findall(decomp)]


def stem_pairs(pairs):
    """Same definition as build_inflection_links.py's stem_pairs(): root
    through the last derivational suffix -- drop trailing enclitics, then
    cut before the first inflectional ending."""
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


def as_decomp_array(pairs):
    return [f"{c}/{i}" for c, i in pairs]


def load_analyzable():
    out = {}
    with open(LEXICON, encoding="utf-8") as fh:
        for line in fh:
            rec = json.loads(line)
            if rec["analyzer_outcome"] != "completed" or not rec.get("decomps"):
                continue
            out[rec["word_rom"]] = rec
    return out


def top_n_stems(rec, n=2):
    """Potential stems of this word's top-N (re-ranked) decomps, deduped."""
    stems = []
    seen = set()
    for d in rec["decomps"][:n]:
        s = stem_pairs(parse(d))
        if s and s not in seen:
            seen.add(s)
            stems.append(s)
    return stems


def build_stem_index(lexicon):
    """potential stem (tuple of (canonical, id)) -> set of word_rom sharing
    it via THEIR OWN top-2 decomps (symmetric with how seed stems are
    derived)."""
    index = {}
    for word, rec in lexicon.items():
        for s in top_n_stems(rec, 2):
            index.setdefault(s, set()).add(word)
    return index


def to_words_jsonl_entry(rec):
    decomps = [
        {"decomp": as_decomp_array(parse(d)), "lenient": bool(l),
         "llm_attested": None}
        for d, l in zip(rec["decomps"], rec.get("decomps_lenient") or
                         [False] * len(rec["decomps"]))
    ]
    return {
        "word_roman": rec["word_rom"],
        "word_syll": rec["word_syl"],
        "rank": rec["rank"],
        "count": rec["count"],
        "decomps": decomps,
        "top_decomp": decomps[0]["decomp"],
        "top_decomp_is_lenient": decomps[0]["lenient"],
        "some_lenient_decomps_included": any(d["lenient"] for d in decomps),
        "several_decomps_are_llm_attested": None,
        "definitions_found_in": None,
    }


def main():
    print("loading the resorted 100k lexicon ...", flush=True)
    lexicon = load_analyzable()
    print(f"  {len(lexicon)} analyzable words", flush=True)

    print("indexing every word's top-2 potential stems ...", flush=True)
    stem_index = build_stem_index(lexicon)
    print(f"  {len(stem_index)} distinct potential stems", flush=True)

    rng = random.Random(SEED)
    seeds = rng.sample(sorted(lexicon), 100)

    words_to_include = set(seeds)
    stems_seen = set()
    for w in seeds:
        for s in top_n_stems(lexicon[w], 2):
            stems_seen.add(s)
            words_to_include |= stem_index.get(s, set())

    print(f"\n{len(seeds)} seed words -> {len(stems_seen)} distinct potential "
          f"stems -> {len(words_to_include)} words total (incl. seeds)")

    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(json.dumps({
            "_comment": "SPIKE DICTIONARY -- Phase 1 output "
                        "(build_spike_dico.py). Structural only, no LLM "
                        "call yet: every decomps[].llm_attested is null, "
                        "'stems' is deliberately absent (not yet "
                        "attested), definitions_found_in is null. NOT the "
                        "real dataset. See README.md, Phase 1/Phase 2.",
        }, ensure_ascii=False) + "\n")
        for w in sorted(words_to_include, key=lambda w: lexicon[w]["rank"]):
            fh.write(json.dumps(to_words_jsonl_entry(lexicon[w]),
                                 ensure_ascii=False) + "\n")

    print(f"wrote {OUT} ({len(words_to_include) + 1} lines incl. the "
          f"header comment)")


if __name__ == "__main__":
    main()
