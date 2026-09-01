"""
Bulk-generates lexc entries for every remaining derivational suffix
(Suffixes.csv, Suffixes_additional.csv) and terminal ending
(Endings_noun.csv, Endings_verb.csv, Endings_verb_participle.csv) whose
context-by-context CSV row uses ONLY an action code this project has
already built a mechanism for: Neutral (plain literal), Suppression
(SUPPR), Nasalization (NASAL), Voicing (VOICE), Fusion (functionally
identical to Suppression, per Action.kt's own comment -- "Fusion should
be replaced in the database by suppression"), Assimilation (the CSV's
own form column for that context already spells the assimilated
surface text directly, same "titut"/tn-sim-p precedent already in
lexicon.lexc -- no marker needed, same treatment as Neutral), and
Selfdecapitation as action2 stacked on Neutral/Suppression as action1
(same SUPPR+DECAP marker stack already used for "it"/tn-nom-p).

Also handles two extensions added after the first version's initial
results were evaluated (see candidates_for_context's own docstring and
TRIGGER_LEXICON's comment for the details of each): the VowelLengthening/
Cancellation family (allV/sallV/iallV), reusing the existing TNNOMD
marker rather than a new mechanism; and generalizing the existing Lever
A trigger-branch pattern to the small set of rows gated by a bare
"id:X" condition for an already-implemented trigger X.

This is a deliberately permissive experiment, not the project's usual
one-affix-at-a-time gold-verified methodology: every literal candidate
a row lists is wired in parallel (multi-candidate cells split on
whitespace, one entry per position -- the established "different
strings never collide" treatment used throughout lexicon.lexc), without
first checking a specific gold word needs it. Rows are still SKIPPED
(not guessed at) when:
  - any context's action code isn't one of the ones handled (the
    specific/conditional variants -- s(...)/si(...)/ssi(...)/
    nassi(...) -- are genuinely new mechanisms, not yet built);
  - a condition column is non-empty but ISN'T a bare, single "id:X" for
    a known trigger (a negated "!id:X", a condition combined with
    another one, or a grammatical-feature condition like "mode:imp"/
    "cas:dat"/"type:v" -- these need modeling context this project
    doesn't parse yet, not attempted here);
  - the form/action1/action2 candidate lists for a context don't split
    into equal-length parallel lists (a data-quality edge case, rare).

Routing: a derivational suffix's own "function" column (Suffixes.csv/
Suffixes_additional.csv) is a 2-letter code, source+target category
(e.g. "vn" = verb-to-noun) -- already established convention throughout
lexicon.lexc's own hand-authored tags (+1vn, +1nv, +1nn, +1vv, etc.). Each
function code gets its own generated LEXICON block (see FUNCTION_LEXICON),
reached from the hub matching its INPUT (first letter) category -- vn/vv
from VerbContinuations, nn/nv from NounContinuations, same split the
hand-authored VnSuffixes/NvSuffixes/VvSuffixes/NnSuffixes blocks already
use. A suffix's own continuation (what it leads to NEXT) instead matches
its OUTPUT (second letter) category (see FUNCTION_CONTINUATION) -- vn/nn
continue into NounContinuations, nv/vv into VerbContinuations. "q"-function
(enclitic particle, e.g. li/1q, lu/1q) suffixes are terminal-only ("#"),
matching the ones already in QParticles. Every suffix also gets a bare "#"
terminal path in addition to its hub, mirroring the near-universal pattern
of existing entries. Terminal endings (Endings_noun.csv/Endings_verb.csv/
Endings_verb_participle.csv) always route to "#" only.

Tag construction (reverse-engineered from lexicon.lexc's already-
validated tags, confirmed against known examples before writing this):
  - Suffixes: f"+{nb}{function}"
  - Endings_noun: f"tn-{case}-{number}" + (f"-{perPoss}{numbPoss}" if
    perPoss else "")
  - Endings_verb: f"tv-{mode}-{perSubject}{numbSubject}" +
    (f"-{perObject}{numbObject}" if perObject else "")
  - Endings_verb_participle: f"tv-{mode}" + (f"-{perSubject}
    {numbSubject}" if perSubject else "") + (f"-{perObject}
    {numbObject}" if perObject else "") + (f"-{tense}" if tense else "")
    -- confirmed exactly against the 4 tv-part-* tags already in
    lexicon.lexc (lugu/tv-part-1s-3s-prespas, lugit/
    tv-part-1s-3p-prespas, tillugit/tv-part-3p, lugu/tv-part-1s-3s-fut).
    Note dialect/sameSubject/posneg are NOT part of the tag -- rows that
    differ only by those columns share one tag with different literal
    spellings (dialectal free variation, same "wire both" treatment).

Output: SEPARATE files (suffixes-generated.lexc, endings-generated.lexc),
never hand-edited, mirroring generate_roots.py's own convention.

Usage (from tools/fst/):
    python3 generate_affixes.py
"""
import csv
import re
from pathlib import Path

REPO_ROOT = Path(__file__).parent.parent.parent
DATA_DIR = REPO_ROOT / "core/src/commonMain/resources/org/iutools/linguisticdata/dataCSV"
SUFFIXES_OUT = Path(__file__).parent / "suffixes-generated.lexc"
ENDINGS_OUT = Path(__file__).parent / "endings-generated.lexc"

# action code -> marker to prefix the literal candidate text with (None
# = plain literal, no marker). Codes not in this map are unhandled and
# cause that candidate to be skipped.
KNOWN_ACTION1 = {
    "n": None,
    "s": "SUPPR",
    "son": "VOICE",
    "nas": "NASAL",
    "fus": "SUPPR",
    "a": None,
}

FUNCTION_CONTINUATION = {
    "vn": "NounContinuations",
    "nn": "NounContinuations",
    "nv": "VerbContinuations",
    "vv": "VerbContinuations",
}

TYPE_CONDITION_RE = re.compile(r"^type:([vn])(?: function:\w+)*$")


def condition_redundant_with_routing(cond_value: str, own_function: str) -> bool:
    """True when a condPrec value is EXACTLY the shape "type:{v|n}"
    optionally followed by "function:X" tokens (e.g. "type:v function:nv
    function:vv") -- this isn't a guess: this project's own routing
    ALREADY guarantees it. A suffix with own_function "nv" or "vv" is,
    by FUNCTION_CONTINUATION's own construction, only ever reached via
    VerbContinuations (either a raw type:v root, or the output of
    another nv/vv suffix) -- which is precisely the membership this
    condition describes. Confirmed by inspection before relying on it:
    every row observed with this exact condition shape lists exactly
    the function codes FUNCTION_CONTINUATION already funnels into the
    matching hub, never a mismatched or narrower set."""
    m = TYPE_CONDITION_RE.fullmatch(cond_value.strip())
    if not m:
        return False
    type_letter = m.group(1)
    expected_hub = "VerbContinuations" if type_letter == "v" else "NounContinuations"
    return FUNCTION_CONTINUATION.get(own_function) == expected_hub


def condition_status(row: dict, own_function: str, *cond_cols: str):
    """Returns ("ok", None), ("redundant", None), ("speculative", desc)
    or ("skip", None) for a row's condition columns, given the known
    single_id_trigger check already ran and returned no trigger.
    "redundant": condPrec is provably already satisfied by this
    project's own routing (see condition_redundant_with_routing) --
    not a guess, wire normally. "speculative": some other condition
    (case/mode/number/nature/transitivity/antipassive/an id: trigger
    this project hasn't wired a dedicated branch for) that this
    project's FST architecture genuinely can't check -- wired anyway,
    per Alain's explicit direction, but flagged as UNVERIFIED (see
    SPECULATIVE_LEXICON_SUFFIX) since there's no gold word left to
    confirm the guess against, unlike every other row this whole
    session's methodology was able to verify."""
    set_cols = [c for c in cond_cols if (row.get(c) or "").strip()]
    if not set_cols:
        return ("ok", None)
    if len(set_cols) == 1 and condition_redundant_with_routing(row[set_cols[0]], own_function):
        return ("redundant", None)
    desc = "; ".join(f"{c}={row[c]!r}" for c in set_cols)
    return ("speculative", desc)


SPECULATIVE_LEXICON_SUFFIX = "Speculative"


def split_or_none(cell: str):
    cell = (cell or "").strip()
    return None if not cell else cell.split(" ")


IALLV_RE = re.compile(r"iallV\((\w+)\)")
INSERTION_ACTION2_RE = re.compile(r"i\(\w+\)")


def candidates_for_context(row: dict, context: str):
    """Yields literal_lower_side strings for every candidate in one
    V/t/k/q context of a Suffixes-shaped or Endings-shaped row, or
    nothing if the context is empty/unhandled. See module docstring for
    the action-code policy.

    allV+x (VowelLengthening+Cancellation) and sallV+x
    (SuppressionAndVowelLengthening+Cancellation): per Action.kt/
    MorphAnalyzerValidation.kt (traced before writing this), these two
    action codes are handled TOGETHER as one paired interpretation, not
    sequentially like Selfdecapitation -- when a stem ends in a genuine
    doubled vowel, BOTH a lengthened-by-this-affix reading (allV) and an
    already-long/no-extra-lengthening reading (x) are valid, so both are
    wired as parallel candidates (same "wire what gold might need, let
    the input string pick" policy as every other multi-candidate case
    in this project). The allV reading is EXACTLY what the existing
    TNNOMD marker already does (duplicate whichever vowel precedes it,
    built for "k"/tn-nom-d, see lexicon.lexc's comment on that entry) --
    reused unchanged, not a new mechanism. sallV additionally deletes a
    preceding q/k/t first (SUPPR, already resolves before TNNOMD in
    phonology.xfscript's rule order, confirmed by reading that file
    before relying on the stacking).

    iallV(X) (InsertionAndVowelLengthening): per Action.kt, "insertion
    of X after t" specifically in T-context dual noun endings, then the
    inserted X is itself doubled -- unconditional once in that context
    (no stem-shape gating), so simply baked into the literal lower-side
    text as X+X+form, no marker needed at all (same "unconditional
    insertion baked into literal text" treatment as lugu/lugit's own
    "i(l)" case).
    """
    form = (row.get(f"{context}-form") or "").strip()
    if not form or form == "-":
        return
    forms = form.split(" ")
    actions1 = split_or_none(row.get(f"{context}-action1", "")) or [""] * len(forms)
    actions2 = split_or_none(row.get(f"{context}-action2", "")) or [""] * len(forms)
    if len(actions1) != len(forms) or len(actions2) != len(forms):
        return
    for lit, a1, a2 in zip(forms, actions1, actions2):
        a1 = a1.strip()
        # "-" is the CSV's own explicit "no action" sentinel, distinct
        # from an empty cell -- appears specifically as one candidate's
        # action2 in a multi-candidate row where the OTHER candidate does
        # have a real action2 (e.g. "nga"/tn-nom-s-4s's V-context:
        # "nga a" forms with action2 "- s") -- confirmed by scanning
        # every CSV for this shape before relying on it (43 rows). Without
        # this normalization the check below (`if a2 and a2 != "decap"`)
        # wrongly treated the literal string "-" as a real, unhandled
        # action and skipped an otherwise-clean Neutral/Suppression/etc
        # candidate -- caught via "atinga"/"maliganga" (both false
        # positives because "nga"/tn-nom-s-4s's own V-context "nga"
        # candidate was silently never wired).
        a2 = "" if a2.strip() == "-" else a2.strip()
        if "&" in lit:
            continue

        if a1 == "allV" and a2 == "x":
            yield f"TNNOMD{lit}"
            yield lit
            continue
        if a1 == "sallV" and a2 == "x":
            yield f"SUPPRTNNOMD{lit}"
            yield f"SUPPR{lit}"
            continue
        m = IALLV_RE.fullmatch(a1)
        if m and not a2:
            inserted = m.group(1)
            yield f"{inserted * 2}{lit}"
            continue
        if a1 == "i(l)" and not a2:
            # Insertion of "l" -- unconditional, baked directly into the
            # literal, no marker (same shape as iallV(X) above). Proven
            # 5 times over this same session (lugu/lugit's own prespas/
            # fut pairs, plus lunga/luta/lutik) with zero counterexamples
            # before generalizing here.
            yield f"l{lit}"
            continue
        if a1 == "i(i)" and not a2:
            # Insertion of "i" -- same unconditional-prepend model as
            # i(l) above (Action.kt's Insertion class: surfaceForm =
            # insertedText + form, no conditioning coded there). This is
            # overwhelmingly the T-CONTEXT candidate on noun/verb endings
            # (epenthetic "i" breaking up a t-final stem + the ending's
            # own consonant-initial form -- a well-known, general
            # Inuktitut pattern, not a one-off), 248 candidates across
            # ~90 distinct morphemes when this was added -- tried in
            # isolation from the other "i(X)" codes below (NOT i(ng)/
            # i(a)/etc. together) specifically so that a
            # full_corpus_check.py correct-not-present regression, if any,
            # is attributable to this one code. See i(ng)'s own comment
            # below for why THAT code is treated more cautiously.
            yield f"i{lit}"
            continue
        if a1 == "i(ng)" and not a2:
            # Insertion of "ng" -- NOT generalized the same unconditional
            # way as i(l)/i(i) above: already known (round 14, prior
            # session -- no further detail survives in this repo) to
            # sometimes need conditional gating instead of unconditional
            # insertion. Guessing at the condition risks the same silent
            # corpus-wide corruption class already seen twice (the TNNOMD
            # and j->t bugs in phonology.xfscript, both only caught by a
            # correct-not-present SPIKE, not by inspection) -- left
            # unhandled (skipped) until the actual condition is
            # rediscovered, not guessed at.
            continue

        if a1 not in KNOWN_ACTION1:
            continue
        marker1 = KNOWN_ACTION1[a1]
        if a2 == "decap":
            if a1 not in ("n", "s"):
                continue
            prefix = (marker1 or "") + "DECAP"
            yield f"{prefix}{lit}"
            continue
        if a2 and INSERTION_ACTION2_RE.fullmatch(a2):
            # An "i(X)" action2 this project doesn't model (X other than
            # the specific ones handled above, e.g. i(ra)/i(ng)/i(a)/
            # i(i)) -- rather than skip the row/candidate entirely,
            # ignore the insertion and keep action1's own semantics.
            # Proven twice already with real gold words and zero
            # counterexamples (allak/1vv's own "i(ra)", innaq/2vv's own
            # "i(ng)", both hand-wired this same way in lexicon.lexc
            # before this was generalized here) -- scoped narrowly to
            # rows where action1 ITSELF is fully understood (Neutral/
            # Suppression/etc.), never combined with a genuinely unknown
            # action1.
            yield f"{marker1 or ''}{lit}"
            continue
        if a2 == "s":
            # Suppression as the SECOND action (CSV action2 == "s"): e.g.
            # the "Lever A candidate B" on the dat/abl/loc/acc noun
            # endings (nut/mut/nik/mik/ni/mi/nit) -- action1 Neutral,
            # action2 Suppression = the plain literal but ALSO delete a
            # preceding q/k/t. Modelled with the same SUPPR marker as an
            # action1 Suppression (a no-op after a vowel-final stem). Not
            # gold-observed: the CSV row's own {context}-action2 column
            # literally says "s".
            prefix = "SUPPR" if "SUPPR" not in (marker1 or "") else marker1
            yield f"{prefix}{lit}"
            continue
        if a2:
            continue
        yield f"{marker1 or ''}{lit}"


def has_condition(row: dict, *cols) -> bool:
    return any((row.get(c) or "").strip() for c in cols)


# Generalizes the existing Lever A trigger-branch pattern (see
# lexicon.lexc's comment on "mut"/tn-dat-s in NounEndings for the
# original mechanism) to condPrec/condPrecTrans/condOnNext/
# condPrecSpecific values shaped EXACTLY "id:X" -- a row that applies
# ONLY when immediately preceded by the specific morpheme+tag X, no
# other condition combined with it (a negated "!id:X", or "id:X"
# alongside a second condition column like "mode:caus", is a genuinely
# different/harder case -- SKIPPED, not guessed at). Measured against
# the actual CSV data before building this: only 4 distinct single-id
# triggers appear this way (nngit/1vv: 25 rows, a parallel "negative
# mood" declarative/interrogative ending set; u/1nv: 2 clean rows,
# jaq/1vv "to seem, to be, to look, to act like" and jaq/3vn "like,
# similar to"; juq/1vn: 2 clean rows, paaluk/1vn "exaggeration" and
# uti/3nn "indefinite pronoun 'one'"; it/3nv: 1 row, li/4vv "to make
# that s.t. or s.o. become such" -- its own CSV row's condPrec is
# EXACTLY "id:it/3nv", nothing else) -- all four trigger morphemes are
# already implemented and well-connected, so a dedicated
# "After<X>TriggerSuffix" lexicon per trigger, reached only from that
# trigger's own lexicon.lexc entries (wired by hand, see that file), is
# a bounded, well-understood extension -- not a speculative one. it/3nv
# was left unwired as a trigger during the earlier CSV-completeness pass
# (li/4vv went into the *Speculative lexicon instead, still reachable
# from the general hub, just unlabeled) -- added here as a real,
# narrower fix: li/4vv is now reachable ONLY after it/3nv specifically,
# not after every verb stem.
TRIGGER_LEXICON = {
    "nngit/1vv": "AfterNngitTriggerSuffix",
    "u/1nv": "AfterUTriggerSuffix",
    "juq/1vn": "AfterJuqTriggerSuffix",
    "it/3nv": "AfterItTriggerSuffix",
}


def single_id_trigger(row: dict, *cols):
    """Returns the TRIGGER_LEXICON destination name if exactly one of
    the given condition columns is set, to a bare "id:X" for a known
    simple trigger X, and no other condition column is also set --
    else None (including for negated/combined conditions, which this
    project doesn't yet know how to evaluate)."""
    set_cols = [c for c in cols if (row.get(c) or "").strip()]
    if len(set_cols) != 1:
        return None
    value = row[set_cols[0]].strip()
    if not value.startswith("id:"):
        return None
    return TRIGGER_LEXICON.get(value[len("id:"):])


# Mirrors generate_roots.py's own ANTIPASSIVE_LEXICON (kept as a separate
# local copy, same as this project's existing TRIGGER_LEXICON pattern --
# generate_roots.py/generate_affixes.py are independent scripts, no
# shared import). See generate_roots.py's own comment for the full
# rationale (root-CATEGORY trigger, not a single-id trigger).
ANTIPASSIVE_LEXICON = {
    "si/1vv": "AfterApSiRoot",
    "i/1vv": "AfterApIRoot",
    "ji/1vv": "AfterApJiRoot",
    "ksi/1vv": "AfterApKsiRoot",
    "nnik/1vv": "AfterApNnikRoot",
    "ri/1vv": "AfterApRiRoot",
}

# Same self-referential category-trigger shape as antipassive, for the
# root-level "intransSuffix"/"transSuffix" CSV columns (see
# generate_roots.py's own INTRANSINFIX_LEXICON/TRANSINFIX_LEXICON
# comments for the full rationale/measurement).
INTRANSINFIX_LEXICON = {
    "gusuk/1vv": "AfterIntransGusukRoot",
    "ksaq/1vv": "AfterIntransKsaqRoot",
    "si/3vv": "AfterIntransSi3vvRoot",
    "suk/1vv": "AfterIntransSukRoot",
}
TRANSINFIX_LEXICON = {
    "gi/4vv": "AfterTransGi4vvRoot",
}
NATURE_LEXICON = {
    "a": "AfterNatureARoot",
    "nb": "AfterNatureNbRoot",
}

# Bare "type:v" (no "function:nv function:vv" OR-alternatives, unlike
# almost every other type-shaped condition) genuinely narrows: only 2
# suffixes have this exact shape (a/1vv, t/1vv), and unlike the OR form,
# it is NOT already guaranteed by hub routing -- a verb-producing stem
# can be reached either directly from a type:v ROOT or via an nv-suffix
# CONVERSION, and Graph.kt's own automaton doesn't distinguish the two
# (confirmed by reading it), so only the real Condition/AttrValCond
# check (inspecting the immediate predecessor's own "type" field) tells
# them apart. Suffixes' OWN "type" column is "sv"/"sn" (never "v"/"n"
# literally), so this naturally applies to ROOTS ONLY without any
# special-casing -- generate_roots.py grants it unconditionally to
# every type=v root.
TYPE_LEXICON = {
    "v": "AfterTypeVRoot",
}

# "ksaq/1nn" is the only morpheme (root or suffix) anywhere in the CSV
# data with a non-empty "plural" column (value "t") -- the plain "-t"
# nominative-plural noun ending's own condPrec ("pl:t") requires
# attaching after a stem whose own "plural" column says "t". No gold
# word currently exercises this pairing, but the data itself is
# unambiguous (a single real candidate), so implemented the same way
# as every other CSV-documented-but-gold-unverified narrowing this
# round (see condOnNext below) -- narrowing-only, so the worst case is
# a missing candidate, never a corrupted one.
PL_LEXICON = {
    "t": "AfterPlTRoot",
}

# condOnNext ("condition on the FOLLOWING morpheme") is the mirror of
# every mechanism above: instead of restricting who can PRECEDE a
# suffix, it restricts what the suffix's OWN outgoing continuation can
# be. Measured against the actual data before building this: 15 real
# rows (16 minus "&aq/1vv", which is skipped entirely regardless --
# "&"-prefixed morphemes are never generated at all, see the "&" in
# morpheme" filter elsewhere in this file), covering exactly 3 shapes:
#   - "id:X" or "id:X id:Y" (2 rows: jariaq/1vn, jjaa/1vv) -- must be
#     followed by ONE of a small, explicit set of specific suffixes.
#   - "mode:X ..." (9 rows) -- must be followed by a verb ending whose
#     own "mode" column matches one of the cited values.
#   - "number:X ..." on an nn-function suffix (2 rows: galaq/1nn,
#     rujuq/1nn) -- must be followed by a noun ending whose own
#     "number" column matches.
# Deliberately NOT covered: "number:X ..." on a VV-function suffix (2
# rows: kisauti/1vv, ujjuaq/1vv) -- Endings_verb.csv has NO plain
# "number" column, only "numbSubject"/"numbObject", and nothing in the
# data disambiguates which one a bare "number:X" condOnNext means --
# guessing between them risks getting the semantics backwards, so left
# untouched (still unconditioned, same as before this round) rather
# than guessed at. No gold word currently exercises ANY of the 15
# covered rows either, but unlike a genuine semantic guess, all 3
# covered shapes have an UNAMBIGUOUS mapping straight from the CSV's
# own columns -- narrowing-only, so the worst case is a missing
# candidate for some future word, never a corrupted one, consistent
# with every other *Speculative-class risk this project has accepted.
ID_ONLY_LEXICON = {
    "qaq/1nv": "OnlyQaqNvSuffix",
    "lik/1nn": "OnlyLikNnSuffix",
    "junniiq/1vv": "OnlyJunniiqVvSuffix",
    "nngit/1vv": "OnlyNngitVvSuffix",
}
MODE_LEXICON = {
    m: f"OnlyMode{m.capitalize()}Endings"
    for m in ("caus", "cond", "freq", "dub", "int", "dec", "ger", "imp", "part")
}
NOUN_NUMBER_LEXICON = {
    "d": "OnlyNumberDNounEndings",
    "p": "OnlyNumberPNounEndings",
}


def condonnext_lexicons(row: dict, function: str):
    """Returns a LIST of narrow lexicon names this row's own OUTGOING
    continuation should be restricted to (replacing the normal
    "#"+FUNCTION_CONTINUATION[function] pair entirely -- condOnNext
    means something specific MUST follow, so unlike every condPrec-based
    mechanism above, "#" is deliberately NOT included), or None if
    condOnNext is empty or shaped in a way this function doesn't cover
    (see the ID_ONLY_LEXICON/MODE_LEXICON/NOUN_NUMBER_LEXICON comment
    above for exactly what's covered and why)."""
    value = (row.get("condOnNext") or "").strip()
    if not value:
        return None
    tokens = value.split(" ")
    if all(t.startswith("id:") for t in tokens):
        lexicons = [ID_ONLY_LEXICON.get(t[len("id:"):]) for t in tokens]
        return lexicons if all(lexicons) else None
    if all(t.startswith("mode:") for t in tokens):
        lexicons = [MODE_LEXICON.get(t[len("mode:"):]) for t in tokens]
        return lexicons if all(lexicons) else None
    if function == "nn" and all(t.startswith("number:") for t in tokens):
        lexicons = [NOUN_NUMBER_LEXICON.get(t[len("number:"):]) for t in tokens]
        return lexicons if all(lexicons) else None
    return None


def category_trigger(row: dict, prefix: str, lexicon_map: dict):
    """Generalizes single_id_trigger to condPrec values shaped EXACTLY
    "<prefix>:X" (e.g. "antipassive:X", "intransinfix:X", "transinfix:X")
    where X is a key of lexicon_map -- a root-CATEGORY trigger (any root
    whose own CSV column lists X), not a single fixed morpheme id.
    Like antipassive_trigger before this was generalized: deliberately
    checks ONLY condPrec, ignoring whatever else might be set in
    condPrecTrans/condOnNext (separate, still-unimplemented mechanisms)
    -- safe to under-constrain, never to over-constrain, since ignoring
    them can only make the result STRICTLY NARROWER than today's
    unconditioned *Speculative reachability, never wider.

    Unlike antipassive's own condPrec (a bare "antipassive:X"),
    intransinfix/transinfix rows have condPrec shaped "type:v,<prefix>:X"
    (an AND with a type check) -- strip a leading "type:v," or "type:n,"
    before matching and ignore it, same as condPrecTrans/condOnNext
    above: these suffixes are already vv-function, only reachable from
    VerbContinuations, so "type:v" is ALMOST always already true by
    construction -- the one narrow gap (the immediate predecessor being
    an nv-CONVERTED verbal stem rather than a genuine type:v root, which
    Stage 1's routing can't distinguish) is accepted as the SAME
    "narrower than before, not perfectly precise" tradeoff as ignoring
    condPrecTrans, not a new risk category."""
    value = (row.get("condPrec") or "").strip()
    for known_prefix in ("type:v,", "type:n,"):
        if value.startswith(known_prefix):
            value = value[len(known_prefix):]
            break
    if not value.startswith(f"{prefix}:"):
        return None
    return lexicon_map.get(value[len(prefix) + 1:])


# "cas:X" condPrec means: attach only after a NOUN THAT ALREADY HAS a
# case ending of case X -- structurally different from every trigger
# above (those are all about the PRECEDING ROOT's own attributes; this
# is about the preceding ENDING). Corresponds to Graph.kt's "Noun"
# state (already case-marked) taking a further nv suffix -- the real
# "iglu-mi-u-juq" pattern (noun+case+"to be X"). Endings_noun.csv's own
# "case" column (dat/abl/acc/gen/loc/nom/sim/via, 39 rows each) is the
# source of truth for which endings match; wired from gen_endings(),
# not gen_suffixes() -- see CASE_LEXICON's own use there.
CASE_LEXICON = {
    "dat": "AfterDatCaseEnding",
    "abl": "AfterAblCaseEnding",
    "loc": "AfterLocCaseEnding",
    "sim": "AfterSimCaseEnding",
    "via": "AfterViaCaseEnding",
}


def cas_triggers(row: dict):
    """Like category_trigger, but condPrec can be an OR of several case
    values ("cas:X" or "cas:X cas:Y ...", e.g. it/1nv's own "cas:loc
    cas:sim") -- returns a LIST of matching lexicon names (usually 1,
    sometimes more), or [] if condPrec isn't shaped "cas:X" at all (or
    references a case not in CASE_LEXICON, e.g. "nom"/"gen"/"acc" never
    appear on any actual condPrec in practice -- confirmed by checking
    the CSV data before writing this, not guessed)."""
    value = (row.get("condPrec") or "").strip()
    if not value.startswith("cas:"):
        return []
    lexicons = []
    for token in value.split(" "):
        if not token.startswith("cas:"):
            return []
        lex = CASE_LEXICON.get(token[len("cas:"):])
        if lex is None:
            return []
        if lex not in lexicons:
            lexicons.append(lex)
    return lexicons



# function -> which generated LEXICON block a suffix's own entries live in.
# Kept SEPARATE per function code (not grouped by output category) so each
# can be wired into the hub matching its INPUT category: "vn" (verb->noun,
# e.g. qati/1vn) must be reached from a VERB-shaped stem (VerbContinuations)
# -- same as the hand-authored VnSuffixes block already correctly is --
# while "nn" (noun->noun) is reached from a NOUN-shaped stem
# (NounContinuations); symmetric split for "nv" (noun->verb, from
# NounContinuations) vs "vv" (verb->verb, from VerbContinuations). An
# earlier version of this dict grouped by OUTPUT category instead (vn+nn
# both under one "NounProducingSuffixesGenerated" block, wired only from
# NounContinuations) -- that happened to be correct for nn/vv (input and
# output category coincide) but silently made every vn/nv suffix
# unreachable from its actual input category, caught via the
# graph-reachability diagnostic on "katujjiqatigiit" (katujji/1v -> qati/
# 1vn, a verb root needing a vn-function suffix that only NounContinuations
# could reach).
FUNCTION_LEXICON = {
    "vn": "VnSuffixesGenerated",
    "nn": "NnSuffixesGenerated",
    "nv": "NvSuffixesGenerated",
    "vv": "VvSuffixesGenerated",
    "q": "QParticlesGenerated",
}


def gen_suffixes():
    all_lexicons = (
        set(FUNCTION_LEXICON.values())
        | {v + SPECULATIVE_LEXICON_SUFFIX for v in FUNCTION_LEXICON.values()}
        | set(TRIGGER_LEXICON.values())
        | set(ANTIPASSIVE_LEXICON.values())
        | set(INTRANSINFIX_LEXICON.values())
        | set(TRANSINFIX_LEXICON.values())
        | set(NATURE_LEXICON.values())
        | set(CASE_LEXICON.values())
        | set(TYPE_LEXICON.values())
        | set(PL_LEXICON.values())
        | set(ID_ONLY_LEXICON.values())
        | set(MODE_LEXICON.values())
        | set(NOUN_NUMBER_LEXICON.values())
    )
    # sorted(): all_lexicons is a set, so iterating it raw makes the
    # order of the LEXICON blocks in the output file depend on
    # PYTHONHASHSEED -- i.e. a different, meaningless reordering every
    # run. Sorting makes the generated file byte-reproducible (and its
    # git diffs readable, and hfst-lookup's enumeration order stable).
    entries_by_lexicon = {lex: [] for lex in sorted(all_lexicons)}
    tags_used = set()
    seen = set()
    skipped_condition = 0
    rows_with_any_candidate = 0
    trigger_rows = 0
    speculative_rows = []

    for fn in ("Suffixes.csv", "Suffixes_additional.csv"):
        with (DATA_DIR / fn).open(encoding="utf-8") as f:
            for row in csv.DictReader(f):
                morpheme = row["morpheme"]
                nb = row["nb"]
                function = row["function"]
                if not morpheme or not nb or function not in FUNCTION_LEXICON:
                    continue
                if "&" in morpheme:
                    continue
                trigger = single_id_trigger(row, "condPrec", "condPrecTrans", "condOnNext")
                if trigger is None:
                    trigger = category_trigger(row, "antipassive", ANTIPASSIVE_LEXICON)
                if trigger is None:
                    trigger = category_trigger(row, "intransinfix", INTRANSINFIX_LEXICON)
                if trigger is None:
                    trigger = category_trigger(row, "transinfix", TRANSINFIX_LEXICON)
                if trigger is None:
                    trigger = category_trigger(row, "nature", NATURE_LEXICON)
                if trigger is None:
                    trigger = category_trigger(row, "type", TYPE_LEXICON)
                if trigger is None:
                    trigger = category_trigger(row, "pl", PL_LEXICON)
                cas_lexicons = cas_triggers(row) if trigger is None else []
                speculative_desc = None
                if trigger is None and not cas_lexicons:
                    status, desc = condition_status(row, function, "condPrec", "condPrecTrans", "condOnNext")
                    if status == "speculative":
                        speculative_desc = desc
                    # "ok" and "redundant" both fall through to normal wiring.
                tag = f"+{nb}{function}"
                if function == "q":
                    # Enclitic particles can stack ("taimaliqai" = li/1q
                    # then qai/1q) -- QParticles as a second continuation
                    # alongside "#" lets a further particle follow, same
                    # treatment as the hand-authored ones in lexicon.lexc.
                    continuations = ["#", "QParticles"]
                else:
                    continuations = ["#", FUNCTION_CONTINUATION[function]]
                # A SUFFIX (not just a root) can also grant an antipassive-
                # category continuation: e.g. tuq/1vv's own "antipassive"
                # column lists "i/1vv" (confirmed: gold words
                # "iqqaqtuivilirijikkut"/"iqqaqtuivilirijikkunnut" need
                # tuq immediately followed by i/1vv). Measured: 41 rows
                # across Suffixes.csv/Suffixes_additional.csv have a
                # non-empty antipassive column of their own -- ANY
                # function, not just vv, since e.g. "aq/2nv" (a noun-to-
                # verb suffix) ALSO lists "si/1vv ri/1vv". Same mechanism
                # as generate_roots.py's own root-level handling
                # (ANTIPASSIVE_LEXICON), just applied to the suffix's OWN
                # outgoing continuation instead of a root's.
                for ap_value in (row.get("antipassive") or "").split(" "):
                    ap_lexicon = ANTIPASSIVE_LEXICON.get(ap_value)
                    if ap_lexicon and ap_lexicon not in continuations:
                        continuations.append(ap_lexicon)
                # Same idea for PL_LEXICON: ksaq/1nn's own "plural"
                # column is "t", so it grants AfterPlTRoot alongside its
                # normal continuation.
                pl_lexicon = PL_LEXICON.get((row.get("plural") or "").strip())
                if pl_lexicon and pl_lexicon not in continuations:
                    continuations.append(pl_lexicon)
                # condOnNext REPLACES continuations entirely (not
                # additive): something specific MUST follow, so "#" and
                # the normal unrestricted hub are both wrong once this
                # applies. See condonnext_lexicons()'s own docstring.
                restricted = condonnext_lexicons(row, function)
                if restricted:
                    continuations = restricted
                # A SUFFIX cited by ANOTHER suffix's id-shaped condOnNext
                # (qaq/1nv, lik/1nn, junniiq/1vv, nngit/1vv) also needs a
                # COPY of its own entries placed into that narrow
                # ID_ONLY_LEXICON CONTAINER (using its own normal
                # continuations, not as an extra continuation of its
                # own) -- so the condOnNext-restricted suffix that leads
                # into this container reaches exactly this candidate,
                # additively alongside this row's normal reachability.
                id_only_lexicon = ID_ONLY_LEXICON.get(f"{morpheme}/{nb}{function}")
                if trigger:
                    lexicons = [trigger]
                    trigger_rows += 1
                elif cas_lexicons:
                    lexicons = cas_lexicons
                    trigger_rows += 1
                elif speculative_desc:
                    lexicons = [FUNCTION_LEXICON[function] + SPECULATIVE_LEXICON_SUFFIX]
                else:
                    lexicons = [FUNCTION_LEXICON[function]]

                any_candidate = False
                for context in ("V", "t", "k", "q"):
                    for lower in candidates_for_context(row, context):
                        any_candidate = True
                        for cont in continuations:
                            entry = f"++{morpheme}{tag}:{lower} {cont} ;"
                            if entry in seen:
                                continue
                            seen.add(entry)
                            for lexicon in lexicons:
                                entries_by_lexicon[lexicon].append(entry)
                            if id_only_lexicon:
                                entries_by_lexicon[id_only_lexicon].append(entry)
                            tags_used.add(tag)
                if any_candidate:
                    rows_with_any_candidate += 1
                    if speculative_desc:
                        speculative_rows.append((morpheme, tag, speculative_desc))
                elif speculative_desc:
                    skipped_condition += 1  # speculative but produced nothing (rare)

    lines = [
        "! Generated by tools/fst/generate_affixes.py -- DO NOT HAND-EDIT.\n"
        "! Rerun that script to regenerate after the source CSVs change.\n"
        "! See its module docstring for exactly which rows/action codes are\n"
        "! included/excluded. Reached from lexicon.lexc's NounContinuations/\n"
        "! VerbContinuations/QParticles -- see those LEXICON blocks.\n"
        "!\n"
        "! *Speculative* blocks (see SPECULATIVE_LEXICON_SUFFIX in this\n"
        "! script): entries wired WITHOUT respecting their own CSV row's\n"
        "! condition column (case/mode/number/nature/transitivity/\n"
        "! antipassive/an id: trigger this project has no dedicated branch\n"
        "! for) -- there is no gold-standard word left to verify these\n"
        "! against (the --fair gold standard was fully resolved before this\n"
        "! pass), so treat every entry in a *Speculative* block as an\n"
        "! UNVERIFIED GUESS, not a confirmed fact, unlike every other entry\n"
        "! in this whole lexicon. Added per Alain's explicit direction\n"
        "! (2026-08-21-ish session): the worst case is a missing candidate\n"
        "! for some word outside the current gold sample (same as before\n"
        "! this pass), not a corruption of an existing correct reading --\n"
        "! confirmed zero regressions on the full gold standard before\n"
        "! committing. If a real problem ever traces back to one of these\n"
        "! specific entries, that's the signal to fix or remove it, not\n"
        "! evidence the whole approach was wrong.\n",
        "Multichar_Symbols " + " ".join(sorted(tags_used)),
    ]
    # A lexicon with zero entries is skipped entirely, not written as an
    # empty block: the same TRIGGER_LEXICON names are declared as
    # possible destinations in BOTH gen_suffixes and gen_endings (a
    # trigger could in principle be hit from either CSV family), but
    # each individual trigger is in practice only ever populated by ONE
    # of the two generators -- writing an empty "LEXICON X" block for it
    # in the OTHER generator's output file would make hfst-lexc see that
    # same LEXICON name defined in two files ("Sublexicon defined more
    # than once", the exact error generate_roots.py's own header already
    # documents hitting once).
    total = 0
    for lexicon, entries in entries_by_lexicon.items():
        if not entries:
            continue
        lines.append(f"\nLEXICON {lexicon}")
        lines.extend(entries)
        total += len(entries)
    SUFFIXES_OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"suffixes: {total} entries ({len(tags_used)} tags) from "
          f"{rows_with_any_candidate} rows with >=1 candidate "
          f"({trigger_rows} via a single-id trigger, "
          f"{len(speculative_rows)} SPECULATIVE -- condition ignored); "
          f"{skipped_condition} rows skipped for a non-empty condition column")
    for lexicon, entries in entries_by_lexicon.items():
        print(f"  {lexicon}: {len(entries)}")
    for morpheme, tag, desc in speculative_rows:
        print(f"    SPECULATIVE: {morpheme}{tag}  ({desc})")


def endings_noun_tag(row: dict) -> str:
    tag = f"tn-{row['case']}-{row['number']}"
    if row.get("perPoss"):
        tag += f"-{row['perPoss']}{row['numbPoss']}"
    return tag


def endings_verb_tag(row: dict) -> str:
    tag = f"tv-{row['mode']}-{row['perSubject']}{row['numbSubject']}"
    if row.get("perObject"):
        tag += f"-{row['perObject']}{row['numbObject']}"
    return tag


def endings_verb_participle_tag(row: dict) -> str:
    tag = f"tv-{row['mode']}"
    if row.get("perSubject"):
        tag += f"-{row['perSubject']}{row['numbSubject']}"
    if row.get("perObject"):
        tag += f"-{row['perObject']}{row['numbObject']}"
    if row.get("tense"):
        tag += f"-{row['tense']}"
    return tag


def gen_endings():
    # Kept in TWO separate LEXICON blocks (not one mixed block) for the
    # same reachability reason as gen_suffixes: tn-* endings must only be
    # reachable from a noun-shaped stem, tv-* endings only from a
    # verb-shaped stem. Endings_noun.csv always produces tn-* tags and
    # Endings_verb.csv/Endings_verb_participle.csv always produce tv-*
    # tags, so routing by source file is exact, no need to inspect the
    # tag string itself.
    entries_by_lexicon = {
        "TnEndingsGenerated": [],
        "TvEndingsGenerated": [],
        "TnEndingsGenerated" + SPECULATIVE_LEXICON_SUFFIX: [],
        "TvEndingsGenerated" + SPECULATIVE_LEXICON_SUFFIX: [],
    }
    for lex in sorted(
        set(TRIGGER_LEXICON.values())
        | set(PL_LEXICON.values())
        | set(MODE_LEXICON.values())
        | set(NOUN_NUMBER_LEXICON.values())
    ):  # sorted(): see the matching note in gen_suffixes() -- keeps the
        # LEXICON-block order (and so the output file) reproducible.
        entries_by_lexicon.setdefault(lex, [])
    tags_used = set()
    seen = set()
    skipped_condition = 0
    rows_with_any_candidate = 0
    trigger_rows = 0
    speculative_rows = []

    specs = [
        ("Endings_noun.csv", endings_noun_tag, ("condPrec",), "TnEndingsGenerated"),
        ("Endings_verb.csv", endings_verb_tag, ("condPrecSpecific",), "TvEndingsGenerated"),
        ("Endings_verb_participle.csv", endings_verb_participle_tag, ("condPrecSpecific",), "TvEndingsGenerated"),
    ]
    for fn, tag_fn, cond_cols, default_lexicon in specs:
        with (DATA_DIR / fn).open(encoding="utf-8") as f:
            for row in csv.DictReader(f):
                morpheme = row["morpheme"]
                if not morpheme or "&" in morpheme:
                    continue
                trigger = single_id_trigger(row, *cond_cols)
                if trigger is None:
                    # "t"/tn-nom(-p)'s own condPrec is "pl:t" -- the only
                    # non-id-shaped condPrec this project's endings data
                    # has (see PL_LEXICON's own comment above).
                    for cond_col in cond_cols:
                        trigger = category_trigger({"condPrec": row.get(cond_col, "")}, "pl", PL_LEXICON)
                        if trigger:
                            break
                speculative_desc = None
                if trigger is None:
                    # Endings have no "function" column of their own to
                    # check redundancy against -- their routing is
                    # already fixed by which CSV file they came from
                    # (tn-* only from Endings_noun.csv, tv-* only from
                    # the two verb files), and no "type:X function:Y"
                    # shaped condition was ever observed here in
                    # practice, so every non-empty condition on an
                    # ending is treated as speculative.
                    status, desc = condition_status(row, None, *cond_cols)
                    if status == "speculative":
                        speculative_desc = desc
                if trigger:
                    lexicon = trigger
                    trigger_rows += 1
                elif speculative_desc:
                    lexicon = default_lexicon + SPECULATIVE_LEXICON_SUFFIX
                else:
                    lexicon = default_lexicon
                tag = tag_fn(row)

                # For noun endings specifically: a further nv-function
                # suffix conditioned on "cas:X" (see CASE_LEXICON above)
                # may follow ONLY a case ending matching its own X --
                # e.g. aq/1nv's "cas:dat" needs a dat-case ending here as
                # its ONLY path in. Additive alongside "#" (this ending
                # is still also a complete word on its own), not a
                # replacement.
                case_lexicon = (
                    CASE_LEXICON.get(row.get("case", "").strip())
                    if fn == "Endings_noun.csv" else None
                )
                end_continuations = ["#"] + ([case_lexicon] if case_lexicon else [])

                # For condOnNext (a suffix requiring THIS specific ending
                # immediately after itself, e.g. gi/2vv's own
                # "mode:int mode:dec"): give every verb ending an EXTRA
                # placement (not a continuation -- a second CONTAINER
                # holding a copy of its own entries) in a lexicon keyed
                # by its own mode, and every noun ending one keyed by its
                # own number -- so a condOnNext-restricted suffix can
                # reach ONLY matching endings by routing into that
                # narrow lexicon instead of the general one. See
                # condonnext_lexicons()'s own docstring for which
                # condOnNext shapes this covers.
                extra_lexicons = []
                if fn in ("Endings_verb.csv", "Endings_verb_participle.csv"):
                    mode_lexicon = MODE_LEXICON.get((row.get("mode") or "").strip())
                    if mode_lexicon:
                        extra_lexicons.append(mode_lexicon)
                elif fn == "Endings_noun.csv":
                    num_lexicon = NOUN_NUMBER_LEXICON.get((row.get("number") or "").strip())
                    if num_lexicon:
                        extra_lexicons.append(num_lexicon)

                any_candidate = False
                for context in ("V", "t", "k", "q"):
                    for lower in candidates_for_context(row, context):
                        any_candidate = True
                        for extra in extra_lexicons:
                            extra_entry = f"++{morpheme}+{tag}:{lower} # ;"
                            entries_by_lexicon[extra].append(extra_entry)
                        for cont in end_continuations:
                            entry = f"++{morpheme}+{tag}:{lower} {cont} ;"
                            if entry in seen:
                                continue
                            seen.add(entry)
                            entries_by_lexicon[lexicon].append(entry)
                            tags_used.add(tag)
                if any_candidate:
                    rows_with_any_candidate += 1
                    if speculative_desc:
                        speculative_rows.append((morpheme, tag, speculative_desc))
                elif speculative_desc:
                    skipped_condition += 1

    lines = [
        "! Generated by tools/fst/generate_affixes.py -- DO NOT HAND-EDIT.\n"
        "! Rerun that script to regenerate after the source CSVs change.\n"
        "! See its module docstring for exactly which rows/action codes are\n"
        "! included/excluded. Reached from lexicon.lexc's NounContinuations/\n"
        "! VerbContinuations -- see those LEXICON blocks.\n"
        "!\n"
        "! *Speculative* blocks: see the matching comment in\n"
        "! suffixes-generated.lexc's own header -- entries wired WITHOUT\n"
        "! respecting their own condition column, unverified against gold,\n"
        "! same rationale.\n",
        "Multichar_Symbols " + " ".join(sorted(tags_used)),
    ]
    # A lexicon with zero entries is skipped entirely, not written as an
    # empty block: the same TRIGGER_LEXICON names are declared as
    # possible destinations in BOTH gen_suffixes and gen_endings (a
    # trigger could in principle be hit from either CSV family), but
    # each individual trigger is in practice only ever populated by ONE
    # of the two generators -- writing an empty "LEXICON X" block for it
    # in the OTHER generator's output file would make hfst-lexc see that
    # same LEXICON name defined in two files ("Sublexicon defined more
    # than once", the exact error generate_roots.py's own header already
    # documents hitting once).
    total = 0
    for lexicon, entries in entries_by_lexicon.items():
        if not entries:
            continue
        lines.append(f"\nLEXICON {lexicon}")
        lines.extend(entries)
        total += len(entries)
    ENDINGS_OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"endings: {total} entries ({len(tags_used)} tags) from "
          f"{rows_with_any_candidate} rows with >=1 candidate "
          f"({trigger_rows} via a single-id trigger, "
          f"{len(speculative_rows)} SPECULATIVE -- condition ignored); "
          f"{skipped_condition} rows skipped for a non-empty condition column")
    for lexicon, entries in entries_by_lexicon.items():
        print(f"  {lexicon}: {len(entries)}")


if __name__ == "__main__":
    gen_suffixes()
    gen_endings()


# ═══════════════════════════════════════════════════════════════════════════
# PER-MORPHEME INVESTIGATION NOTES
#
# Migrated from tools/fst/lexicon.lexc, where each of these suffix/ending
# morphemes was originally hand-added and verified one at a time, before
# generate_affixes.py covered its Suffixes.csv / Endings_*.csv row in bulk.
# Kept here -- NOT in suffixes-generated.lexc / endings-generated.lexc,
# which are overwritten on every run -- as the record of which CSV
# candidates per context were used, how each was checked (often against the
# real Java analyzer's own output, or hfst-lookup directly), and which
# alternatives / dead-ends were considered. Grep by morpheme id.
#
# These notes describe both this generator's output AND the phonology rules
# in phonology.xfscript that the entries depend on (marker symbols such as
# SUPPR / NASAL / DECAP, rule ordering, why a marker was or wasn't reused).
# Where a note says something is "deferred" / "not attempted" / "not
# implemented", that is still the state unless a later note supersedes it.
#

#
# ── ngita+tn-gen-p-4s ──
# "ngita"/tn-gen-p-4s: clean single V-context candidate per
# Endings_noun.csv (Neutral, bare "ngita"). "gavamakkungita" (kkut/1nn,
# t-final) needed SUPPR rather than the general t->n rule: t->n would
# give "kkunngita" (double n, since ngita's own leading "ng" already
# supplies one) instead of gold's single-n "kkungita" -- SUPPR runs
# before the general t->n rule in the cascade (phonology.xfscript), so
# deleting kkut's own t outright avoids the conflict.
#
# ── ni+tn-loc-p ──
# "ni"/tn-loc-p: "amisuni" = {amisu:amisu/1n}{ni:ni/tn-loc-p}. V-only
# (V-form=ni/Neutral); t/k/q all ambiguous or id-conditional, deferred.
#
# ── titut+tn-sim-p ──
# "titut"/tn-sim-p is a K-CONTEXT (not q) single-candidate Neutral row --
# same literal "titut" text as V-context, so needs no separate marker,
# one plain entry covers both. Target word is "inuttitut" (root "inuk",
# k-final), NOT the more obvious "inuktitut": the general k->t rule below
# (for "quviattunga") correctly assimilates "inuk"+"titut" -> "inuttitut"
# -- confirmed by testing the generator direction directly. "inuktitut"
# (no assimilation) is gold-attested too but almost certainly the
# lexicalized proper-noun spelling of the language name itself, not a
# compositional form; left unimplemented rather than fighting an
# already-correct general rule for one irregular spelling. Its own
# q-context is the same Lever A trigger-conditional shape, but NOT
# implemented: the one gold word that would exercise it
# ("qallunaatitut") has an unrelated, unexplained root-internal
# alternation (qaplunaaq -> qallunaa, same category as iglu->illu), so
# there's no clean example to verify against.
#
# "mit"/tn-abl-s was investigated with the same V+trigger+default shape
# as "mut" and initially left unimplemented: every gold word that would
# exercise its q-context chained through an already-unimplemented
# dependency (luaq/1vv) or an unrelated root alternation, and no
# V-context example existed either -- since implemented above once
# "kingulliqpaamit"/"sivulliqpaamit" (paaq/1nn) provided a clean example.
# "tut"/tn-sim-s remains unattested anywhere in the 985-word gold
# standard, not worth implementing blind.
#
# ── tigut+tn-via-p ──
# Three more V-context (or V+k/q) noun endings:
#   - "tigut"/tn-via-p ("via" case): "tusaajitigut" =
#     {tusaaji:tusaaji/1n}{tigut:tigut/tn-via-p}. V+k (both Neutral,
#     plain, no marker); t-context needs its own Insertion("i") marker
#     (not built, unused by this word since "tusaaji" is vowel-final);
#     q-context is id-conditional, deferred as usual.
#   - "nginnit"/tn-abl-p-4s: "asinginnit" =
#     {asi:asi/1n}{nginnit:nginnit/tn-abl-p-4s}. V+k+q (k/q both
#     Suppression, reuses SUPPR); t-context ambiguous, deferred.
#   - "nginni"/tn-loc-p-4s: "asinginni" =
#     {asi:asi/1n}{nginni:nginni/tn-loc-p-4s}. Same shape as "nginnit"
#     above (V+k+q via SUPPR, t deferred).
#
# ── ngat+tn-nom-s-4p ──
# "ngat"/tn-nom-s-4p: "ilangat" = {ila:ila/1n}{ngat:ngat/tn-nom-s-4p} --
# V+k+q (k/q both Suppression, reuses SUPPR); t-context ambiguous (2
# candidates), deferred.
#
# ── miik+1vn ──
# "miik"/1vn (exclamation "Oh, how...!"): Suffixes.csv lists three
# literal spellings (miik/mii/mi) crossed with Nasalization vs
# Suppression actions in q-context -- but gold shows neither action
# cleanly: "nakuqmi"/"nakuqmii" keep the preceding root's q UNCHANGED
# (matching neither Suppression-deletes-it nor Nasalization-converts-it),
# while "nakurmii"/"nakurmiik" (same root "nakuq") DO show Nasalization
# (q->r). Same "wire plain and NASAL-marked variants in parallel, let
# gold's own spelling pick the path" strategy as usiq/1vn's SUPPR/VOICE
# split below -- all 3 spellings wired both plain and NASAL-prefixed (6
# entries total). "qujannami"/"qujannamik" (a 4th spelling, "mik", plus
# "naq"/1vv's own "nna" spelling and a "quja" root) not attempted: more
# unexplained variation than 2 words can justify guessing a rule from.
#
# ── jjut+1vn ──
# "jjut"/1vn ("reason/cause/motive for doing s.t."): same "one who/reason
# for X" shape as "ji"/1vn just above, wired identically (bare "jjut"
# terminal, continuing "jjuti", same 4 continuations). "katimajjutiksaq"
# also needed a bare (non-SUPPR) terminal candidate for ksaq/1nn below --
# own trailing q kept, since gold wants "ksaq" not "ksa" when it's the
# FINAL morpheme. q-context ("rut"/"ruti", Fusion) not attempted: no gold
# word in this cluster needs it.
#
# ── gaq+1vn ──
# "gaq"/1vn: "makpigaq" = {makpi:makpiq/1v}{gaq:gaq/1vn} -- verb-to-noun
# derivational suffix ("forms a noun with an inherently passive
# meaning"). Same shape as juq/1vn and kkut/1nn: Suffixes.csv's row has
# one deterministic candidate per context (V-form/action Neutral,
# t/k/q-form Suppression, all literally "gaq" itself unchanged) --
# genuine rule, reuses the existing SUPPR marker exactly like
# "liri"/"qai"/"kkut" (delete the preceding stem's final q/k/t,
# "makpiq"->"makpi", then append "gaq"). Also continues into NnSuffixes
# ("maligaksait" needs "ksaq"/1nn to attach directly after "gaq"), into
# NounEndings directly ("maligarmut"/"maligarmi"/"maligarmik"/
# "maligarnit" all attach a tn-ending directly onto "gaq" with no
# intervening "ksaq", reaching the generic non-trigger Lever A branch
# there -- confirmed by the attested surface "gar", Nasalization, not
# "ga", Suppression, since "gaq"/1vn is not one of Lever A's trigger
# ids), and into NvSuffixes (so a noun-to-verb suffix like "liuq" can
# follow: "maligaliuqti" = mali(malik/1v)+ga(gaq/1vn)+liuq(liuq/1nv)+
# ti(ji/1vn)).
#
# ── ut+1vn ──
# Suffixes.csv's own V-form column lists "ut uti utik utaq" TWICE --
# once (Neutral) as this family's plain, unmarked candidates, once more
# (further down the same cell) with action "i(jj)" for the already-
# wired "jjut/jjuti/jjutik/jjutaq" family above. Only the "g"-inserted
# sibling ("gut/guti/gutik/gutaq") and the "jj"-geminated one got wired
# when this suffix was first built -- the genuinely bare, unmarked
# forms were missed entirely. "katimautitsa" needs "uti" bare (no "g"),
# confirmed via gold; not decomposable by the real Java analyzer either
# (confirmed via --pipeline).
#
# ── jaq+1vn ──
# "jaq"/1vn: Suffixes.csv actually has TWO "jaq/1vn"-tagged rows (nb=1
# "passive", nb=3 "similar to"), distinguished only by an id-conditional
# condPrec (nb=1: NOT preceded by u/1nv; nb=3: preceded by u/1nv) -- but
# unlike the "mut"/"nik" id-conditional cases (deferred because the
# SURFACE FORM itself differs by condition), here both rows produce the
# IDENTICAL surface rule (V-form "jaq"/Neutral, t/k/q-form "taq"/Neutral
# -- the existing j->t-after-t/k/q rule handles the consonant contexts
# already, no new marker needed), so the id-conditional distinction is
# purely semantic and doesn't affect which lexc entry to write. "jaq"
# also gained its own t/k/q-context literal spelling, "taq" -- Suffixes.csv
# gives this directly as its own candidate (not derived via the general
# j->t rule), needed for "pigiaqtitaq" family ("tit"/1vv ends in t, jaq
# attaches right after) -- and a NnSuffixes continuation
# ("qaujimajatuqanginnik" needs jaq -> tuqaq/1nn directly). "ilagijaujuq"
# = {ila:ila/1n}{gi:gi/1nv}{jaq:jaq/1vn}{u:u/1nv}{juq:juq/1vn} needs the
# bare terminal path too ("piqujaq"), not just mid-chain.
#
# ── it+3nv ──
# "it"/3nv ("to be such"): Suffixes.csv's own condPrec ("type:a") is a
# grammatical-feature condition the bulk generator can't model, but
# it's confirmed directly by gold: both target words' root is tagged
# "1a" (adjective), matching this row's own condition exactly.
# Q-context gives 2 candidates, both literal "it" with Suppression --
# no real ambiguity (both actions identical), resolves to a single
# "SUPPRit" (for the plain q-kept "qanuq" root spelling). A plain "it"
# (no marker) is ALSO needed since "qanuq"/1a already has its own
# q-dropped spelling variant "qanu" wired separately -- attaching to
# THAT spelling needs no further deletion. Previously investigated and
# confirmed low-value on its own (round-16-era: only 3 gold words are
# 3nv-tagged at all) but 2 of those 3 are exactly this suffix's own
# target words, so worth wiring now that they're the last remaining
# gap. Neither is decomposable by the real Java analyzer either
# (confirmed via --pipeline).
#
# ── giik+2nv ──
# "giik"/2nv: "ajjigiinngittunik" = {ajji:ajji/1n}{gii:giik/2nv}
# {nngit:nngit/1vv}{tu:juq/1vn}{nik:nik/tn-acc-p} -- V-context only
# (V-form=giik/Neutral). t-context is a genuine single candidate
# (Insertion "i") but unused here (root "ajji" is vowel-final);
# k/q-contexts use the unbuilt Fusion primitive, skipped.
#
# ── liaq+2nv ──
# "liaq"/2nv ("motion towards: to go to"): clean single V-context
# candidate per Suffixes.csv (Neutral, bare "liaq"); "aanniaviliaqtunut"
# attaches after vik/3vn (vowel-final surface).
#
# ── iq+1nv ──
# "iq"/1nv: clean single V-context candidate per Suffixes.csv (Neutral,
# bare "iq"); its own trailing q survives until something downstream
# needs it gone (si/1vv's own literal spellings and ut/1vn's SUPPR-
# prefixed "ruti" both already handle that, same established pattern as
# every other q-final suffix). t/k/q-context (Insertion "ng"/"a",
# Suppression) not attempted: unattested in this cluster.
#
# ── qaq+1nv ──
# "qaq"/1nv ("to have/possess"), needed by "jjut"/1vn's "pijjutiqaqtu*"
# cluster -- clean single V-context candidate (Neutral), reused unchanged
# for t/k/q since "jjuti" (what it always attaches to here) is
# vowel-final. Needs BOTH VnSuffixes (for "juq"/1vn to follow,
# "pijjutiqaqtuq"/"...tunik") and TvEndings directly ("pijjutiqaqtut"
# attaches "jut"/tv-ger-3p straight onto qaq, no juq/1vn in between). A
# second gold example, "akiqangittuq", is NOT reached: it produces
# "akiqaNNgittuq" (double n) from plain concatenation + SUPPR, not gold's
# "akiqaNgittuq" (single n) -- something reduces nngit's own doubled "nn"
# to a single "n" specifically after qaq's deletion, not explained by any
# mechanism built so far (same unexplained-surface-simplification
# category as "iglu"->"illu"). Not pursued further; revisit once the
# nn->n reduction is understood.
#
# ── gi+1nv ──
# "gi"/1nv ("have as; possess"): "isumagillugu" = {isuma:isuma/1n}
# {gi:gi/1nv}{llugu:lugu/tv-part-1s-3s-prespas}. V-context only
# (V-form=gi/Neutral); t-context is a genuine single candidate too
# (Insertion "i") but unused by this word (root "isuma" is vowel-final)
# and not implemented (would need its own marker, deferred); k/q-contexts
# use "Fusion", an unbuilt primitive, skipped. Also gained its own
# q-context literal spelling, "ri" (Suffixes.csv gives this directly as a
# Fusion candidate -- mechanically identical to SUPPR, deletes the
# preceding q) -- needed for "apiqqusirijara" (usiq's own bare "qusiq"
# candidate, own q kept, then gi's "ri" eats it) -- and reaches
# VnSuffixes as well as TvEndings, so it can be followed by a further
# vn-suffix like "jaq"/1vn ("ilagijaujuq" needs gi -> jaq/1vn), not just
# terminate in a verb ending. "ut"/1vn's "quti" and "usiq"/1vn's "qusiq"
# candidates above both needed a path into NvSuffixes so "gi" can follow
# them at all -- neither had ever reached a noun-to-verb suffix before.
#
# ── paaq+1nn ──
# "paaq"/1nn ("the most of all; big, very"), the suffix &iq's "lliq"
# family chains into -- clean single V-context candidate per its own CSV
# row.
#
# ── miuq+1nn ──
# "miuq"/1nn ("resident of a place name"): clean single V-context
# candidate per Suffixes.csv (Neutral, bare "miuq"). SUPPR reused so a
# following ending's own SUPPR marker (it/tn-nom-p, nut/tn-dat-p, both
# already SUPPR-equipped) eats miuq's own trailing q for free -- same
# established pattern as every other "own q survives until something
# downstream needs it gone" suffix in this file.
#
# ── limaaq+1nn ──
# "limaaq"/1nn ("inclusiveness: all of" -- a DIFFERENT homograph than
# "limaaq"/2vv "ceaselessly" in VvSuffixes below): a clean, single-
# candidate rule (V-form Neutral, t/k/q-form Suppression, same shape as
# juq/kkut/gaq/lik) once "nut" had both its default NASAL and SUPPR
# q-context branches (see NounEndings above) -- confirmed via
# "inulimaanut" = {inu:inuk/1n}{limaa:limaaq/1nn}{nut:nut/tn-dat-p} and
# "kikkulimaanut"/"kikkulimaat" (the latter also exercising "it"'s
# existing DECAP mechanism unchanged: limaaq's own trailing q is deleted,
# leaving "...limaa" -- two vowels -- so DECAP correctly strips "it"'s
# own leading i, giving "...limaat"). Two other candidate words
# ("kanatalimaami", needs root "kanata" but the only bulk-generated
# homograph is tagged 2n not gold's 1n -- a pre-existing, already-
# documented root-homograph mismatch; "nunavulimaami", root "nunavut"
# isn't bulk-generated at all -- it only exists in Locations.csv, a
# source file generate_roots.py doesn't cover) are left unimplemented,
# blocked by those unrelated gaps, not by limaaq itself.
#
# ── lik+1nn ──
# "lik"/1nn ("possession: 'one with'"): "nanulik" = {nanu:nanuq/1n}
# {lik:lik/1nn} -- noun-to-noun. Same shape as juq/kkut/gaq: one
# deterministic candidate per context (V-form/action Neutral, t/k/q-form
# Suppression, "lik" itself unchanged) -- genuine rule, reuses SUPPR
# unchanged. Also continues into NounEndings ("nunalittinni" =
# nuna+li(lik/1nn)+ttinni(ptingni/tn-loc-s-1d)), not just the terminal
# path (matching "nanulik" standalone).
#
# ── aluk+1nn ──
# "aluk"/1nn ("largeness/impressiveness"): Suffixes.csv gives TWO
# V-context candidates ("aluk"/"aaluk", each with a secondary "i(ra)"
# insertion action) -- but no gold-attested word needs the "aaluk"/
# "i(ra)" branch at all, so only the plain "aluk" Neutral candidate is
# implemented. Needs the SUPPR marker even though "aluk" itself is
# Neutral in V-context: "akuni" (i-final) is genuine V-context, but
# "uqsu*"'s actual root is "uqsuq" (q-FINAL -- the gold surface span
# "uqsu" is already q-deleted, easy to misread as vowel-final from the
# surface alone) -- aluk's own k/q-form action1 IS "s" (Suppression), the
# same SUPPR marker used everywhere else in this lexicon, harmless as a
# no-op when nothing q/k/t-final actually precedes it (confirmed safe for
# "akunialuk"). "uqsualuit"/"uqsualuup" need NO further new mechanism:
# "it"/tn-nom-p and "up"/tn-gen-s already carry their own SUPPR marker
# (deletes a preceding q/k/t), so aluk's own trailing "k" is deleted by
# the EXISTING machinery the moment it reaches NounEndings -- confirmed
# "aluit"/"aluup" fall out for free, same "bulk mechanism pays off beyond
# the tested set" shape as several other suffixes in this file.
# "uqsualummut" is what first exercised the k->m rule in
# phonology.xfscript: "mut"/tn-dat-s's plain-Neutral candidate alone
# would give literal "aluk"+"mut" = "alukmut", not gold's "alummut".
# k->m is NOT an aluk-specific patch -- it is a member of the general
# total-regressive-assimilation family (t->n / k->t / p->t / t->m / k->m
# / m->n / k->n), which is this project's modelling of Benoit's
# Action.Assimilation (Action.kt: the stem's final consonant becomes the
# following affix's initial consonant). aluk's own CSV row doesn't
# mention it because that row describes aluk's shape by what PRECEDES it,
# not what follows -- the assimilation is a property of the boundary, not
# of aluk. "uqsualummut" is just the gold word where a k-final stem first
# met an m-initial affix.
#
# ── tuqaq+1nn ──
# "tuqaq"/1nn: "gavamatuqakkut" = {gavama:gavama/1n}{tuqa:tuqaq/1nn}
# {kkut:kkut/1nn} -- V-context only (V-form=tuqaq/Neutral). t-context has
# a genuine single candidate too (Insertion "i") but isn't needed by this
# word (root "gavama" is vowel-final) and isn't implemented here -- would
# need its own marker (insert "i" before a t-final stem), deferred.
# k/q-contexts are ambiguous (2 unconditioned candidates each), skipped
# as usual. Also gained a NounEndings continuation and its own q-context
# Suppression candidate (Suffixes.csv's own q-form gives "tuqaq tuqaq"/
# "n s" -- a second, SUPPR-prefixed candidate) -- both needed for
# "qaujimajatuqanginnik" (jaq's own trailing q deleted by tuqaq's SUPPR,
# then tuqaq -> nginnik/tn-acc-p-4s).
#
# ── tuinnaq+2nn ──
# "tuinnaq"/2nn ("only/just/merely"): "kikkutuinnait" =
# {kikku:kikkut/1p}{tuinna:tuinnaq/2nn}{it:it/tn-nom-p} -- full V/t/k/q,
# same shape as juq/kkut/gaq/lik/raq (single candidate, Neutral/
# Suppression), reuses SUPPR. Continues into NounEndings (the
# "it"/tn-nom-p above) rather than back into NnSuffixes: checked that
# this doesn't hit "it"'s deferred Selfdecapitation gap (unlike the
# "limaaq"/1nn case above) -- deleting tuinnaq's own trailing q leaves
# "tuinna", ending in a single vowel after a consonant, not two vowels,
# so decap never fires here.
#
# ── allak+1vv ──
# "allak"/1vv ("ease, simpleness of action: 'easily', 'just'"):
# Suffixes.csv gives 2 literal spellings in EVERY context ("allak"/"ala",
# same free-variation shape as usiq/ut/miik) -- only V-context wired so
# far since "uqaalautaa" (root "uqaq", V-context) is the only gold
# example, confirmed via the real Java analyzer's own output before
# wiring. The CSV's own action2 ("i(ra)", an insertion this project's
# generator doesn't model) is left unimplemented, same as juq/1vn's own
# unexplained action2 codes elsewhere in this file.
#
# ── allak+1vv ──
# t/k/q-context (Suppression, deletes the preceding stem's final q/k/t):
# same two literal spellings again, needed since "uqaalautaa"'s root
# "uqaq" is itself q-final (own q deleted by SUPPR before "ala"
# attaches).
#
# ── li+2vv ──
# "li"/2vv ("to make that s.t. or s.o. ..."): Suffixes.csv gives a clean
# single-candidate row per context (V "li" Neutral, t/k/q "li"
# Suppression), but its condPrec ("!cp(id:li/4vv)", a negated
# mutual-exclusion note against the DIFFERENT li/4vv homograph, not a
# blocking condition on li/2vv itself) isn't a bare "id:X" trigger the
# bulk generator understands -- "atuliqujaujuq" needs it (root "atuq"
# q-context: SUPPR deletes the q).
#
# ── luaq+1vv ──
# "luaq"/1vv ("excessive action: too much/quite"): clean single
# V-context candidate per Suffixes.csv (Neutral, bare "luaq"; the row's
# second candidate, "lluaq", is unattested here). Reaches VnSuffixes for
# juq/1vn to follow ("piluaqtumi"/"...mik"/"...mit"). The last of these
# ("piluaqtumit") found one more gap: mit/tn-abl-s had never been added
# to NounEndingsAfterQTriggerSuffix, unlike its "mi"/"mik" siblings --
# fixed there.
#
# ── vallia+1vv ──
# "vallia"/1vv ("progression, gradually") -- clean single V-context
# candidate per Suffixes.csv (Neutral, bare "vallia"); needed for
# "pivalliatittinirmut" (si/1vv's t-context "ti" family, via tit/1vv).
# t/k/q-form ("pallia", v->p, own literal spelling) not attempted:
# unattested in this corpus.
#
# ── tuinnaq+1vv ──
# "tuinnaq"/1vv ("merely/only/simply"): clean single V-context candidate
# per Suffixes.csv (Neutral, bare "tuinnaq"); every gold example attaches
# after an already-vowel-final vv-suffix (ma/juma/juma-via-ruma). A
# DIFFERENT homograph from the already-implemented "tuinnaq"/2nn noun
# suffix in NnSuffixes above -- confirmed genuinely separate tags before
# wiring, not a duplicate.
#
# ── qu+2vv ──
# "qu"/2vv ("to desire, to wish something to be done"): V-form Neutral,
# t/k/q-form Suppression -- same clean single-candidate shape as
# juq/kkut/gaq/lik/limaaq. "piqujaq" = {pi:pi/1v}{qu:qu/2vv}{jaq:jaq/1vn}
# -- V-context (root "pi" is vowel-final), continues into VnSuffixes
# since jaq/1vn follows it.
#
# ── a+1vv ──
# "a"/1vv ("action done repeatedly/on several objects"): same
# "bare-terminal keeps q, continuing form drops it" shape as ut/1vn's own
# ut/uti split above: "atuagaq" (bare terminal) keeps "aq";
# "atuagait"/"atuagarmik"/"atuagarnik" (continuing into gaq/1vn) use the
# reduced "a". Root "atuq" is q-final (surfaces "atu"), so both
# candidates need SUPPR to delete it -- same trap as several other
# q-final-root suffixes in this file.
#
# ── juma+1vv ──
# "juma"/1vv ("desire"): V-form is a genuine 2-candidate free-variation
# ambiguity ("juma"/"guma", both Neutral -- gold attests BOTH spellings
# for the identical decomposition, e.g. "qaujijumajunga" vs
# "qaujigumajunga", so implementing V-context would mean guessing, same
# policy as "ma"/1vv below); t-context is likewise 2-candidate, also
# skipped. ONLY the q-context is implemented: single candidate, Fusion
# (== SUPPR reused, literal text already substituted to "ruma" in the
# CSV, same shape as "gama"/"gapta"/"gavit"'s own Fusion rows in
# TvEndings below) -- confirmed via "uqarumajunga"/"uqarumavunga" (root
# "uqaq", q-final). Also reaches VvSuffixes directly (not just
# TvEndings): "tusarumatuinnaqtunga" = tusaq+ruma(juma q-context)+
# tuinnaq(1vv)+tunga.
#
# ── lauq+1vv ──
# "lauq"/1vv and "qqau"/1vv ("uqalaurmat"/"uqaqqaummat"): Suffixes.csv
# gives both a clean single-candidate shape, same as juma's q-context:
# V-form Neutral, t/k/q-form Suppression (deletes the preceding stem's
# own final q/k/t) -- only the q-context is implemented here since that's
# what both gold words need (root "uqaq", q-final). "lauq" has a SECOND
# row in Suffixes.csv (a different function, "priority of the command")
# gated by "condOnNext=mode:imp" -- a real disambiguator, not a guess:
# these target words aren't imperative, so the first row (general
# perceived past) is the unambiguous match. Both suffixes are themselves
# q-final, so "mat"/tv-caus-4s's own NASAL marker (see TvEndings below)
# turns THEIR trailing q into "r" when it directly follows --
# "uqalaurmat" is "uqa"+SUPPR(deletes uqaq's own q)+"lauq"+NASAL(lauq's q
# -> r)+"mat".
#
# ── giaq+1vv ──
# "giaq"/1vv ("begin to", needed by "ut"/1vn's r-prefix family above):
# Suffixes.csv gives a clean single V-context candidate (Neutral, bare
# "giaq") -- confirmed via "ilagiarut"/"qaujigiarutit", where giaq's own
# trailing q is deleted not by anything special here but by "ut"/1vn's
# OWN SUPPR marker on its r-prefixed candidate -- the same "the FOLLOWING
# suffix's SUPPR eats MY trailing q" pattern established for gaq/usiq
# above, not a new mechanism. Also reaches VvSuffixes directly (not just
# VnSuffixes): "pigiaqtitaq" chains vv-suffix into vv-suffix directly
# (giaq -> tit).
#
# ── qqau+1vv ──
# "qqau"/1vv: see "lauq"/1vv's comment above (shared investigation).
#
# ── tuq+1vv ──
# "tuq"/1vv ("prolonged action: for a long time"): "akiraqtuqtut" =
# {akiraq:akiraq/1v}{tuq:tuq/1vv}{tut:jut/tv-ger-3p} -- V/k/q-context all
# Neutral (single candidate, "tuq" itself unchanged, plain
# concatenation); only t-context is Suppression (single candidate too,
# but not needed by this word's q-final root, and would need its own
# marker scoped to t only -- the existing SUPPR marker deletes q/k/t
# uniformly, which would be WRONG here since this suffix's own k/q-context
# is Neutral, not Suppression -- deferred rather than reusing SUPPR
# incorrectly). Never continued into VvSuffixes at first -- only ever
# validated word-final-ish via TvEndings ("akiraqtuqtut"); fixed once
# "aviktursimajuni" needed tuq -> sima/1vv. Also gained a VnSuffixes
# continuation in pursuit of the "uuktutigilugu" family (uuk+tuq(1vv)+
# ti/uti(ut/1vn)+gi(1nv)+lugu) -- turned out NOT sufficient on its own:
# gold's span for ut/1vn here is "ti" (via {ti:ut/1vn}), a spelling
# this project's ut/1vn candidate set doesn't cover (not the r-prefix or
# q-prefix families implemented above -- possibly the CSV's unimplemented
# "guti" t-context candidate, itself contingent on tuq/1vv having its own
# t-context spelling change not yet investigated). Left unattempted
# rather than guessing a new ut/1vn candidate from a single word; the
# VnSuffixes continuation itself is harmless and kept since it's
# independently correct.
#
# ── sima+1vv ──
# "sima"/1vv: "titiraqsimajut" = {titi:titiq/1v}{raq:raq/1vv}
# {sima:sima/1vv}{jut:jut/tv-ger-3p}. V+k+q ALL Neutral (single
# candidate, plain "sima" unchanged in every context -- unlike most
# suffixes above, its q-context does NOT delete what precedes it,
# confirmed by rereading the CSV row carefully: q-action1="n", not "s").
# t-context is 2-way ambiguous ("sima sima"/"s n"), deferred. Never
# continued into VnSuffixes at first -- only ever validated via TvEndings
# ("titiraqsimajut"); the same word needs sima -> juq/1vn, fixed once
# that was noticed.
#
# ── ma+1vv ──
# "ma"/1vv ("to be in a state of") -- a much bigger cluster once chained:
# "kati"+"ma" = "to be meeting/assembled" backs an entire "katima*"
# family of gold words. Its two V-form candidates ("ma"/"uma") were
# initially treated as unresolvable free variation without checking
# whether they collide. Confirmed via gold they DON'T: "katimaji" (root
# "kati", vowel-final) uses bare "ma", but "aaqqiumainnarutinut" (root
# "aaqqik", ALSO vowel-final once its own k is handled) uses "uma" --
# same context, different roots, different literal spellings --
# textbook "wire both in parallel" case, same as ngani/ani and
# nganut/anut in NounEndings above. t/k/q-form (Suppression, both
# candidates) not attempted: no gold word in the reachable cluster needs
# it yet.
#
# ── raq+1vv ──
# "raq"/1vv ("prolonged/staged action"): same shape as juq/kkut/gaq/lik
# (Neutral/Suppression), reuses SUPPR. Shares its target example with
# "sima"/1vv above ("titiraqsimajut").
#
# ── limaaq+2vv ──
# "limaaq"/2vv ("ceaselessly" -- a DIFFERENT morpheme than
# "limaaq"/1nn "inclusiveness: all of" in NnSuffixes above, same spelling
# but a distinct homograph/function): "uqalimaarniq" =
# {uqa:uqaq/1v}{limaar:limaaq/2vv}{niq:niq/2vn} -- full V/t/k/q, same
# Neutral/Suppression shape, reuses SUPPR. The surface "limaar" (not
# "limaaq") comes entirely from "niq"'s own already-implemented
# Nasalization q-context (q->r), not from anything new here.
#
# ── nga+1vv ──
# "nga"/1vv: "turaangajuq" = {turaa:turaaq/1v}{nga:nga/1vv}
# {juq:juq/1vn} -- same shape as juq/kkut/gaq/raq (single candidate,
# Neutral/Suppression), reuses SUPPR unchanged.
#
# ── jara+tv-ger-1s-3s ──
# "jara"/tv-ger-1s-3s ("I...it", 1s subject/3s object gerundive):
# Endings_verb.csv's V-context is a single clean candidate (Neutral,
# bare "jara"); every gold example in this cluster attaches to a
# vowel-final stem. t/k/q-form ("tara", also Neutral -- own literal
# spelling, not the general j->t rule, though it'd give the same result)
# not attempted: unattested in this corpus.
#
# ── tillugit+tv-part-3p ──
# "tillugit"/tv-part-3p (participle, 3rd person plural object, no
# specific subject) -- clean single V-context candidate per
# Endings_verb_participle.csv (Neutral, bare "tillugit"), needed by
# "katimatuinnaqtillugit" (tuinnaq/1vv -> tillugit directly).
#
# ── junga+tv-ger-1s ──
# "junga"/tv-ger-1s: "quviattunga" = {quviat:quviak/1v}
# {tunga:junga/tv-ger-1s} -- identical V/t/k/q shape to "jut"/tv-ger-3p
# above: all single-candidate Neutral, reuses the existing
# j->t-after-t/k/q rule unchanged, no new mechanism at all.
#
# ── lugu+tv-part-1s-3s-prespas ──
# "lugu"/tv-part-1s-3s-prespas and "lugit"/tv-part-1s-3p-prespas:
# "isumagillugu" = {isuma:isuma/1n}{gi:gi/1nv}{llugu:lugu/tv-part-1s-3s-
# prespas} and "katillugit" = {kati:kati/1v}{llugit:lugit/tv-part-1s-3p-
# prespas}. V-context only (both dialect variants in
# Endings_verb_participle.csv agree: V-form is the bare morpheme text with
# action "i(l)" -- Insertion of "l" -- UNCONDITIONALLY, not gated by
# anything the way VVNG's insertion is). Since this insertion never varies
# by what precedes in the V-context, it's baked directly into the literal
# lower-side text ("llugu"/"llugit", canonical "lugu"/"lugit") rather than
# implemented as a rule -- no marker needed. t/k/q-contexts differ by
# dialect (North Baffin vs Cumberland Peninsula use different forms) and
# aren't attempted.
#
# ── lunga+tv-part-1s-prespas ──
# "lunga"/tv-part-1s-prespas (intransitive, no object): Endings_verb_
# participle.csv's V-form action is the same unmarked "i(l)" insertion
# as lugu/lugit's own V-context above, baked directly into the literal.
# Needed for "ministaullunga"/"qaujimallunga" (both vowel-final stems).
#
# ── luta+tv-part-1p-prespas ──
# "luta"/tv-part-1p-prespas (intransitive, no object): same shape as
# "lunga" directly above. Needed for "gavamaulluta".
#
# ── jugut+tv-ger-1p / jutit+tv-ger-2s / gama+tv-caus-1s ──
# Four more tv-endings:
#   - "jugut"/tv-ger-1p ("angiqtugut"): same shape as jut/junga/jutit
#     (all single-candidate Neutral, V-form "jugut" literal, q-context
#     "tugut" falls out of the existing j->t rule for free -- no new
#     marker).
#   - "jutit"/tv-ger-2s ("qujannamiingujutit"): same shape again, reached
#     via "u"/1nv's own TvEndings continuation above.
#   - "gama"/tv-caus-1s, "gapta"/tv-caus-1p, "gavit"/tv-caus-2s
#     ("qaujimagama"/"qaujimagatta"/"qaujimagavit"): Endings_verb.csv's
#     "caus" rows are single-candidate Neutral in the V-context (root
#     "qaujima" is vowel-final, so only V-context is exercised);
#     t/k/q-contexts differ per ending (some Assimilation, one
#     "rama"/Fusion -- an unbuilt mechanism) and aren't attempted here,
#     matching this project's minimal-scope policy of only wiring the
#     context an actual gold word needs.
#
# ── git+tv-imp-2s ──
# "git"/tv-imp-2s: see "suk"/1vv's comment in VvSuffixes above for the
# full imperative-mood investigation. k-context, Fusion == SUPPR reused,
# deletes suk's own trailing k: "tunngasu"+"git" = "tunngasugit".
# ═
# ════════════════════════════════════════════════════════════════════════
