# Vendored: net.sf.hfst (hfst-optimized-lookup-java)

A pure-Java reader for HFST optimized-lookup (`.hfstol`) transducers. Used by
`org.iutools.morph.fst.MorphologicalAnalyzer_FST` to run the finite-state
Inuktitut analyzer (`tools/fst/lexicon-analyser.hfstol`) without shelling out
to the native `hfst-lookup` tool -- so the same analyzer can eventually run
on Android/iOS, not just a dev machine.

## Source

- Repository: https://github.com/hfst/hfst-optimized-lookup
- Path: `hfst-optimized-lookup-java/src/net/sf/hfst/`
- Commit: `938edf4075ab1e3a08017750d80d32e903cf5376` (2018-02-27; the Java
  reader's own last change was 2016-10)
- License: Apache License 2.0 (see `LICENSE` in this directory)

The upstream project is effectively frozen (no Maven/Gradle artifact, Ant
build only, no commits in years), so the sources are copied in here verbatim
rather than added as a dependency. The `.hfstol` binary format is itself
stable, so "frozen" is acceptable.

## Validation

`cli`'s `MorphologicalAnalyzer_FST__AccuracyTest` confirms this reader
returns, for every word in the gold standard, the **same set** of
decompositions the native `hfst-lookup` produces for the same transducer
(verified: 0 words out of 922 differ). Upstream has open bug reports about
`hfst-ol.jar` disagreeing with `hfst-lookup` on *some* transducers
(hfst-optimized-lookup issue #3, SourceForge bug #283) -- that test is what
proves those bugs don't affect ours. Re-run it after rebuilding the
transducer.

The one thing this reader does NOT reproduce is `hfst-lookup`'s path
ordering (it traverses the automaton differently), so "which analysis comes
first" differs. That doesn't matter here: neither tool's raw order is
meaningful, and ranking is applied separately downstream.

## Local modifications

- `Transducer.java`: the abstract `analyze(String)` method was widened from
  package-private to `public` (both concrete overrides,
  `WeightedTransducer` / `UnweightedTransducer`, were already public).

Nothing else was changed. To re-sync with upstream, re-copy the files and
re-apply that one change.
