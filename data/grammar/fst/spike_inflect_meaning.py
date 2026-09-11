"""
SPIKE (doc/dev/plans/offline-dictionary-generation.md): to run Guess-Meaning /
Find-Dictionary-Entry over the 100k words, can we call the LLM ONCE per stem
(paradigm) and RULE-render every other inflected form's meaning from the
ending's grammatical features -- instead of one LLM call per word?

  1. cluster the 100k top-1 decomps by stem-signature (root .. last
     derivational suffix; drop inflectional ending + enclitics);
  2. draw 18 pairs (B, A) AT RANDOM (seed 20260908) from stems with >=2
     forms -- B = the more frequent form (the one we would "spend" the LLM
     call on), A = another form of the same stem;
  3. B_MEANING below = Guess Meaning for B, produced by Claude playing the
     GM model for this spike (no API key used), reduced to the uninflected
     LEMMA it implies;
  4. rule-render A from (lemma, A's ending features);
  5. A_INDEP below = an INDEPENDENT reading of A (also Claude-as-GM); we
     check whether the rule reproduces it.

The deliverable is the RULE engine (ending-id -> phrase template) + an
honest verdict on where it holds and where it breaks. Pure stdlib.
Run from data/grammar/fst/.
"""
import json
import random
import re
from collections import defaultdict

IN = "scratchpad/rerank_100k_top2.jsonl"
ENDING_RE = re.compile(r"^t[nvd]-|^t[ap]d-")
ENCL_RE = re.compile(r"^\d+q$")
SEG_RE = re.compile(r"\{([^:}]+):([^/}]+)/([^}]+)\}")


def segs(d):
    return [(c, i) for _s, c, i in SEG_RE.findall(d)]


def stem_sig(p):
    ids = [i for _c, i in p]
    e = len(ids)
    while e and ENCL_RE.match(ids[e - 1]):
        e -= 1
    cut = e
    for k in range(e):
        if ENDING_RE.match(ids[k]):
            cut = k
            break
    return tuple((c, i) for c, i in p[:cut])


def endings(p):
    return tuple(i for _c, i in p if ENDING_RE.match(i)) or ("(bare)",)


def enclitics(p):
    return tuple(c for c, i in p if ENCL_RE.match(i))


# --- ending-id -> feature bundle + English template --------------------------
# case -> adposition; possessor 4s/4p rendered "his/her"/"their" (obviative);
# article (a/the) is NOT rule-recoverable and is normalised out when scoring.
NOMINAL = {
    "(bare)": ("", "sg"), "tn-nom-s": ("", "sg"), "tn-nom-p": ("", "pl"),
    "tn-nom-d": ("two ", "du"),
    "tn-acc-s": ("", "sg", " [obj]"), "tn-acc-p": ("", "pl", " [obj]"),
    "tn-gen-s": ("of ", "sg"), "tn-gen-p": ("of ", "pl"),
    "tn-abl-s": ("from ", "sg"), "tn-abl-p": ("from ", "pl"),
    "tn-loc-s": ("in ", "sg"), "tn-loc-p": ("in ", "pl"),
    "tn-dat-s": ("to ", "sg"), "tn-dat-p": ("to ", "pl"),
    "tn-via-s": ("through ", "sg"), "tn-via-p": ("through ", "pl"),
    "tn-sim-s": ("like ", "sg"),
    "tn-nom-s-1p": ("our ", "sg"),
    "tn-nom-s-4s": ("his/her ", "sg"), "tn-nom-s-4p": ("their ", "sg"),
    "tn-nom-p-4s": ("his/her ", "pl"), "tn-nom-p-4d": ("their ", "pl"),
    "tn-gen-s-4s": ("of his/her ", "sg"),
    "tn-dat-s-4s": ("to his/her ", "sg"), "tn-dat-p-4s": ("to his/her ", "pl"),
}
VERBAL = {
    "tv-ger-1s": ("I", "V"), "tv-ger-1p": ("we", "V"), "tv-ger-2s": ("you", "V"),
    "tv-ger-3s": ("he/she", "Vs"), "tv-ger-3p": ("they", "V"),
    "tv-dec-1s": ("I", "V"), "tv-dec-1p": ("we", "V"), "tv-dec-3p": ("they", "V"),
    "tv-ger-1s-3s": ("I", "V it"), "tv-ger-1p-3s": ("we", "V it"),
    "tv-dec-1s-3s": ("I", "V it"), "tv-dec-1s-3p": ("I", "V them"),
    "tv-ger-1s-3p": ("I", "V them"),
    "tv-part-1s-prespas": ("while I", "V"), "tv-part-1p-prespas": ("while we", "V"),
    "tv-part-1s-3p-prespas": ("while I", "V them"),
    "tv-caus-1s": ("because I", "V"), "tv-caus-1p": ("because we", "V"),
    "tv-caus-4p": ("because they", "V"),
    "tv-imp-1d": ("let's (two of us)", "V"),
}


def render_noun(lemma_sg, lemma_pl, eid):
    spec = NOMINAL[eid]
    pre, num = spec[0], spec[1]
    suf = spec[2] if len(spec) > 2 else ""
    return f"{pre}{lemma_pl if num == 'pl' else lemma_sg}{suf}"


def render_verb(pred, eid):
    bare, s3 = pred
    subj, tmpl = VERBAL[eid]
    return f"{subj} " + tmpl.replace("Vs", s3).replace("V", bare)


# --- the 18 random pairs, with Claude-as-GM meanings ------------------------
# lemma = the uninflected sense taken from B's single Guess-Meaning call.
# kind: "noun"/"verb"; pred = (bare, 3sg) for verbs.
PAIRS = [
    # stem, B_form, A_form, B_eid, A_eid, kind, lemma_sg, lemma_pl, pred, A_indep, note
    ("sana+juksaq", "sanajuksanik", "sanajuksat", "tn-acc-p", "tn-nom-p",
     "noun", "thing to be built", "things to be built", None,
     "things to be built", ""),
    ("niqi+tsiavak", "niqittiavannut", "niqittiavanni", "tn-dat-p", "tn-loc-p",
     "noun", "good food", "good foods", None, "in good foods", ""),
    ("uqalimaagaq+u+juq", "uqalimaagaujuq", "uqalimaagaujumik", "(bare)", "tn-acc-s",
     "noun", "book", "books", None, "a book [obj]", ""),
    ("ilitaq+gi+juma+gi", "ilitarijumagivara", "ilisarijumagivakka",
     "tv-dec-1s-3s", "tv-dec-1s-3p", "verb", None, None, ("want to recognize", "wants to recognize"),
     "I want to recognize them", "object sg->pl only"),
    ("quja+li+qati+gi+juma", "qujaliqatigijumavakka", "qujaliqatigijumajakka",
     "tv-dec-1s-3p", "tv-ger-1s-3p", "verb", None, None,
     ("want to have as a thanking-companion", "wants ..."),
     "(the ones) I want to have as thanking-companions", "MOOD shift dec->ger"),
    ("aupaq+luk+juq", "aupaluktuq", "aupaluktuup", "(bare)", "tn-gen-s",
     "noun", "the red one", "the red ones", None, "of the red one", ""),
    ("nuna+taaq+qatau+sima+nngit", "nunataaqatausimanngittut", "nunataaqatausimanngittut",
     "tv-ger-3p", "tv-ger-3p", "verb", None, None,
     ("have not taken part in getting new land", "has not ..."),
     "they have not taken part in getting new land", "SAME surface form twice (100k dup)"),
    ("mikik+luaq+niq", "mikiluarninganut", "mikiluarninginnut",
     "tn-dat-s-4s", "tn-dat-p-4s", "noun", "its being too small", "its instances of being too small",
     None, "to its being too small (pl)", "possessum sg->pl"),
    ("qangata+suuq", "qangatajuunik", "qangatajuumit", "tn-acc-p", "tn-abl-s",
     "noun", "airplane", "airplanes", None, "from an airplane", ""),
    ("arvaq+aq", "arvaarluk", "arvaarlu", "tv-imp-1d", "tv-imp-1d",
     "verb", None, None, ("hunt bowhead whale", "hunts bowhead whale"),
     "let's (two of us) hunt bowhead whale", "same ending, surface allomorph luk/lu"),
    ("pi+quti", "piqutinit", "piqutiup", "tn-abl-p", "tn-gen-s",
     "noun", "belonging", "belongings", None, "of the property", ""),
    ("nani+si+giaq+qaq", "nanisigiaqaratta", "nanisigiaqattugut",
     "tv-caus-1p", "tv-ger-1p", "verb", None, None,
     ("have to go and find", "has to go and find"),
     "we go to find (it)", "MOOD shift caus->ger"),
    ("sana+jaq+ksaq", "sanajaksamik", "sanajassait", "tn-acc-s", "tn-nom-p",
     "noun", "material to be made into something", "materials to be made",
     None, "materials to be made", ""),
    ("qauji+juma+mi", "qaujigumammijunga", "qaujijumammijunga", "tv-ger-1s", "tv-ger-1s",
     "verb", None, None, ("want to find out", "wants to find out"),
     "I want to find out", "same ending, root/affix allomorph"),
    ("arsarniq", "aqsarniit", "aqsarniup", "tn-nom-p", "tn-gen-s",
     "noun", "aurora / northern lights", "auroras", None, "of the northern lights",
     "LEXICALISED root -- composing from morphemes would fail; reusing B works"),
    ("akau+nngit+li+uq+jjut+u+juq", "akaunngiliurutiujunik", "akaunngiliurutiujumit",
     "tn-acc-p", "tn-abl-s", "noun", "a disadvantage / a bad thing",
     "disadvantages", None, "from something that is not good", ""),
    ("ikajuq+qu", "ikajuqullugit", "ikajuqulluta",
     "tv-part-1s-3p-prespas", "tv-part-1p-prespas", "verb", None, None,
     ("tell/ask (someone) to help", "tells ..."),
     "while we help / so that we help", "VALENCY change: 1s+3p transitive -> 1p intransitive"),
    ("taku+vallia", "takuvalliajavut", "takuvalliagatta",
     "tv-ger-1p-3s", "tv-caus-1p", "verb", None, None,
     ("gradually come to see", "gradually comes to see"),
     "because we gradually come to see (it)", "MOOD shift ger->caus, object dropped"),
]

STOP = {"a", "an", "the", "of", "to", "from", "in", "at", "through", "like",
        "his", "her", "their", "our", "two", "obj", "while", "because", "so",
        "that", "it", "them", "us", "you", "i", "we", "they", "s", "lets",
        "one", "the", "ones", "(the", "let's"}


def content(s):
    s = s.lower().replace("(", " ").replace(")", " ").replace("/", " ")
    return frozenset(w.strip(".,") for w in re.split(r"[^a-z'-]+", s)
                     if w and w not in STOP)


def verify_random_sample():
    """Re-draw the 18 pairs to confirm PAIRS matches the seeded sample."""
    grp = defaultdict(list)
    for r in (json.loads(l) for l in open(IN, encoding="utf-8")):
        p = segs(r["top1"]["decomp"])
        if p:
            grp[stem_sig(p)].append(r)
    clusters = [(k, v) for k, v in grp.items() if len(v) >= 2]
    rng = random.Random(20260908)
    rng.shuffle(clusters)
    out = []
    for k, v in clusters[:18]:
        a, b = rng.sample(v, 2)
        B, A = (a, b) if a["rank"] < b["rank"] else (b, a)
        out.append((B["word_rom"], A["word_rom"]))
    return out


def main():
    drawn = verify_random_sample()
    ok_gram = ok_str = 0
    fails = []
    for i, row in enumerate(PAIRS):
        (stem, bw, aw, beid, aeid, kind, lsg, lpl, pred, indep, note) = row
        assert (bw, aw) == drawn[i], f"pair {i} drift: {drawn[i]} vs {(bw, aw)}"
        got = (render_noun(lsg, lpl, aeid) if kind == "noun"
               else render_verb(pred, aeid))
        gram = content(got) == content(indep)
        exact = got.lower().strip() == indep.lower().strip()
        ok_gram += gram
        ok_str += exact
        if not gram:
            fails.append((aw, got, indep, note))
        print(f"{i+1:2}. {stem}")
        print(f"    B {bw:26} [{beid}]  -> LLM lemma: "
              f"{lsg if kind == 'noun' else 'to ' + pred[0]!r}")
        print(f"    A {aw:26} [{aeid}]")
        print(f"       rule  : {got}")
        print(f"       indep : {indep}")
        print(f"       {'== grammar OK' if gram else '!! MISMATCH'}"
              f"{'  (exact)' if exact else ''}   {note}")
    n = len(PAIRS)
    print(f"\n--- grammatical transform correct: {ok_gram}/{n} "
          f"({100*ok_gram//n}%)   exact string: {ok_str}/{n}")
    if fails:
        print("misses:")
        for aw, got, indep, note in fails:
            print(f"  {aw:24} rule={got!r}  indep={indep!r}  <- {note}")


if __name__ == "__main__":
    main()
