# FST prototype (`data/grammar/fst/`)

An experimental **finite-state** re-implementation of the morphological
analyzer, built with [HFST](https://hfst.github.io/). It is a research
prototype, developed and tracked separately from the production Kotlin
analyzer in `:core` (`MorphologicalAnalyzer_R2L.decomposeWord()`) — the two
share no code and will not fully agree until the lexicon here covers the
same data. See [`doc/dev/plans/fst-analyzer-plan.md`](../../../doc/dev/plans/fst-analyzer-plan.md)
for the design, milestones, and current status.

Notes:

- **Roman orthography only.** `hfst-lookup` takes Roman input; unlike the
  real analyzer it does not transcode syllabics internally.
- The compiled transducers (`*.hfst`, `*.hfstol`) are **build output** and
  are git-ignored. The `*-generated.lexc` files (generated from the CSV
  linguistic data) *are* committed.

When the FST becomes an official part of the project, the user-facing notes
below should move to the root `README.md`.

## Prerequisites

### HFST command-line tools

The scripts here shell out to `hfst-lookup` (and the build uses
`hfst-lexc`, `hfst-xfst`, `hfst-invert`, `hfst-fst2fst`).

| Platform | Install |
|---|---|
| Debian / Ubuntu (incl. the dev container) | `sudo apt-get install -y hfst` |
| macOS (Homebrew) | `brew install ualbertaaltlab/hfst/hfst` |
| Any (conda) | `conda install -c conda-forge hfst` |

Or grab the statically-linked universal binaries from the
[HFST download page](https://hfst.github.io/downloads/index.html) and put
`hfst/bin/` on your `PATH`.

Check: `which hfst-lookup`.

### Python

Python 3.9+ (standard library only). Run every script **from this
directory** (`data/grammar/fst/`) — they import each other by module name and
resolve data paths relative to their own location.

## Building the transducer

A fresh checkout has the `*-generated.lexc` files but not the compiled
`lexicon-analyser.hfstol` that the scripts query. To build it:

```bash
cd data/grammar/fst

# 1. (only if the CSV linguistic data changed) regenerate the lexc sources
python3 generate_roots.py            # -> roots-generated.lexc
python3 generate_affixes.py          # -> suffixes-generated.lexc, endings-generated.lexc
python3 generate_demonstratives.py   # -> demonstratives-generated.lexc

# 2. combine the hand-written and generated lexicons into one .hfst
#    (xfst's own `read lexc` takes a single file; the standalone hfst-lexc
#    tool takes several)
hfst-lexc -o combined-lexicon.hfst \
    lexicon.lexc roots-generated.lexc suffixes-generated.lexc \
    endings-generated.lexc demonstratives-generated.lexc

# 3. compose with the phonology rules -> generator, then invert -> analyser
hfst-xfst -F phonology.xfscript                       # -> lexicon-generator.hfst
hfst-invert  -i lexicon-generator.hfst -o lexicon-analyser.hfst
hfst-fst2fst -w -i lexicon-analyser.hfst -o lexicon-analyser.hfstol
```

The `-w` on the last step (weighted optimized-lookup) is required — `-O`
silently flattens the strict/lenient weight distinction. Step 2 emits a few
"Sublexicon is mentioned but not defined" warnings (for as-yet-unwired
`*GeneratedSpeculative` ending paths) — expected, not fatal.

The reasoning behind each step is in the header comment of
[`phonology.xfscript`](phonology.xfscript), which is the authoritative copy
of these commands.

## Grammar file structure

Two files are hand-authored — [`lexicon.lexc`](lexicon.lexc) and
[`phonology.xfscript`](phonology.xfscript); the `*-generated.lexc` files are
produced from the CSV linguistic data by the `generate_*.py` scripts.

### Why the split is where it is

Not every hand-authored line has to be hand-authored:

- **Morpheme *inventory* (root and affix/ending entries)** — CSV-derivable,
  and mostly hand-authored only for historical reasons: entries were added
  to `lexicon.lexc` one at a time (each verified against the Kotlin
  analyzer for the gold word that needed it) *before* the bulk generators
  existed. `generate_roots.py` / `generate_affixes.py` /
  `generate_demonstratives.py` now cover the same CSV files wholesale. What
  genuinely needs to stay by hand is a thin **exceptions layer** — the
  judgement calls the generators deliberately don't make
  (`skip_if_combination`, excluding the `pr` type and `&`-notation rows,
  which spelling variant to wire, a few context restrictions).
- **Continuation-class wiring** (`LEXICON Root`, the `NounContinuations` /
  `VerbContinuations` hubs, which suffix classes each root type reaches) —
  derivable *in principle* from the morpheme type codes (`1v`, `1vn`,
  `1nn`, `tn-*`, …), which already encode what each morpheme consumes and
  produces; the Kotlin analyzer builds its transitions from exactly that.
  Currently a hand-designed simplification of that graph.
- **`phonology.xfscript`** — genuinely hand-authored, and not for
  historical reasons:
  - the CSV encodes phonology unevenly: sometimes as a real general rule
    (one deterministic surface candidate per V/t/k/q context → a clean
    `xfst` rule), sometimes as hardcoded literal surface forms with no
    recorded conditioning, disambiguated Kotlin-side by brute-force string
    matching. "Rule vs. two literal entries" is a per-case judgement the
    CSV does not contain;
  - some behaviour lives only in Kotlin control flow (the lenient second
    pass, `removeCombinedSuffixes`, the longest-root/fewest-morphemes final
    sort, `sameAsNext`/`samePosition` guards) — several of these are not
    expressible as FST transition rules at all;
  - the finite-state encoding adds bookkeeping absent from the source data
    entirely: marker-symbol placement, rule *ordering*, guard rules
    (`JGUARD`/`KGUARD`).

  See [`doc/dev/plans/fst-analyzer-plan.md`](../../../doc/dev/plans/fst-analyzer-plan.md)'s "Open
  risks" section — *"Phonology-rule authoring is the hardest part … requires
  real Inuktitut phonology judgment, not mechanical code translation."*

Direction of travel: push as much of the inventory as possible into the
generated files, keeping `lexicon.lexc` down to the architecture, the
phonology-bearing entries, and the exceptions layer. A first pass has
removed the ~185 hand entries that were byte-for-byte identical to an entry
a generator already emits (coverage over the gold standard unchanged); what
remains in `lexicon.lexc` is entries that still differ from the generated
inventory — spelling variants, tighter continuations, marker symbols — plus
the parts that are not inventory at all. The linguistic investigation that
originally accompanied each moved entry (which CSV candidates were used, how
it was verified, which alternatives were rejected) now lives in a
`PER-MORPHEME INVESTIGATION NOTES` section at the end of `generate_roots.py`
and `generate_affixes.py`, grep-able by morpheme id.

### Gold-standard fitting — read before trusting the coverage numbers

Because `lexicon.lexc` and `phonology.xfscript` were grown **one gold word
at a time**, a number of hand-authored lines exist because a specific
gold-standard word needed them, not because the CSV data says so. Benoit's
analyzer has no such per-word patches — every decomposition comes from the
CSV plus general rules. The full inventory is in
[`gold-standard-fitting.md`](gold-standard-fitting.md); in short:

- **Spelling variants absent from the CSV `variant` column** (~40–70
  entries): `iglu→illu`, `kanangnaq→kanannaq`, `nattilik→natsilik`,
  `taamna→tanna`, `ikpaksaq→ippaksaq`, … These stand in for a *general*
  consonant-cluster-assimilation rule (`gl↔ll`, `ngn↔nn`, `kp↔pp`, …) that
  Benoit applies to every root from the CSV. **≈62 of the 920 `--fair`
  correct words depend on this hand layer** (removing all hand roots drops
  `--fair` to 858/922). One of them, `illaq`, the FST accepts even though
  Benoit rejects it.
- **Coverage scoped to what the gold sample exercises** — affix context
  candidates (V/t/k/q) are wired only where an attested word needs them;
  the rest are "not attempted: unattested in this corpus".
- **Explicit guesses** flagged as such in comments (`ptingni` 1p forms,
  `innaq/1nn`, `ralaaq/1nn`, `tit/1vv`, …).
- **One `phonology.xfscript` rule** (`k → m`) added to fix a single word.

Until the general cluster rule is ported (it is on the to-do list), report
the FST's gold coverage *with* this caveat, not as a clean CSV-plus-rules
figure.

### `lexicon.lexc` — morphemes and how they concatenate

Standard `lexc` format (HFST's `hfst-lexc`). It compiles as a **generator**
(analysis + tags → pre-phonology surface); the analyser queried by
`hfst-lookup` is this composed with the phonology rules and then inverted.
`lexicon.lexc`'s own header documents the tag/boundary convention in full.

- **`Multichar_Symbols`** — every multi-character symbol used below:
  - morpheme **signature ids** (`+1v`, `+1n`, `+1vn`, `+1nv`, `+tn-nom-s`,
    `+tv-ger-3s`, …) — same ids as the Kotlin analyzer's `.csv` data;
  - **`++`** — the boundary between two morphemes (Benoit's `}{`); a single
    **`+`** joins a morpheme's canonical form to its id (Benoit's `/`);
  - **internal marker symbols** — placed on an entry's lower side to trigger
    a phonology rule, then deleted by that rule. They never appear in
    output:

    | Marker | Effect (see the rule's own comment in `phonology.xfscript`) |
    |---|---|
    | `SUPPR` | delete a preceding stem-final `q`/`k`/`t` (suffix wants a vowel-final stem) |
    | `DECAP` | delete a suffix-initial `i` after a `VV` stem |
    | `VVNG` | insert `ng` after a `VV` stem |
    | `NASAL` | rewrite stem-final `k`→`ng`, `q`→`r` |
    | `PMARK` | labialize: `v`/`k` → `p` |
    | `VOICE` | lenite: `t`→`l`, `k`→`g`, `q`→`r` |
    | `MINSERT` | insert `m` after a vowel |
    | `TNNOMD` | copy the preceding vowel (dual `tn-nom-d` ending) |
    | `JGUARD`, `KGUARD` | block a blanket `j`→`t` / `k`→`t` rule from firing inside a root's own spelling |

    `LENIENT` is described with its rule; it is **weighted, not a marker**
    (weight can't be injected as a symbol after `read regex`).

- **`LEXICON Root`** — the start state. Fans out to the hand-picked
  categories (`Nouns`, `Verbs`, `Conjunctions`, `Exclamations`, `Adverbs`,
  `Pronouns`) and to the generated root lexicons (`NounsGenerated`,
  `VerbsGenerated`, …).
- **Root lexicons** list entries `canonical+id:surface  Continuation ;` and
  send every noun/verb root to the fan-out hub `NounContinuations` /
  `VerbContinuations`.
- **`NounContinuations` / `VerbContinuations`** — hubs that let *any* root
  reach *every* applicable suffix and ending class (hand-written +
  `*Generated` + `*GeneratedSpeculative`), plus `# ;` (word may end here).
- **Suffix / ending lexicons** (`VnSuffixes`, `NvSuffixes`, `NnSuffixes`,
  `VvSuffixes`, `NounEndings`, `TvEndings`, …) — entries
  `++canonical+id:surface  Continuation ;`; derivational suffixes loop back
  through a continuation hub, terminal endings go to `# ;`.

Entries are commented one by one with the gold word that motivated them and
how the surface form was verified — read those comments before changing
anything.

### `phonology.xfscript` — surface rewrite rules

`read regex Lexicon` loads the combined lexc, then a single `.o.`
composition chain applies context-sensitive replace rules
(`[ x -> y || left _ right ]`), mostly in **pairs**: one rule fires on a
marker symbol, the next deletes the marker. Rules are added one at a time
alongside the lexicon. The chain ends with the optional, weighted `LENIENT`
rule and `save stack lexicon-generator.hfst`.

### `*-generated.lexc` — generated, committed, never hand-edited

Same lexc syntax, written from `core/.../dataCSV/` by the three
`generate_*.py` scripts. Each defines its own `…Generated` (and
`…GeneratedSpeculative`) `LEXICON` blocks, which the hand-written
continuation hubs reference by name. `…GeneratedSpeculative` = entries
wired **without** respecting their CSV condition column and unverified
against the gold standard (see `suffixes-generated.lexc`'s header).

| File | From | Defines |
|---|---|---|
| `roots-generated.lexc` | `generate_roots.py` | `NounsGenerated`, `VerbsGenerated`, `AdverbsGenerated`, `PronounsGenerated`, `ConjunctionsGenerated`, `ExclamationsGenerated` |
| `suffixes-generated.lexc` | `generate_affixes.py` | `{Vn,Vv,Nn,Nv}SuffixesGenerated(Speculative)` + many context-restricted helper lexicons |
| `endings-generated.lexc` | `generate_affixes.py` | `{Tn,Tv}EndingsGenerated` |
| `demonstratives-generated.lexc` | `generate_demonstratives.py` | `Demonstrative{Adverbs,Pronouns}Generated` + their roots and endings |

## Running the FST on a word

`segment_iu.py` is the `data/grammar/fst/` counterpart of the `:cli` command
(which calls itself `segment_iu`): same three modes and option names.

```bash
cd data/grammar/fst

python3 segment_iu.py atuagaq                       # positional, = --word
python3 segment_iu.py --word iglumut
python3 segment_iu.py --word atuagaq --raw          # hfst-lookup's own strings + weights
python3 segment_iu.py --word nalunaijaijuq --lenient-decomps
python3 segment_iu.py --interactive                 # prompt loop, 'q' to quit
printf 'atuagaq\niglumut\n' | python3 segment_iu.py --pipeline   # one JSON per line
python3 segment_iu.py --word iglu --analyser lexicon-analyser.hfst
```

`--pipeline` emits the same JSON keys as the Kotlin CLI's `--pipeline`
(`word`, `lenient`, `elapsedMSecs`, `timedOut`, `exception`,
`decompositions`); `timedOut` is always `false` here (an `hfst-lookup` is
instantaneous, so there is no `--timeout-secs`).

For a one-off check without the wrapper:

```bash
echo "atuagaq" | hfst-lookup -q lexicon-analyser.hfstol
```

## The other scripts

Measurement and code-generation tools, all run as `python3 <name>.py` from
this directory.

| Script | Purpose |
|---|---|
| `histogram.py` | 4-category histogram (first-correct / not-first / not-present / no-decomps) over a small curated batch of gold words. |
| `full_corpus_check.py` | Same histogram over the **whole** gold standard, by default restricted to the words the `:cli` accuracy suite itself evaluates for a like-for-like percentage; `--all` widens it back to every gold word (misspelled/proper-name/etc. included) for gap-finding. |
| `topn_stats.py` | "Correct decomposition within top N" (N=1..5) for the FST raw order, a reranked order, and Benoit's analyzer, over the fair-vs-`:cli` population by default (`--all` widens it). |
| `hansard_volume_speed.py` | Decomposition-volume and wall-clock comparison, FST vs Benoit, over the 10k most frequent Hansard word forms (see `hansard-cache/`). |
| `benoit_sort.py` | Replicates the real analyzer's decomposition ranking (`DecompositionState.compareTo()`), for reranking FST output. |
| `remove_combined.py` | Replicates `DecompositionState.removeCombinedSuffixes()`. |
| `affix_frequency.py` | Ranks affix ids by how many gold words use them — the order in which to add phonology rules. Output: [`affix-priority.md`](affix-priority.md). |
| `next_affix_priority.py` | Re-ranks affix priority against the *current* implementation state rather than the static frequency list. |
| `snapshot_correct.py` | Writes the exact set of currently-correct gold words, for strict regression diffing across lexicon/phonology edits. |
| `generate_roots.py`, `generate_affixes.py`, `generate_demonstratives.py` | Generate the `*-generated.lexc` files from `core/.../dataCSV/`. |

## Data files

- `hansard-cache/` — the real analyzer's output over the 10k most frequent
  Hansard words, plus the FST-vs-Benoit volume/speed comparison. See
  [`hansard-cache/README.md`](hansard-cache/README.md).
- `*-generated.lexc` — generated lexc sources (committed; do not hand-edit).
- `affix-priority.md` — current affix priority ranking (regenerated by
  `affix_frequency.py`).
