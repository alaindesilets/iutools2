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
            # before generalizing here -- deliberately NOT extending this
            # same treatment to the OTHER "i(X)" insertion codes (i(ng),
            # i(a), i(i)): i(ng) is already known (round 14) to sometimes
            # need conditional gating instead of unconditional insertion,
            # and i(a)/i(i) have zero gold-verified examples in this
            # project at all -- guessing at those risks the same silent
            # corpus-wide corruption class already seen twice (the
            # TNNOMD and j->t bugs), not just wasted effort.
            yield f"l{lit}"
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
    )
    entries_by_lexicon = {lex: [] for lex in all_lexicons}
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
    for lex in set(TRIGGER_LEXICON.values()):
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

                any_candidate = False
                for context in ("V", "t", "k", "q"):
                    for lower in candidates_for_context(row, context):
                        any_candidate = True
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
