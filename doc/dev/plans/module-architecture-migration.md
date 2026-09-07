# Plan: module architecture migration (`core` / `apps` / `data` + platform source sets)

Status: **in progress.** Owner: Alain. Created 2026-09-04.

## Progress

- **2026-09-04** -- `data/{grammar,corpus,lexicon}/` created; `tools/fst/`
  moved to `data/grammar/fst/` (lexc sources, `phonology.xfscript`, the
  `*-generated.lexc`, the `generate_*.py` generators, `hansard-cache/`, the
  re-ranker experiment, the FST docs -- everything, together, to keep the
  script<->lexc relative paths and Python imports intact). All path
  references in `:fst`, `:cli`, `:core`, `:composeApp` and the docs updated;
  the Python generators' `REPO_ROOT` depth bumped one level; `:cli:test`
  re-run. Done ahead of Benoit starting FST work so he branches from the
  refactored layout. This is Phase 0 plus an early slice of Phase 3; the
  later phases below are unchanged.

## Why

The original iutools Java project was three interrelated projects --
`iutools-core`, `iutools-apps`, `iutools-data`. iutools2 has drifted from
that shape and two problems have surfaced:

1. **Non-UI logic is stuck in `:composeApp`.** The "Guess Meaning"
   machinery -- `SpaldingDictionary`, `TusaalangaFetcher`, Hansard
   bilingual-example assembly, prompt building, the Claude call
   (`GuessMeaningEngine`) -- lives in the UI module only because that was
   the first place that needed it. It is domain logic, reusable across
   CLI / Compose / iOS, and it cannot currently be called from a headless
   JVM tool.

2. **Generated data had no home.** The FST prototype (then in `tools/fst/`)
   produces lexc sources, morpheme-frequency priors, and the compiled FST;
   the re-ranker experiment produces feature tables; the planned LLM silver
   standard would be another artifact; the Spalding dictionary is parsed
   and embedded; there are Hansard word-frequency lists and (eventually) a
   bilingual example corpus and a morpheme dictionary. These are data that
   have value in their own right, and were scattered across `tools/`,
   `:core`'s resource tree, and live network calls in `:composeApp`. The
   FST slice of this is now addressed (see Progress); the rest is Phase 3.

The immediate trigger is the **LLM silver-standard generator** for the FST
re-ranker (see `data/grammar/fst/reranker-experiment.md`): it needs to run the
same context-assembly + Claude-ranking that Guess Meaning does, but as an
offline batch over ~10k words, with no duplicated implementation.

## Target architecture

Two **orthogonal** axes. They are not in tension -- they answer different
questions.

### Axis 1 -- functional: Gradle modules

| grouping | modules | role |
|---|---|---|
| **core** | `:core` | the analyzer + reusable domain logic (incl. the Guess Meaning enrichment logic). KMP library. Ships in every app. |
| **apps** | `:cli`, `:composeApp`, later `desktopApp` / `iosApp` | entry points and UI. Depend on `:core`; add no domain logic of their own. |
| **data** | passive artifacts under `data/`, plus a JVM-only `:data-gen` (or `:cli` subcommands) | data files + the thin generator/manager code that produces them. |

Module *names* stay as they are (`:core`, `:cli`, `:composeApp` -- the
KMP-wizard defaults, see `AGENTS.md`). The `core` / `apps` / `data`
grouping is expressed as **top-level directories**, not by renaming
modules -- renaming churns `settings.gradle.kts`, every build file, IDE run
configs, and the Android-Studio test-config skill's `iutools2.*` labels.

### Axis 2 -- platform: KMP source sets inside each module

`commonMain` / `jvmMain` / `androidMain` / `iosMain` / `desktopMain`
(/ `jsMain` if web ever returns). Each module declares only the targets its
functionality needs:

| module | targets |
|---|---|
| `:core` | JVM + Android + iOS |
| `:composeApp` | Android + iOS + Desktop(JVM) |
| `:cli` | JVM only |
| `:data-gen` | JVM only |
| `androidApp` / `iosApp` / `desktopApp` | one platform each |

### The two rules that reconcile the axes

- **"Cannot compile for platform X"** (JVM-only API, non-KMP dependency)
  -> **source set.** Put it in `jvmMain`. `androidMain` does not depend on
  `jvmMain`, so it is genuinely excluded from the APK.
- **"Compiles fine but must not ship in an app"** (generators, crawlers,
  batch tooling, heavy test suites) -> **separate module** that no app
  depends on. Source sets cannot express "JVM yes, but keep out of every
  shipped app".

### Litmus test for `:core`

> Does at least one shipped app (Android / iOS / desktop) call this at
> runtime?

- **Yes** -> `:core` (`commonMain` by default; a platform source set only
  when forced by a platform API or a non-multiplatform dependency).
- **No** (only consumers are dev tools, the CLI, or generators) -> its own
  module, never `:core`. Precedent: the full accuracy/regression suite
  lives in `:cli` (JVM-only, ships nothing), not `:core`.

### What `:data` is

- **Passive data**: platform-agnostic files, or Kotlin generated from them
  and embedded (the pattern `:core`'s linguistic CSVs already use).
- **Generators/managers**: build-time, JVM-only, may do network + file
  I/O. Each is a **thin wrapper** -- it orchestrates and writes an
  artifact, delegating all domain logic to `:core`. The Guess-Meaning-based
  silver-standard generator is the first: "Guess Meaning in a loop, plus
  write results to a file."
- Membership test: *is it orchestration that produces a data artifact,
  delegating domain logic to `:core`?* -> yes: `data/`.

### What does NOT move (revised 2026-09-05)

This section originally said the linguistic-data CSVs would stay embedded
in `:core` (`core/.../dataCSV/generated/`), on the assumption that they
were already embedded as generated Kotlin source. That directory never
existed -- the CSVs were plain JVM classpath resources, loaded by
`LinguisticDataCSV.kt` via `java.io.*`. Moved 2026-09-05 to
`data/grammar/linguistic-data/`, following the same split the FST already
uses: raw source data in `data/`, a separate embedded artifact in the
module that ships it. `:core` still doesn't gain a dependency on `:data` at
build time -- Gradle points `:core`'s `commonMain` resources at the new
path directly (`core/build.gradle.kts`'s `resources.srcDir`).

## Migration steps

Each step must leave `main` building and the regression gate green
(`./gradlew :cli:test` -- Hansard histogram unchanged; plus
`:composeApp:compileDebugKotlin` and `:composeApp:testDebugUnitTest` for
steps that touch `:composeApp`). Do the work on a branch, not on `main`.

### Phase 0 -- layout only, no code moves

- [x] Create the `data/` top-level directory with `README.md`s stating
      what it holds (three categories -- `grammar/`, `corpus/`, `lexicon/`)
      and the membership test above. `corpus/` and `lexicon/` are empty
      placeholders for now.
- [ ] Decide whether `apps/` becomes a real directory now (moving
      `cli/` and `composeApp/` under it, updating `settings.gradle.kts`
      paths but **not** module names) or stays deferred. Recommendation:
      defer -- do it only alongside the first new app shell (desktop or
      iOS), to pay the `settings.gradle.kts` / IDE-run-config churn once.
- [x] This plan + `doc/dev/plans/README.md` land on `main`.

### Phase 1 -- extract the Guess Meaning enrichment into `:core`

Status (2026-09-07): the Guess Meaning code **is on `main`** now
(`GuessMeaningEngine`, `GuessMeaningInline`, `guessMeaningSeedPrompt()` in
`WordLookupScreen.kt`, `SpaldingDictionary`, `TusaalangaFetcher`,
`NunavutHansardLocalIndex`, all under `composeApp/.../org/iutools/app/`).
The `llm-guess-meaning-spike` reconciliation is done. An in-progress
unpushed commit on `main` has already moved the leaf value types
(`CandidateMeanings`, `ChatMessage`, `GuessMeaningCacheKey`, `LlmCost`,
`ModelStats`, `MorphemeRow`) to `:core` `org.iutools.llm`, and
`PrefixFallback` to `org.iutools.search`. The pieces below are what's
left.

- [ ] Inventory the pieces in `:composeApp`: `SpaldingDictionary`,
      `TusaalangaFetcher`, Hansard example assembly, prompt builder,
      `GuessMeaningEngine` (the Claude call), and their tests.
- [ ] Move the **pure** logic to `:core/commonMain`: given a word + its
      decompositions + resource data, build the prompt; parse the LLM's
      ranked response; pick top-N. No `java.*`, no HTTP client here.
- [ ] Define transport/data interfaces in `:core/commonMain`
      (`interface LlmClient`, `interface ExampleSource`, ...).
- [ ] Provide `actual`/implementations per platform:
      `:core/jvmMain` (Anthropic SDK or Ktor), `:core/androidMain` as
      needed. iOS impl deferred to the iOS port.
- [ ] `:composeApp`'s Guess Meaning UI now calls `:core`; delete its local
      copies.
- [ ] Gate: `:composeApp:compileDebugKotlin`, `:composeApp:testDebugUnitTest`,
      `:cli:test`.

Progress (2026-09-07): the leaf value types, `guessMeaningSeedPrompt()`,
`BilingualExample`, `DictionaryLookupResult` / `ShorterWordDictionaryResult`
are all moved (see the migration memory for the list). **The `LlmClient`
interface + `GuessMeaningEngine` extraction now has its own detailed,
resume-from-cold plan: [`guess-meaning-engine-to-core.md`](guess-meaning-engine-to-core.md).**
Still-app-side after that: `GuessMeaningCostLog`, `SpaldingDictionary`,
`TusaalangaFetcher`.

#### Phase 1 follow-up -- unify "decomposition -> human-readable form"

Do this **after** the enrichment above is in `:core`, not before (it would
mean refactoring code that's about to move). Deferred 2026-09-07 as too big
to fold into the mechanical class moves; noted here so it isn't lost.

Today there are three separate paths that turn a morpheme / decomposition
into readable text, two of them called `MorphemeRow`:

| where | type | output |
|---|---|---|
| `org.iutools.morphemedict.MorphemeDictionary` | `private class MorphemeRow(id, Morpheme?)` + `descr: MorphemeHumanReadableDescr` | canonical form + grammar gloss + meaning, via `MorphemeHumanReadableDescr` (in `:core`, tested) |
| `org.iutools.llm.MorphemeRow` (moved in the in-progress commit) | `(surfaceForm, morphemeId, Morpheme?)` | rendered by `guessMeaningSeedPrompt()` as `- <surface> (<id>): <meaning>` -- no grammar gloss, forced to syllabic |
| `WordLookupScreen.kt` morpheme-detail popup | -- | reads `morpheme.frenchMeaning` / `englishMeaning` directly |

`MorphemeHumanReadableDescr` is already the "human-readable form" engine;
Guess Meaning just doesn't use it and re-derives a poorer version.

Proposed shape (Alain, 2026-09-07): an abstract `MorphDecompFormatter` in
`org.iutools.morph` (`:core`) that owns the traversal (walk components,
parse `{surface:id/tag}` vs `id/tag`, resolve the `Morpheme`, assemble the
whole, incl. the multi-candidate case) and defers three seams to
subclasses:

- `formatCanonicalForm(canonical)`
- `formatSurfaceForm(surface?)`  -- may be absent (FST output has no matched substring)
- `formatDefinition(morpheme?)`  -- incl. the "unknown morpheme" fallback

Notes / decisions to make when this is picked up:

- The Guess Meaning subclass is **domain logic -> lives in `:core`**, not
  `:composeApp`. Only a Compose-rendering formatter would go in the app.
- The Morpheme Dictionary is **not** a subclass: `MorphemeResultCard`
  renders Compose `Text`, not a `String`, and it formats a *single*
  morpheme (a search hit), not a whole decomposition. It stays on
  `MorphemeHumanReadableDescr`.
- Real clients of `MorphDecompFormatter` today: Guess Meaning's prompt;
  plausibly a CLI `--pipeline` text dump later; possibly the detail popup.
  With effectively one client now, the concrete formatter could come
  first and the abstract base be factored out when a second one appears.
- The three seams should **delegate to `MorphemeHumanReadableDescr`**
  internally -- do not add a 4th grammar-gloss implementation.
- Resolves the `MorphemeRow` name collision: the base consumes one shared
  per-morpheme struct (surface + id + `Morpheme?`) that replaces
  `org.iutools.llm.MorphemeRow`.
- Behaviour change to weigh: routing Guess Meaning through
  `MorphemeHumanReadableDescr` would add the grammar gloss to the prompt
  (currently omitted). Decide if that's wanted.
- Cheap intermediate step available on its own: make
  `guessMeaningSeedPrompt()`'s morpheme line call
  `MorphemeHumanReadableDescr` instead of its hand-rolled string (~20
  lines, no hierarchy, no collision to untangle) -- same grammar-gloss
  caveat.

### Phase 2 -- first `:data` generator: the LLM silver standard

- [ ] Add `:data-gen` (JVM-only module) **or** a `:cli` subcommand
      (`--silver-standard` / a `pipeline` mode) -- pick one; `:cli`
      subcommand is lighter and `:cli` already hosts `--pipeline` tooling.
- [ ] It reads `{word, decomps}` (JSON, one per line / STDIN), where the
      caller has already selected the top-N by the shared 5-key sort
      (that sort lives in Python `benoit_sort.py`, validated by
      `SortSyncTest` -- do not re-implement it in the generator).
- [ ] For each word it calls `:core`'s enrichment (dict + Hansard examples
      + prompt + Claude), emits the re-ranked decomps + a confidence per
      decomp on STDOUT.
- [ ] Response cache keyed by (word, decomp set, prompt); incremental
      checkpoint so a 10k run resumes.
- [ ] API key from `ANTHROPIC_API_KEY` / a flag, not hard-coded.
- [ ] Runs where the Hansard source is reachable -- possibly Alain's
      machine, not the devcontainer (see
      `doc/dev/plans/gov-nu-ca-crawling-investigation.md` and the concordancer
      Cloudflare block).
- [ ] Nothing in `apps/` depends on `:data-gen`.
- [ ] Gate: it compiles; `:cli:test` unchanged.

### Phase 3 -- consolidate data ownership (low priority, incremental)

- [x] FST slice done early (2026-09-04): `tools/fst/` -> `data/grammar/fst/`,
      generators included (the script<->lexc coupling made splitting them
      riskier than moving them together).
- [ ] Move `data/grammar/fst/hansard-cache/` to `data/corpus/hansard/`
      (corpus-derived, not grammar). Needs the same path-reference sweep in
      `:cli`'s `AnalyzerSpeedComparisonTest` / `SortSyncTest` and the
      Python scripts.
- [ ] Move the remaining passive artifacts under `data/` one at a time,
      each with a short contract doc: the gold standard(s) (today Kotlin
      fixtures in `:cli`), generated silver standards.
- [ ] Decide whether the non-generator analysis scripts under
      `data/grammar/fst/` (`histogram.py`, `reranker_*.py`,
      `full_corpus_check.py`, ...) should move back to `tools/` -- they
      consume data and produce reports rather than managing a data
      artifact. Deferred: the import coupling with the generators makes it
      non-trivial and it blocks nothing.
- [ ] `data/grammar/fst/scratchpad/` stays git-ignored and local.

### Phase 4 -- platform expansion (only when needed)

- [ ] `desktopApp`: the JVM target of `:composeApp` + Compose Desktop
      `nativeDistributions` packaging (macOS / Windows / Linux differ only
      in packaging, not code).
- [ ] `iosApp`: not started. (An earlier exploratory attempt on a
      now-deleted `ios-work` branch was dropped rather than merged --
      the codebase had moved on enough that starting fresh made more
      sense than reconciling it.)
- [ ] Web: explicitly out of scope (`AGENTS.md`). The only standing
      obligation is discipline -- keep `:core/commonMain` to pure Kotlin
      stdlib + kotlinx so a JS/Wasm target stays *possible* without a
      `:core` rewrite.

## Risks / notes

- KMP source-set and `expect`/`actual` wiring is fiddly; do Phase 1 in
  small commits, compiling after each.
- Do **not** rename Gradle modules -- the churn (settings file, build
  files, IDE run configs, the `android-studio-run-all-tests` skill's
  `iutools2.*` labels) is not worth it. Grouping is directory-level.
- Phase 1 depends on the `llm-guess-meaning-spike` branch state; sequence
  accordingly.
- The re-ranker experiment (`data/grammar/fst/`) does not need this migration to
  proceed -- only Phase 1 + Phase 2 unblock the silver-standard generator,
  and Phase 2 can start as a `:cli` subcommand without the full `:data`
  structure.
