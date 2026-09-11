# The R2L reference@1 re-ranker

This directory holds the shipped artifact of the "reference@1" re-ranker: a
small model that re-sorts R2L's own candidate decompositions of a word so
that the one actually attested by Hansard usage lands first more often than
R2L's native order manages on its own. Background and how this model was
trained (a pure-stdlib GBDT, cross-validated against the gold standard) is
in `data/grammar/fst/reranker-experiment.md` and
`doc/dev/plans/offline-dictionary-generation.md` (Part 3 "Cheap layer").

- `reranker_model.json` -- the model `org.iutools.morph.rerank.ReferenceReranker`
  (in `:core`) actually loads at runtime, via `:core`'s classpath (see
  `core/build.gradle.kts`). A list of 150 decision trees plus the ordered
  feature list they were trained on. Regenerate with
  `data/grammar/fst/export_kotlin_reranker.py`.
- `reranker_golden_fixture.json` -- test-only data: a sample of words with
  their full candidate lists, computed feature vectors, and model scores,
  used by `ReferenceRerankerParityTest` (in `:cli`'s test source set) to
  assert the Kotlin port computes byte-for-byte the same features and
  scores as this Python model. Not shipped in the app -- wired into
  `:cli`'s test resources only (see `apps/cli/build.gradle.kts`).

## Why trees with raw thresholds, not bin indices

The Python GBDT trains against quantile-*binned* feature values (for
training speed), so a raw tree from `reranker_gbdt.py` stores, per split,
a *bin index* -- meaningless without also shipping the exact bin-edge
table it was computed against. `export_kotlin_reranker.py` converts every
split to the equivalent **raw feature-value threshold** before writing
`reranker_model.json` (validated bit-for-bit against the original
bin-indexed model on every training row before being trusted) so the
Kotlin side needs no binning logic at all -- just
`if (x[feat] < thr) left else right`.

## Why no bigram / backoff / r2l feature groups

The full GBDT (all `reranker_cv.FEATURE_GROUPS`) reaches ~90.8% fair
reference@1 (nested CV) on R2L's own candidate lists. Dropping `r2l`
(circular here -- a candidate already IS an R2L decomp, so "does R2L agree
with itself" is near-redundant with `rank_current_sort`) and `backoff` /
`bigram` (two more corpus-derived feature groups) cost about 0.4 point in
flat-CV testing -- and mean the Kotlin port needs **zero new corpus-derived
data tables**: every kept feature is either a pure function of `(word,
decomp)` (`shape`, `cat`, `surface`, `relative`, `weight`) or already
exactly `org.iutools.morph.MorphemeFrequencyPrior` (`freq` -- verified
byte-for-byte identical to this model's training data, all 1259 keys, no
new table to keep in sync). Given the project's standing concern about
Python/Kotlin logic drifting apart, trading 0.4 point of accuracy for zero
new data-fidelity surface area was judged worth it.
