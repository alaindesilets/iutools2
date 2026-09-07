# The analyzed-lexicon dataset ("the offline dictionary")

## What this is

A large-scale **morphologically analyzed lexicon of Inuktitut**: word form →
ranked decomposition(s) into morphemes (with the morphemes' glosses and
grammatical categories), mined from the Nunavut Hansard by running R2L
(Benoit Farley's Uqailaut, `:core` `MorphologicalAnalyzer_R2L`) over the
most frequent word forms, later enriched with plain-language meanings via
"Guess Meaning" (Claude).

Alain's framing (2026-09-06): this is **an artifact with value well beyond
iutools** -- openly-available morphologically analyzed Inuktitut data is
scarce, and this may be the single most reusable output of the whole
project. So it is treated as a first-class, versioned, publishable
**dataset**, not a build byproduct.

It is upstream of two things already designed elsewhere:

- the Morpheme Dictionary "example words" feature
  (`doc/morpheme-dictionary-examples-design.md`) -- its `morphemeId → [example
  words]` index is derived from this lexicon;
- the decomposition re-ranker
  (`doc/dev/plans/reranker-objectives-and-analyzers.md`) -- an R2L re-ranker
  would improve which decomposition is ranked first in this lexicon.

## Status

**S1 done and published, 2026-09-07.** Word list = the 100 000 most
frequent Hansard syllabic word forms; mined with R2L; published as GitHub
release `analyzed-lexicon-v1` on `alaindesilets/iutools2`. Everything about
S1 — the data, how to fetch it, how it was made, how to rebuild it — now
lives in [`data/lexicon/decompositions/`](../../../data/lexicon/decompositions/)
(`README.md`, `datapackage.json`, `fetch.sh`, `regenerate.sh`). This
planning doc is now about **S2 and S3** (below) and the design rationale
behind S1.

---

## Decisions made in the discussion

- **Analyzer: R2L, not the FST.** R2L's candidate lists are short and every
  decomposition it emits is grammatically plausible (its `correct@1` /
  R-precision are 100% by construction). The FST over-generates (median 6,
  mean 20, max 856 decomps/word) and emits many analyses that are not valid
  for the specific word. R2L's only weakness is `reference@1` -- which of
  its readings is the attested/idiomatic one -- and v1 does not depend on
  that (see the gate below). The design doc's own measurement had R2L give
  *higher* morpheme coverage than the FST under the strict gate (741 vs 683
  morphemes for "M in every decomp"), precisely because it produces fewer
  decomps per word.
- **Include every R2L decomposition per word**, not just the top-1 or the
  gold `all_correct_decomps` subset.
- **The R2L re-ranker is a v2 coverage lever, not a v1 dependency.** The
  entire re-ranker experiment so far is built on *FST* candidate lists; an
  R2L re-ranker does not exist and is a real build effort (retarget the
  feature table, retrain, validate). v1 ships without it.
- **Home: `data/lexicon/decompositions/`.** The in-repo half (README,
  descriptor, scripts) is tracked, modelled on `data/grammar/gold-standard/`.
  The big output file is not in git -- see "How S1 is stored" below.

---

## Open question: which words, and how many?

Tied to token-occurrence coverage. Measured this session from the repo's
Hansard corpus (`tools/hansard-corpus/.../NunavutHansard.iu`, Parallel
Corpus 3.0.1; the frequency computation reproduces the existing
`data/grammar/fst/hansard-cache/top10k_words.txt` exactly):

| top-N word types | % of token occurrences | min frequency at N |
|---:|---:|---:|
| 1,000 | 50.5% | 561 |
| 2,000 | 55.3% | 259 |
| 5,000 | 61.1% | 94 |
| **10,000** | **65.2%** | 44 |
| 20,000 | 69.0% | 21 |
| 30,000 | 71.2% | 14 |
| 50,000 | 73.9% | 8 |
| 100,000 | 77.5% | 4 |
| 200,000 | 81.1% | 2 |

Corpus: ~7.75M syllabic word tokens, **1.56M distinct types**, type/token
ratio **0.20**, **80.6% of types are hapax** (16.2% of all tokens). This is
the "polysynthesis tax" -- in English a parliamentary corpus's top 10K
covers ~90-95% of tokens; Inuktitut builds words productively, so a
word-form frequency list is structurally far less efficient.

**Is the uncovered ~35% just other inflections of already-captured lexemes?**
Partly, near the cut, not deeper. Prefix-overlap proxy (roots/derivation
are prefixal in Inuktitut, inflection suffixal), % of a rank band's tokens
whose form shares a ≥K-syllabic-char prefix with some top-10K word:

| rank band | % tokens | LCP≥5 | LCP≥7 | LCP≥9 |
|---:|---:|---:|---:|---:|
| 10k–20k | 3.9% | 60% | 28% | 9% |
| 20k–50k | 4.9% | 56% | 24% | 7% |
| 50k–100k | 3.6% | 51% | 20% | 6% |
| 200k–500k | 5.2% | 41% | 14% | 3% |
| 500k+ | 13.7% | 39% | 12% | 3% |

Median form length (syllabic chars): top-10K **7**, ranks 10k–50k **8**,
ranks 100k–500k **11**. So the near-tail is mostly morphological neighbours
of captured words (but only ~25-28% a *strong* stem match -- a real share
are new stems); the deep tail, where most of the uncovered mass actually
lives, is longer, morphologically heavier new constructions plus noise
(typos, proper names, English borrowings). The prefix test is a proxy only
-- a proper answer needs decomposing the tail (blocked on syllabic→Roman
transliteration for the FST, or ~1-2h of R2L).

**What actually matters for the downstream use** is morpheme-inventory
coverage: how many of the ~5211 morpheme IDs get ≥1 good example. The
example-words design measured the 10K cache → 635 morphemes under the
strict gate. A larger mined corpus fills the mid-frequency morpheme tail.

**Cost.** R2L ≈ 79 ms/word (789 s for ~10K, per the example-words design).
So 50K ≈ ~1 h, 100K ≈ ~2 h of offline analysis -- fine for a one-time run.

**Decided for v1:** 100K, word-list source = the Hansard corpus itself. A
gov.nu.ca-frequency word list stays a possible future input (gov.nu.ca is
Cloudflare-blocked from the devcontainer; see
`doc/dev/plans/gov-nu-ca-crawling-investigation.md`).

---

## The pipeline is a chain of derived artifacts

| stage | output | reproducible? |
|---|---|---|
| **S1 · analyzed-lexicon** | word → ranked decomps (R2L, later + R2L re-ranker) over top-N words | ~yes but **expensive** (~1-2 h) and **not strictly deterministic** -- R2L applies a timeout (`timedOut` / `elapsedMSecs` fields in the current cache), so words that time out can differ run to run |
| **S2 · morpheme→example-words index** | invert S1 + eligibility gate + score + root-balance + truncate | **yes, cheap** -- a pure function of S1. This is the Morpheme Dictionary pass-2 input. |
| **S3 · glossed-lexicon** | decomp → plain-language meaning, "Guess Meaning" (Claude) over S1 | **no** -- costs money, different text each run. Its output *is* source-of-truth for anything downstream. |
| shipped | small generated Kotlin (~270 KiB) from S2 (later S3) | n/a -- plain git, it's tiny and goes in the APK |

**Consequence:** "track only the recipe" does not work for the whole chain.
S3's recipe does not reproduce its output, and its input (S1 vN) is itself a
produced artifact. Each stage's output is a first-class versioned artifact;
each stage's *recipe* is a lockfile/manifest that pins its upstream artifact
versions + hashes, plus (for S3) the model, prompt hash, params and date.
For non-reproducible stages the output hash is pinned and a re-run is an
explicit version bump.

Rule of thumb:
- reproducible & cheap (S2) → gitignore the output, track the script,
  regenerate on demand;
- reproducible but expensive (S1) → track the script + cache the output in a
  retrievable store + checksum; regenerate only when inputs change;
- non-reproducible (S3) → the output is data; store and version it; the
  recipe is provenance, not a reproduction guarantee.

---

## How S1 is stored

DagsHub + DVC were evaluated and dropped (2026-09-07): the old
`iutools/iutools-data` repo was un-cloneable and un-privatable, and Alain
lost confidence in the service. S1 compressed is only ~15 MB, so an object
store / DVC was overkill anyway.

**Mechanism actually used** (see `data/lexicon/decompositions/README.md`):

- The dataset file ships as a **GitHub release asset** on
  `alaindesilets/iutools2` (tag `analyzed-lexicon-v<N>`, immutable, never
  overwritten). Same pattern as the app's prebuilt Hansard SQLite index
  (`tools/README-hansard.md`, `NunavutHansardDownloader.kt`).
- In git: `datapackage.json` (a Frictionless Data Package descriptor +
  W3C-PROV provenance) pins the release version, the file's SHA-256 and
  size, the field schema, the source corpus, and the exact generating
  command. It is the equivalent of a DVC pointer file, just human-readable.
- `fetch.sh` = the equivalent of `dvc pull` (download + verify checksum);
  `regenerate.sh` = rebuild from the corpus. No DVC, no second account.
- `git checkout <old commit>` + re-run `fetch.sh` gives the data version
  that went with that code (the old commit's `datapackage.json` points at
  the old release tag).

Discoverability: a breadcrumb in `data/lexicon/README.md`, the release
shows on the repo's Releases page, and a Zenodo↔GitHub webhook can mint a
DOI per release later without changing anything for consumers.

---

## Licensing

- **S1 (decompositions): resolved.** The Nunavut Hansard corpus is
  CC-BY-4.0 and explicitly permits derivatives; S1 is published under
  CC-BY-4.0 with attribution + the Joanis et al. LREC 2020 citation (see
  `datapackage.json` and the v1 release notes). What S1 publishes -- a
  unigram frequency table + per-word morphological analyses -- is not a
  corpus redistribution. Morpheme glosses come from the iutools linguistic
  data, already public under this repo's `LICENSE.md`.
- **S3 (Guess Meaning text): still open.** Claude-generated; note
  Anthropic's terms on model output (user owns outputs, but flag it).
  Decide before publishing an S3-glossed dataset.
- **Not in scope, ever:** non-Schneider Living Dictionary content.

---

## Sequencing

1. **S1 v1 -- DONE (2026-09-07).** 100 000 Hansard word forms, R2L, no
   re-ranker. The R2L 20 s per-word timeout was *kept*, not disabled: ~192
   words that time out (and 1 that hangs) make the run non-deterministic at
   ~0.2 %, which is documented and accepted -- the artifact is stored, not
   regenerated. Published as GitHub release `analyzed-lexicon-v1`.
2. **S2 -- next.** Invert S1 into a `morphemeId → [example words]` index
   (eligibility gate `fitness == 1.0 && n ≤ 7`, score, root-balance,
   truncate). Pure cheap function of S1 -- gitignore the output, track the
   script. Ship Morpheme Dictionary pass 2.
3. **v2.** Build the R2L `reference@1` re-ranker (see
   `reranker-objectives-and-analyzers.md`), re-mine S1 with it, move S2 to
   the relaxed "M in the top-k re-ranked decomps" gate for more coverage.
4. **S3.** Add the Claude-generated glossed layer; resolve S3 licensing
   first.

Related: `doc/dev/plans/reranker-objectives-and-analyzers.md`,
`doc/morpheme-dictionary-examples-design.md` (note: that file still refers
to `tools/fst/` -- now `data/grammar/fst/`),
`doc/dev/plans/gov-nu-ca-crawling-investigation.md`.
