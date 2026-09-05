# Plan: an HFST-based finite-state morphological analyzer for Inuktitut

## Goal

Evaluate whether the current recursive-backtracking analyzer
(`MorphologicalAnalyzer_R2L.decomposeWord()` in `:core`) could be replaced,
in whole or in part, by a compiled finite-state transducer (FST) — for
performance, and because the Giella infrastructure (below) gives this
project a mature, battle-tested toolchain and methodology to build on
instead of inventing one from scratch.

This document is the detailed plan requested on the `spike-FST` branch,
after reading three documents from Benoît Farley (the original analyzer's
author) describing its algorithm and data model, and after surveying the
`giellalt` GitHub organization for a suitable reference implementation.

## Reference implementations: giellalt/lang-ipk and giellalt/lang-kal

[giellalt](https://github.com/giellalt) is an actively maintained
open-source infrastructure for building FST-based morphological analysers
(plus Constraint Grammar disambiguation, spellcheckers, etc.) for
minority/indigenous languages, mostly Uralic and circumpolar. Two of its
member repos are directly relevant:

- **`giellalt/lang-ipk`** (Iñupiaq, Alaska) — **the primary reference**.
  Iñupiaq and Canadian Inuktitut are both varieties on the same Inuit
  dialect continuum (Iñupiaq ↔ Inuktitut ↔ Kalaallisut), making it
  structurally and phonologically the closest existing Giella language
  module. No Canadian Inuktitut module (`ike`/`ikt`) exists in `giellalt`
  today — this project would be filling a real gap, not just building an
  internal tool.
- **`giellalt/lang-kal`** (Kalaallisut/Greenlandic) — secondary reference,
  for cases Iñupiaq doesn't cover well. More distantly related, but the
  same Giella tooling and conventions apply.

**Important caveat**: both are structural/methodological templates, not
sources of reusable vocabulary or rules. The actual Inuktitut roots,
affixes, and phonological alternations must come from this project's own
CSV data (`data/grammar/linguistic-data/`) and from Alain's own linguistic
judgment — copying Iñupiaq or Kalaallisut lexc/phonology content directly
would produce a wrong analyzer for Inuktitut specifically.

Confirmed repo structure (both repos, same shape):
```
src/fst/morphology/
  root.lexc              — alphabet, multichar symbols, POS tags
  stems/*.lexc            — roots by word class (nouns.lexc, verbs.lexc, ...)
  affixes/*.lexc           — derivational/inflectional continuation lexicons
  phonology.xfscript       — phonological alternation rules (xfst replace rules)
src/cg3/                  — Constraint Grammar disambiguation (not needed for
                             this project's use case — see Non-Goals)
```
Compiled with `hfst-lexc` + `xfst`/`hfst-twolc`, producing an
`hfst-optimized-lookup` binary transducer (`.hfstol`), queried at runtime
with `hfst-lookup` (CLI) or `libhfst`'s lookup API.

**Toolchain availability confirmed**: `hfst` (3.16.0-5) is installable via
`apt` from `deb.debian.org`, which is already in this project's devcontainer
firewall allowlist. Unlike Playwright/poppler-utils earlier this same day,
this doesn't need a container rebuild — just `sudo apt-get install -y hfst`,
run once by Alain (the AI agent's sudo is restricted to the firewall script
only, per today's earlier findings).

## Mapping the existing algorithm onto lexc/xfst concepts

From the three documents (IMA Slides, Database Slides, Morphological
Analyzer prose doc):

| Current Java concept | lexc/xfst equivalent |
|---|---|
| `Graph.java` state graph (`W → W* → {N,V,A,...} → []`) | lexc `LEXICON`/continuation-class structure — this maps very directly, lexc *is* a way of writing exactly this kind of automaton |
| Root/affix CSV data (`RootsSpalding.csv`, `Suffixes.csv`) | lexc stem/affix entries — machine-generated from the existing CSVs via a script, not hand-typed (see Preserving Data Integrity in `AGENTS.md`) |
| `Action.java` phonological transformations (consonant insertion/deletion, voicing, place assimilation) | `xfst` replace rules in `phonology.xfscript` — the hardest, most linguistically sensitive part to get right |
| `condPrec`/`condOnNext`, transitivity, case/mood agreement, `sameAsNext`/`samePosition` | flag diacritics (`@U.FEATURE.VALUE@` etc.) — powerful but easy to get subtly wrong; needs careful testing |
| Decomposition signature (`1n`, `1v`, `1nn`, `tn-dat-p`, ...) | FST output tags — tag design needs to stay compatible enough with the existing format for the validation harness below to compare results |

## Validation strategy: the existing 919-word gold standard

`cli/src/test/kotlin/org/iutools/morph/MorphAnalGoldStandard_Hansard.kt`
(and `MorphAnalGoldStandard_WordsThatFailedBefore.kt`) already contain 900+
real words with known-correct decompositions, e.g.:
```kotlin
addCase(AnalyzerCase("akinga", arrayOf("{aki:aki/1n}{nga:nga/tn-nom-s-4s}")))
addCase(AnalyzerCase("amisuummata", arrayOf("{amisu:amisu/1n}{u:u/1nv}{mmata:mata/tv-caus-4p}")))
```
This is a real, existing asset — not something to build for this project,
just to reuse. The current backtracking analyzer's baseline on this set is
the documented histogram (670 first-decomposition-correct / 247
correct-but-not-first / 2 correct-not-present / 0 no-decomps, out of 919).
Any FST prototype should eventually be measured against the same set, with
its own histogram, for a real comparison — not just "does it work on a few
examples I hand-picked."

**Output-format nuance to solve before large-scale comparison**: the FST's
tag output and the backtracking analyzer's `{surface:canonical/signature}`
string format won't be byte-identical without deliberate tag design work.
For the proof-of-concept stage, comparing "did it produce the right
sequence of morphemes" by eye is enough; a proper automated comparison
script comes later (Milestone 4 below), once the tag format is stable
enough to be worth automating against.

## Proving it can work before scaling up

Alain's request: don't build the full lexicon/ruleset first — prove the
approach on a couple of non-trivial real words first, then grow.
Concretely, a minimal lexc/xfst grammar containing *only* the roots,
affixes, and phonological rules those specific words need, compiled and
queried with real HFST tools, checked against the known-correct
decomposition from the gold standard above.

### Milestone 0 — toolchain smoke test
Install HFST (`sudo apt-get install -y hfst`), compile the most trivial
possible lexc file (one root, no affixes), run `hfst-lookup` on it, confirm
the whole pipeline (lexc → hfst-lexc → .hfstol → hfst-lookup) works at all
in this environment. Pure plumbing, no linguistics yet.

**Done.** `data/grammar/fst/milestone0-smoke-test.lexc`; the whole pipeline works
(one snag: an empty `Multichar_Symbols` section makes `hfst-lexc` fail with
a syntax error — omit the section entirely when there's nothing to declare,
rather than leaving it empty).

### Milestone 1 — simplest real case: `akinga`
`{aki:aki/1n}{nga:nga/tn-nom-s-4s}` — a noun root plus one terminal noun
ending, no phonological alternation (straight concatenation: `aki` + `nga`
= `akinga`). Minimal lexc: one root in a `Nouns` lexicon, one ending in a
`NounEndings` lexicon reachable from it, no phonology rules needed at all.
Goal: `echo akinga | hfst-lookup analyser.hfst` returns a tag sequence
identifiable as `aki` + the nominative-singular-4th-person ending.

**Done.** `hfst-lookup` always reads the transducer's *upper* side as
input; lexc's own convention writes upper=analysis, lower=surface (a
*generator*), so the actual analyser (surface in, tags out) is produced by
inverting the compiled net with `hfst-invert` before querying it — every
milestone since builds on that same generator→invert→(optimize) pipeline.
Settled the tag format here too: a morpheme's own canonical form and its
signature id are joined by a single `+` (playing Benoit's `/` role); `++`
marks the boundary to the next morpheme (playing Benoit's `}{` role). A
bare `/` itself is avoided — it's `xfst`'s replace-rule context operator,
confirmed by testing that `a -> b / c _ d` fails to parse (`xfst` wants
`||` there instead), so a literal `/` inside a tag would collide with
phonological rules from Milestone 2 onward.

### Milestone 2 — adding a derivational suffix and real phonology
Originally planned around `amisuummata`
(`{amisu:amisu/1n}{u:u/1nv}{mmata:mata/tv-caus-4p}`, `u`+`mata`→`ummata`
gemination) — **swapped for `amittuq`
(`{amit:amit/1v}{tuq:juq/1vn}`)** after investigating the source Kotlin
analyzer (`Endings_verb.csv`, `Affix.kt`, `LinguisticData.kt`) found that
the `mata`→`mmata` alternation isn't a general rule there at all: it's two
hardcoded literal candidate surface forms in the CSV, disambiguated only by
brute-force string matching against the real word, with no conditioning
link recorded to what precedes. Porting that faithfully would mean two
literal lexc entries, not an `xfst` rule — it wouldn't actually test
whether `Action.java`-style phonology translates into `xfst` rules, which
is the whole point of this milestone. `amittuq`'s suffix allomorphy (the
`juq`/`tuq` context table in `Suffixes.csv`, every `V`/`t`/`k`/`q`-form
column holding exactly one deterministic candidate, not several) is a
genuine, general, context-conditioned rule instead: `juq`'s initial `j`
surfaces as `t` after a t/k/q-final stem, and this needed no invisible
boundary marker — the preceding letter itself is a sufficient trigger,
already present in the surface string.

**Done.** `data/grammar/fst/lexicon.lexc` + `phonology.xfscript`. This milestone
is also where each suffix's own canonical text started being echoed into
the tag stream (not just its id) — needed as soon as surface can differ
from canonical, which is exactly this word (`juq` canonical, `tuq`
surface).

### Milestone 3 — a long derivational chain: `aanniaqtulirijikkunnut`
`{aanniaq:aanniaq/1v}{tu:juq/1vn}{liri:liri/1nv}{ji:ji/1vn}{kkun:kkut/1nn}{nut:nut/tn-dat-p}` —
verb root, then `vn → nv → vn → nn → tn`, six morphemes cycling through
most of the noun/verb continuation-class alternation. Proves the
continuation-class graph handles realistic derivational depth, not just a
single suffix — `VnSuffixes`/`NvSuffixes` in `data/grammar/fst/lexicon.lexc` are
written as genuinely reusable continuation classes (the same `VnSuffixes`
lexicon serves both `juq` and `ji`, and `NvSuffixes` loops back into it),
not one-off per-word lexicons.

**Done — with the phonology turning out different from what this section
originally predicted**, confirmed against the source Kotlin analyzer before
writing anything (same investigative discipline as Milestone 2's word
swap):
- `juq → tuq` reuses Milestone 2's own rule (root `aanniaq` is q-final).
- `tuq → tu` (the `q` drops before `liri`) is **not** a general "q before
  l" sound change — it's a lexical property of the suffix `liri`
  specifically (`Suffixes.csv`'s q-context row for `liri` carries a
  `Suppression` action on what precedes it). Modeled with an invisible
  `QDEL` marker inserted right before `liri`'s own lexc entry, rather than
  a blanket context rule, so it can't accidentally delete every future
  q-before-l sequence the lexicon might grow to include.
- `kkut → kkun` before `nut` **is** a genuinely general, systematic rule
  (`Action.Assimilation`, the same action code recurring in hundreds of
  rows across `Suffixes.csv`/`Endings_noun.csv`/`Endings_verb.csv`) — a
  direct, marker-free context rule, `t` immediately before `n`.
- The "second gemination" this section originally called out turned out
  not to be a separate phenomenon at all: the doubled `nn` in `...kkunnut`
  is a free byproduct of the assimilation above plus plain concatenation
  with `nut`'s already-lexical initial `n` — no extra rule or lexc entry
  needed for it.

If Milestone 3 works, the core mechanics (continuation classes mirroring
`Graph.java`, phonological rules mirroring `Action.java`) are validated on
real, representative complexity — the remaining work becomes *breadth*
(more roots/affixes/rules from the CSV data), not new mechanism.

### Milestone 4 — small curated batch, first real regression check
Hand-pick ~15-20 gold-standard words covering the different signature
types seen in the docs (`n`, `v`, `c`, `a`, `e`, `p`, `nn`, `nv`, `vn`,
`vv`, `tn`, `tv`, `q`), still with a minimal (not full) lexicon scoped to
just those words. Write a small script (`data/grammar/fst/` — same spirit as
`tools/build_hansard_index.py`) that runs `hfst-lookup` over the batch and
reports a first tiny histogram, the same shape as the existing 919-word
one. This is also where the output-format comparison question (above) gets
a real, working answer instead of eyeballing results.

### Milestone 5+ (not detailed yet — sequenced, not scoped)
Scale the lexicon up by generating lexc entries from the full CSV data,
build out the full phonology ruleset, and run against the complete 919-word
gold standard for a real histogram comparison against the backtracking
baseline. Left deliberately unscoped until Milestones 0-4 prove the
approach — no point detailing a full-scale plan before the mechanism is
validated.

Within this phase, coverage work has been organized around two things: an
affix's marginal value against the CURRENT lexicon state (recomputed after
every batch via `data/grammar/fst/next_affix_priority.py`, not a one-time static
ranking — see that script's own docstring), and, periodically, a shared
underlying *mechanism* that blocks several affixes/endings at once (a
"lever") rather than one affix at a time. Two levers found and implemented
so far: the id-conditional k/q-context pattern shared by several `tn-*`
noun endings (`grep "if(id:"` across `Endings_noun.csv`), and the
Voicing/Fusion phonology primitives (CSV action codes `son`/`fus`).

**Known incomplete on purpose: the imperative mood** (`Endings_verb.csv`'s
~72 `tv,imp` rows — 9 intransitive person/number combinations, 63
transitive subject×object combinations, confirmed exhaustive for that
dimension by cross-checking the person/number combinations present).
Investigated as a candidate third application of the Voicing/Fusion lever,
but a full sweep of the 985-word gold standard found only **two** words
anywhere using any imperative ending (`tunngasugit`, `tunngasugitsi`) —
Hansard parliamentary transcripts essentially never contain direct
commands, so the CSV's row count doesn't translate into real corpus value
here the way it did for the other two levers. Implemented only the one
cleanly gold-attested case (`suk`/1vv + `git`/tv-imp-2s); left the other 7
mechanically-identical-but-unattested intransitive rows and the entire
63-row transitive family unimplemented, specifically to avoid breaking
from this project's "verify against a real gold example" discipline —
building the rest would mean trusting the CSV and already-proven
mechanisms with no automated way to catch an error, and this exact family
already produced one caught CSV/reality mismatch (`gipsi`/tv-imp-2p's
attested surface contradicts its own CSV row). See
`data/grammar/fst/lexicon.lexc`'s own "round 10" comment for the full
investigation. Revisit if a future data source (dictionary examples,
elicited forms) provides real attestation for the untested combinations.

## Non-goals for now

- **On-device runtime** — real, scoped, buildable work, but entirely
  deferred until an FST is proven accurate enough to be worth shipping. All
  milestones above run as a dev-time tool (`data/grammar/fst/`), not inside the
  app. When this is eventually tackled, note there's a proven precedent to
  build on rather than a from-scratch Kotlin `.hfstol` reader: **libhfst
  itself (C++) is not the mobile path** — no known precedent embeds it via
  JNI/iOS native bindings on either platform — but
  [Divvun Keyboards](https://play.google.com/store/apps/details?id=no.uit.giella.keyboards.Sami)
  (same `giellalt` ecosystem as `lang-ipk`/`lang-kal` above) ships live
  Android *and* iOS keyboard apps with on-device HFST-based spellcheckers
  for Sámi languages today, built on
  [divvunspell](https://github.com/divvun/divvunspell) — a Rust
  reimplementation of `hfst-ospell`'s optimized-lookup algorithm, using a
  byte-aligned transducer format (THFST/BHFST, not plain `.hfstol`) that's
  memory-mapped rather than fully loaded, specifically because plain
  optimized-lookup wasn't fast enough on ARM without that alignment work.
  Worth evaluating divvunspell via Kotlin/Native FFI (JNI on Android,
  cinterop/XCFramework on iOS — a known pattern for consuming a Rust C-ABI
  library from Kotlin Multiplatform) against writing a Kotlin reader from
  scratch, once this milestone is actually reached.
- **CG3 disambiguation** — the `src/cg3/` half of the Giella stack
  (statistical/rule-based disambiguation between multiple valid parses)
  isn't needed for this project's use case (returning all valid
  decompositions is already how the current analyzer behaves) — skip
  entirely unless a concrete need shows up later.
- **Full lexicon/ruleset** — explicitly out of scope until Milestones 0-4
  succeed (see above).

## Open risks, named honestly

- **Phonology-rule authoring is the hardest part.** Translating
  `Action.java`'s imperative, context-dependent transformations into
  declarative `xfst` rules requires real Inuktitut phonology judgment, not
  mechanical code translation — this needs Alain's close review at every
  step, not just at the end.
- **Flag diacritics for relational conditions** (transitivity agreement,
  case/mood attributes, `sameAsNext`/`samePosition` duplicate-avoidance)
  are a well-known FST technique but easy to get subtly wrong and
  non-obvious to debug — budget real time for this in Milestone 3+.

  **Update (Milestone 5+, round 11 investigation)**: read the actual
  source (`core/.../morph/r2l/MorphologicalAnalyzer_R2L.kt`,
  `DecompositionState.kt`) to see exactly what lives in Kotlin control
  flow rather than the CSV-implied transition graph. Three distinct
  things, not one:
  1. **Whole-word retry strategies** tried BEFORE any morpheme-level
     analysis (final "n" reanalyzed as "t"; a possibly-missing final
     consonant; a hardcoded transliteration-error regex fixup,
     `([iua])qk([iua])` → `$1qq$2`). Per Alain: the missing-final-consonant
     handling specifically is deferred to Kotlin-side pre/post-processing
     whenever this becomes a real analyzer, not modeled in the FST — it's
     also decreasingly relevant as the written language standardizes. The
     regex fixup is cheap and safe to port (pure text substitution before
     `hfst-lookup` is even called, can't cause FST regressions) but
     currently matches **0 of the 985 gold words** — real, but not a
     coverage lever on this corpus; worth doing eventually as defensive
     handling for real user input, not for the accuracy-gate histogram.
  2. **`sameAsNext`/`samePosition`**: history-dependent checks during the
     recursive search, preventing a suffix from "eating" characters
     already claimed by the next suffix's own deletion action. Not
     cleanly portable to HFST's local, context-restriction-rule model —
     the risk it guards against already shows up in this project as the
     accepted-risk false-positive category (`full_corpus_check.py`'s
     `correct-not-present` canary), not something with a clean native FST
     mechanism.
  3. **Global, cross-candidate post-processing** in `doDecompose()`:
     `removeCombinedSuffixes` (when a spelled-out sequence of morphemes
     and a single composite-morpheme entry explain the same string with
     the same flanking context, keep only the composite reading) and a
     final sort (longest root first, then fewest morphemes) that decides
     which accepted decomposition is presented *first*. Neither is
     expressible as FST transition rules — both compare the FULL SET of
     candidate decompositions against each other after the search
     completes. The sort is the direct explanation for why this project's
     own histogram has always tracked `first-decomposition-correct`
     separately from `correct-but-not-first`: `hfst-lookup` has no notion
     of Benoit's tie-break preference, it just enumerates whatever paths
     the automaton has. This IS expressible via HFST's weighted
     (tropical-semiring) transducers — already the format in use here —
     but **tested empirically against the current 274-word-correct state
     and found net negative** (naively weighting by root-string-length
     produced 1 gain but 7 regressions): this project's bulk-generated
     root set (`generate_roots.py`) has more homograph/shadowing noise
     than Benoit's own curated root database, so porting his tie-break
     rule as-is interacts badly with our noisier data. Worth revisiting
     once false-positive/shadowing counts are lower, not now.
     `removeCombinedSuffixes`'s relationship to this project's own
     `skip_if_combination` root-exclusion filter (`generate_roots.py`) is
     real (same underlying CSV "combination" column) but not fully
     understood yet — a genuine open question (specifically: where
     `decomposeCompositeRoot` gets set `true` and whether
     `CommonCompositeWords.csv`-sourced roots are treated differently from
     `Suffixes.csv`-level composite suffixes) that would need real
     investigation before committing effort, not assumed to be a
     promising lever without that.
  **Conclusion**: none of these three threads currently outperforms
  continuing with affix/ending-level work (the "lever" approach used in
  rounds 7-11) — documented here so the investigation doesn't need
  repeating, not because the underlying architectural gap isn't real.
- **Iñupiaq/Kalaallisut are related languages, not Inuktitut** — every
  phonological rule and every piece of vocabulary must be verified against
  this project's own data/Alain's judgment, never assumed correct because
  it worked for Iñupiaq.
- **Effort scale**: this is realistically weeks of iterative work even with
  AI assistance, not a single-session spike — the milestone structure above
  exists specifically so the very first question ("can this even work at
  all") gets answered in hours, not weeks, before committing to the rest.
