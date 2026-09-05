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

### What does NOT move

`:core`'s embedded linguistic-data CSVs (`core/.../dataCSV/generated/`)
stay where they are. Their embedding as generated Kotlin source is a
deliberate choice for iOS/Android resource-loading portability -- see the
file headers. `:core` does **not** gain a dependency on `:data`.

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

Prerequisite: reconcile with the `llm-guess-meaning-spike` branch -- the
Guess Meaning code is not on `main` yet. Either land that branch first, or
do this extraction as part of landing it.

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
- [ ] `iosApp`: folded into the existing `ios-work` branch effort.
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
