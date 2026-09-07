# `:fst` -- the finite-state (HFST) morphological analyzer

`org.iutools.morph.fst.MorphologicalAnalyzer_FST` plus the vendored
pure-Java optimized-lookup reader it sits on
(`src/main/java/net/sf/hfst/` -- see its `VENDORED.md`). It reads the
compiled Inuktitut transducer (`data/grammar/fst/lexicon-analyser.hfstol`)
in-process, no subprocess, so the same analyzer runs on a dev machine, in
the CLI, and on Android.

## Why this is its own module and not part of `:core`

`MorphologicalAnalyzer_FST` is analyzer logic and by the litmus test in
[`doc/dev/plans/module-architecture-migration.md`](../doc/dev/plans/module-architecture-migration.md)
it belongs in the **`core` group** -- a shipped app calls it at runtime.
It is carved out into a separate module for one purely mechanical reason:
the vendored `net.sf.hfst` reader is Java source, and `:core`'s Android
compilation uses `com.android.kotlin.multiplatform.library`, which does
not compile Java source. So `:fst` is a plain (non-KMP) JVM/Android
library that both `:cli` (JVM) and `:composeApp` (Android) can depend on.

## Why it is NOT folded into `:composeApp`

Two reasons:

- It is domain logic, not UI. `AGENTS.md`: `:cli` and `:composeApp`
  depend on `:core` and *add no analyzer logic of their own*. Moving the
  FST analyzer into an app module goes the wrong way.
- `:cli`'s non-negotiable regression gate depends on it
  (`MorphologicalAnalyzer_FST__AccuracyTest`, plus `SortSyncTest` and
  `AnalyzerSpeedComparisonTest`). `:cli` is a JVM-only module and cannot
  depend on `:composeApp`, an Android *application* module.

For the same reasons, when an `apps/` directory is eventually created,
`fst/` stays at the top level next to `core/`, never under `apps/`.

## Eventual target: absorb into `:core`

The clean end state is no `:fst` module at all: port the ~13 vendored
`net.sf.hfst` Java files to Kotlin, then `MorphologicalAnalyzer_FST` and
the reader move into `core/jvmMain` + `core/androidMain` source sets. A
comment in `MorphologicalAnalyzer_FST.kt` already anticipates the
`core/src/jvmMain/java/net/sf/hfst/` path.

This is a separate, riskier task -- porting a binary-format parser, to be
revalidated byte-for-byte against native `hfst-lookup` (see
`VENDORED.md` -> "Validation") -- and it does not block the
`:composeApp` -> `:core` consolidation that is Phase 1 of the migration
plan. Until it happens, `:fst` stays as it is.

## See also

- `build.gradle.kts` (this directory) -- the same rationale, in brief.
- `src/main/java/net/sf/hfst/VENDORED.md` -- upstream source, license,
  local modifications, and the validation test.
- `data/grammar/fst/` -- the lexc sources, generators, and compiled
  transducer this module reads.
