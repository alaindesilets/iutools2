# Morpheme Dictionary — corpus example words (design in progress)

## Status

**Design discussion, paused.** Pass 1 of the Morpheme Dictionary (morpheme
lookup + human-readable grammar/meaning) is implemented on branch
`iutools2-Android-UI` (see the `project_morpheme_dictionary_status` memory
note and commit `d68253c`). This document records where the design of
pass 2 — real corpus **example words** for each morpheme — stands after a
design session, so it can be picked up later.

**Why paused:** the eligibility rule we landed on depends on how well the
FST's decompositions can be ranked, and the other agent's learned
re-ranker (reported at ~90 % first-decomposition-correct / ~98 %
correct-in-top-3, vs the current production sort's 74 % / 86 %) is not yet
pushed. Once it is, its per-decomposition scores plug straight into the
evaluation harness described below, the projected numbers here get
replaced with measured ones, and the gate is finalised. **Resume at the
"Open items" section.**

## What pass 2 is

The original iutools Java "Morpheme Dictionary" web app has two halves.
Pass 1 (done) is morpheme → description. Pass 2 is morpheme → a short list
of real words from a corpus that exemplify the morpheme in use — e.g. for
`mut/tn-dat-s`, words like `iglumut`, `nunamut`. The app shows these under
each morpheme entry.

## How the Java original picks example words

Reconstructed by reading the current and historical sources in
`iutools/iutools` on GitHub:

- `iutools-core/src/main/java/org/iutools/morphemedict/MorphemeDictionary.java`
- `.../morphemedict/MorphWordExample.java`
- `.../morphemedict/ExamplesRootsBalancer.java`
- `.../morphemedict/MorphDictionaryEntry.java`
- test: `.../src/test/java/org/iutools/morphemedict/MorphemeDictionaryTest.java`
- older equivalents pre-2022 under `org.iutools.morphemesearcher.*`
  (`MorphemeSearcher.java`, `ScoredExample.java`) — same scoring formula,
  root-balancing done via "bins" instead of `ExamplesRootsBalancer`.

Pipeline, per matching morpheme id (`bestExamplesForMorphID`):

1. **Candidate pool** — `corpus.wordsContainingMorpheme(id, 100, "frequency:desc")`.
   Elasticsearch query on `morphemesSpaceConcatenated` (the space-joined
   morpheme sequence of a decomposition); returns the 100 most
   corpus-frequent words whose sample decompositions (`decompsSampleSize`
   = 10) contain the id as a substring. Corpus membership is the hard
   precondition; corpus frequency is the primary order.

2. **Score** — `MorphWordExample.getScore()`:
   ```
   score = 10000 * analysesFitness + frequency
   analysesFitness = (# sample decomps containing the morpheme) / (total # sample decomps)
   ```
   One 0–1 criterion (fitness) heavily weighted so it dominates, with raw
   corpus frequency as the in-band tie-break. The `10000` does **not**
   strictly guarantee dominance against large raw frequencies — a known
   soft spot.

3. **Sort** — `MorphExampleComparator`: score desc, then shorter word
   first, then case-insensitive alphabetical.

4. **Root balancing** — `ExamplesRootsBalancer.balance()`: reorders so the
   top of the list isn't all inflections of one root. Iterative passes
   over the score-sorted list; an example is added only if its root's
   count is not currently the maximum among already-picked roots,
   otherwise deferred to the next pass; if a whole pass adds nothing, the
   first deferred one is force-added. Stops at `maxExamples` (default 20 =
   `nbWordsToBeDisplayed`). A word's "root" = the first morpheme of its
   top sample decomposition.

The original has **no** minimum-fitness floor — it takes the top 20 by
score regardless — and **no** check that the example word (or its root) is
itself in any dictionary. Multi-criteria weighting with powers of ten
(1/10/100/1000), if it ever existed in this feature, predates the
2020-12 GitHub history; every version on GitHub uses the single
`10000*fitness + frequency` formula.

## Port constraint and data substitute

`:core` has no `CompiledCorpus` / Elasticsearch, and won't — out of scope.
The substitute for the candidate pool is
`tools/fst/hansard-cache/top10k_words_benoit_decomps.jsonl`: the 10,000
most frequent word forms in the full Nunavut Hansard, each with its list
of decompositions. Frequency itself is not stored, only rank (line
order) — a synthetic `freq = N - rank` stands in for the tie-break.

Plan: invert `word → decompositions` into `morphemeId → [words]` **offline,
at generation time**, apply the scoring/gate/balancing/truncation, and
embed only the capped result as generated Kotlin (same approach as
`MorphemeFrequencyPrior`). Runtime does a `Map` lookup and nothing else.

## Design converged on so far

### Shipped artifact

- **Encoding A**: `morphemeId <TAB> word1,word2,…` (Roman only), pre-scored,
  pre-balanced, truncated to 20. ~270 KiB / ~1900 lines. Syllabics
  reconstructed at display via `Roman.transcodeToSyllabics` (already in
  `commonMain`).
- Kotlin/JVM string literals cap at 65535 bytes UTF-8 — the generator must
  split the blob into ~5 chunks joined at load (`MorphemeFrequencyPrior`,
  19 KiB, doesn't hit this; 270 KiB will).
- **Frozen at generation for v1**: the ordered ≤20 list is stored as-is;
  runtime only displays + transcodes. The root-balancing algorithm is
  therefore fixed at generation time — acceptable for v1.
- Alternatives measured but not chosen: encoding B (Roman+syllabics,
  696 KiB), encoding C (per-word rank/fitness/root kept for runtime
  re-scoring, cap 100, 1.3 MiB).

### Eligibility gate (the precision lever)

A word W is eligible as an example of morpheme M iff, over W's sample
decompositions (sample capped at 10, Java-style):

- **`fitness == 1.0`** — M appears in every sample decomposition of W, **and**
- **`n_decomps <= 7`** — W has at most 7 decompositions.

Rationale for the second clause — **a word with many decompositions is
inherently ambiguous, and M surviving all of them can be a systematic
artifact** (the FST over-attaching a common affix). Measured precision of
`fitness == 1.0` by decomposition count `n` (gold standard, FST decomps,
production sort):

| n | pairs | precision |
|---|---|---|
| 1 | 199 | 99.5 % |
| 2 | 108 | 100 % |
| 3–4 | 142 | 100 % |
| 5–7 | 180 | 100 % |
| **8–10** | 453 | **90.9 %** |
| any | 1082 | 96.1 % |

Small `n` is the *safest* case, not the riskiest — so the guard caps `n`,
it does not floor it. `fitness >= 0.8` shows the same shape (n∈[3,4]:
100 %, n∈[8,10]: 81.7 %).

### Score (ordering within a morpheme's list)

Powers-of-ten weighted, every criterion in `[0, 1)`:

```
score = 1000 * fitness
      +  100 * rootHasDefinition        (0/1: W's root has a real gloss; French freMean in a French UI)
      +   10 * normalizedFrequencyRank
      +    1 * (1 - n_decomps / 10)      (cleaner decomposition ranked higher)
```

Or a lexicographic tuple sort `(fitness, rootHasDefinition, freqRank,
shortness)` to sidestep weight calibration — each criterion must be
strictly `< 1` for the powers-of-ten trick to be safe.

`rootHasDefinition` is the "dictionary-backed" secondary criterion: the
Java original never checks it, but ~24 % of `RootsSpalding.csv` roots have
no French gloss and some have thin English ones; an example built on a
well-defined root is more useful to a learner. Implemented as a tie-break
so it never distorts the fitness/frequency ordering.

### Then

Root balancing (port `ExamplesRootsBalancer` + its test
`test__balanceExamplesByRoots`, which transposes directly), truncate to 20.

### Optional second tier

If a morpheme has fewer than N eligible examples, top up with
`fitness >= 0.8, n <= 7` words (~92 % precision on gold), shown below /
de-emphasised.

## Measured numbers (this session)

All on the `--fair` gold standard (922 words) and/or the 10K Hansard cache,
FST decompositions, current production sort (`benoit_sort.py` +
frequency tie-break). **These are the baseline the re-ranker is expected
to improve on.**

**FST decomposition speed** — 9,999 words in **17.9 s** wall-clock (native
`hfst-lookup`, batched) vs 789 s summed for R2L; ~44× faster. The Java
in-process reader would be ~2–5× slower than native, still ~1–2 min for
10K. FST yields ~28.6 deduped decomps/word vs R2L's ~15.2.

**P@k for the production sort** (some top-k analysis == full gold id-set):

| P@1 | P@2 | P@3 | P@4 | P@5 |
|---|---|---|---|---|
| 74.3 % | 81.2 % | 85.6 % | 88.6 % | 90.1 % |

**Gate precision** — P(M in W's true decomposition | rule) on gold:

| rule | example pairs | precision | words with ≥1 ex |
|---|---|---|---|
| M in top-1 | 2427 | 88.8 % | 922 |
| M in any of top-5 | 5832 | 41.2 % | 922 |
| M in all top-3 | 1427 | 90.9 % | 781 |
| M in all top-5 | 1264 | 92.9 % | 709 |
| M in every decomp | 968 | 99.9 % | 607 |
| **M in every decomp, n ≤ 7** | 629 | **99.84 %** (1 FP) | — |

**Coverage** — denominator `LinguisticData.allMorphemeIDs()` = **5211**;
gate `fitness == 1.0 && n <= 7`, mined over the 10K:

| | count | % of 5211 |
|---|---|---|
| ≥ 1 example | 635 | **12.2 %** |
| ≥ 5 examples | 252 | 4.8 % |
| **no example** | 4576 | **87.8 %** |

Against the ~1930 morphemes actually attested in the Hansard top-10K:
635 / 1930 ≈ 33 % covered. The empty 88 % is dominated by the long tail
(rare roots, rare ending combinations); the high-frequency affixes and
roots a learner actually looks up are in the 635, mostly in the 252.

**False positives in shown examples** — 1 / 629 eligible gold pairs =
**0.16 %** (~1 bad example per 600), clean across every `n` bucket 1–7.
Top-5 examples are a frequency-favoured subset of eligible pairs, so their
FP rate is ≤ this.

**Other coverage points (10K, various gates):**

| gate | ≥1 / ≥5 / fills-cap-20 |
|---|---|
| `k==n`, any n | 796 / 357 / 121 |
| `k==n && n<=7` | 635 / 252 / 67 |
| `k==n && n<=5` | 604 / 228 / 55 |
| `fitness>=0.8`, any n | 856 / 386 / 143 |
| `fitness>=0.8 && n<=7` | 652 / 255 / 71 |

Relaxing `fitness == 1.0` to `>= 0.8` buys almost nothing (635 → 652
morphemes) for ~8 points of precision — not worth it. The real coverage
lever is a **larger mined corpus** (50K–100K word forms; ~1–3 min of FST
analysis offline), which fills the mid-frequency tail specifically — the
shipped index barely grows because two ceilings bound it (the ~5211
finite inventory, and the per-morpheme cap of 20).

## Open items — resume here when the re-ranker is pushed

1. **Re-measure the gate with the re-ranker's ordering.** The evaluation
   harness (built this session, currently in scratch) plugs the ranker in
   at one point: `sort_with_frequency_tiebreak` in
   `tools/fst/benoit_sort.py`. Swap that for the re-ranker's scored order
   and re-run the precision/coverage tables above.

2. **The gate almost certainly changes.** At P@3 = 98 %, the strict
   "M in every decomp, n ≤ 7" gate is overkill — it existed only to
   compensate for a weak sort. Projected replacement:
   **"M in all of the top-3 re-ranked decomps"** → ~97–98 % precision at
   ~883 morphemes covered (17 % of 5211, ~46 % of attested) — a large
   coverage gain over 635 at near-equal precision. Even looser:
   "M in top-1 re-ranked && fitness ≥ 0.8 && n ≤ 7" → likely ~1000+
   morphemes at ~93–95 %. **These are projections; measure them.**

3. **Metric note.** P@1/P@3 are whole-decomposition-correct. Example
   mining only needs morpheme-level correctness ("is M in the true
   parse"), a generally easier target — effective gate precision should
   be ≥ the P@k figures.

4. **Sequencing.** Pass 2's generation step consumes the re-ranker's
   ordering, so pass 2 depends on the re-ranker being finalised and its
   scoring reproducible offline in `tools/fst/`.

5. **Nothing new ships.** The re-ranker runs offline at generation; the
   example lists stay frozen in the APK. It becomes a runtime/shipping
   concern only if the screen ever re-ranks live — not v1.

6. **Analyzer choice for the cache.** FST vs R2L for the mined
   decompositions is still open — decide on decomposition quality (top-1
   correctness, spurious-analysis noise), not speed. Numbers above are
   FST. R2L (the existing cache) gave marginally higher coverage under
   the strict gate (741 vs 683 morphemes for "M in every decomp") because
   it produces fewer decomps per word.

7. **Then:** finalise the gate, regenerate the inverted index, port
   `ExamplesRootsBalancer` + `test__balanceExamplesByRoots`, wire the
   generated Kotlin into `:core`'s `MorphemeDictionary`, and surface the
   examples in `MorphemeDictionaryScreen.kt` (with `values/` + `values-fr/`
   strings, including the "no example found in the Hansard corpus"
   message for empty entries).
