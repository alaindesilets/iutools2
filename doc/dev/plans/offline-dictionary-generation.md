# Plan: generate an offline dictionary

Pre-compute the information the app shows on **Word Lookup** — the word's
decomposition(s), a plain-language meaning, and (for the Morpheme
Dictionary) whether a decomposition reflects real usage — so that every
user gets it without a live analyzer or LLM call.

Three things make this worth a dedicated pipeline:

1. **The analyzer over-generates in the idiomatic sense.** Every
   decomposition R2L emits is *grammatically* valid, but for a given
   surface word usually only one reflects how the word is actually used.
   Telling them apart needs usage evidence, not grammar.
2. **Guess Meaning needs a Claude API key.** Pre-computing it offline lets
   users without a key still see a meaning.
3. **A morphologically analyzed Inuktitut lexicon is scarce and reusable.**
   The word→decomposition data (stage S1 below) is treated as a
   first-class, versioned, **publishable dataset**, not just a build input
   — it has value well beyond iutools.

This doc merges three earlier notes: `analyzed-lexicon-dataset.md` (the
dataset), `offline-dict-stem-base.md` (the coverage strategy), and the
original LLM-pass plan.

## Consumers

- **Word Dictionary** — pre-computed Guess Meaning, so users without a
  Claude API key still get a meaning.
- **Morpheme Dictionary** — the **"good example words"** gate
  (`doc/morpheme-dictionary-examples-design.md`): an example of morpheme M
  must come from a decomposition that both contains M *and* reflects real
  usage.
- **Word Lookup** — a decomposition lookup: check a pre-computed table
  first, fall back to running R2L live only for words not in it (live R2L
  is ~0.5–2 s/word past the top few thousand).

---

# Part 1 — the data spine: the analyzed lexicon

`word form → ranked decomposition(s) into morphemes`, mined by running R2L
(Benoit Farley's Uqailaut, `:core` `MorphologicalAnalyzer_R2L`) over a
large word list, later enriched with glosses and idiomaticity flags.

## Stages

| stage | output | reproducible? |
|---|---|---|
| **S1 · analyzed lexicon** | word → every R2L decomposition, R2L's native rank order | ~yes but **expensive** (~1–2 h) and **not strictly deterministic** — R2L applies a per-word timeout, so words near it flip run to run (~0.2%) |
| **S2 · morpheme → example-words index** | invert S1 + eligibility gate + score + root-balance + truncate | **yes, cheap** — a pure function of S1; the Morpheme Dictionary pass-2 input |
| **S3 · glossed + idiomaticity-annotated lexicon** | per decomposition: plain-language gloss (Guess Meaning) + `usage_attested` verdict, from an LLM pass over S1 | **no** — costs money, different text each run; its output *is* source-of-truth downstream |
| shipped | small generated Kotlin (~270 KiB) from S2 (later S3), plus a Word-Lookup stem index | n/a — small, goes in the APK |

**Tracking consequence:** "track only the recipe" fails for the whole
chain. S3's recipe doesn't reproduce its output, and its input (S1 vN) is
itself produced. So: reproducible-and-cheap (S2) → gitignore the output,
track the script; reproducible-but-expensive (S1) → track the script +
store the output as a checksummed release asset; non-reproducible (S3) →
the output is data, store and version it, the recipe is provenance only.

## S1 — done and published (2026-09-07)

`analyzed-lexicon-v1`: the 100 000 most frequent Nunavut Hansard syllabic
word forms, every R2L decomposition of each, native rank order, strict vs.
"guessed dropped final consonant" readings flagged. No re-ranker (see
Part 3). Everything about it — the data, how to fetch it, how it was made,
how to rebuild it — lives in
[`data/lexicon/decompositions/`](../../../data/lexicon/decompositions/).

**Storage.** DagsHub + DVC were evaluated and dropped (un-cloneable old
repo, can't privatise on the free plan, lost confidence). S1 compressed is
only ~15 MB. Mechanism used instead:

- the dataset file ships as a **GitHub release asset**
  (`analyzed-lexicon-v<N>`, immutable, never overwritten) — same pattern as
  the app's prebuilt Hansard SQLite index (`tools/README-hansard.md`,
  `NunavutHansardDownloader.kt`);
- in git, `datapackage.json` (a Frictionless Data Package descriptor +
  W3C-PROV provenance) pins the release version, SHA-256, size, field
  schema, source corpus, and generating command — the human-readable
  equivalent of a DVC pointer file;
- `fetch.sh` = `dvc pull` (download + verify checksum); `regenerate.sh` =
  rebuild from the corpus. `git checkout <old commit>` + `fetch.sh` gives
  the data version that went with that code.

**Non-determinism.** The R2L 20 s per-word timeout was *kept*, not
disabled: ~192 words that time out (and 1 that hangs) make a rebuild differ
by ~0.2% and never byte-identical. So S1 is *stored*, not regenerated on
demand; a rebuild is "equivalent, not identical".

## Which words, and how many?

Token-occurrence coverage of the top-N Hansard word types (measured from
Parallel Corpus 3.0.1; reproduces `data/grammar/fst/hansard-cache/top10k_words.txt`):

| top-N types | % of tokens | min freq at N |
|---:|---:|---:|
| 1,000 | 50.5% | 561 |
| **10,000** | **65.2%** | 44 |
| 50,000 | 73.9% | 8 |
| 100,000 | 77.5% | 4 |
| 200,000 | 81.1% | 2 |

Corpus: ~7.75M syllabic tokens, **1.56M distinct types**, **80.6% of types
are hapax** (16.2% of tokens). This is the "polysynthesis tax" — English
parliamentary top-10k covers ~90–95% of tokens; Inuktitut builds words
productively, so a word-form frequency list is structurally far less
efficient. **The coverage lever is not more forms — it is stems (Part 2).**

Cost: R2L ≈ 79 ms/word → 100K ≈ ~2 h, 500K ≈ ~11 h — fine for a one-time
run.

## Licensing

- **S1: resolved.** Nunavut Hansard corpus is CC-BY-4.0 and explicitly
  permits derivatives; S1 ships under CC-BY-4.0 with attribution + the
  Joanis et al. LREC 2020 citation (in `datapackage.json` and the release
  notes). A unigram frequency table + per-word analyses is not a corpus
  redistribution. Morpheme glosses come from the iutools linguistic data,
  already public under this repo's `LICENSE.md`.
- **S3 (Guess Meaning text): open.** Claude-generated; note Anthropic's
  terms on model output. Decide before publishing an S3 dataset.
- **Never in scope:** non-Schneider Living Dictionary content (see "Rights"
  in Part 3).

---

# Part 2 — coverage strategy: analyze a large N, ship the M-word stem base

**Don't pick N by frequency and stop.** Analyze a **much larger N**,
cluster the words into **stems** (lexemes), and keep the **M ≪ N base
stems** plus their attested inflected forms. Every word in the large N is
then either one of the M base words or an inflection of one. The
Word-Lookup index is keyed by stem; a lookup either hits a stem directly or
resolves the surface word to its stem.

**Why:** measured on the current 100k S1 — of the 82,046 analyzable words,
only **~26,246 are distinct stems; 68% of the list is inflectional
redundancy**. Shipping stems instead of forms gives far more
lexeme/morpheme coverage per shipped byte, generalises to unseen inflected
forms of known stems, and yields a real lexeme inventory (reusable like S1).

## Stemming a decomposition

A decomp is a sequence of `{surface:canonical/id}` segments. Classify each
`id` tag (verified against `data/grammar/linguistic-data/` and a full tag
tally of the S1 output — these 4 classes cover ~99.5% of tag occurrences):

| class | tag shape | source CSV |
|---|---|---|
| **root** | `\d+[a-z]$` — `1v`, `1n`, `1c`, `1a` | `RootsSpalding` / `RootsSchneider` |
| **derivational suffix** | `\d+[a-z]{2}$` — `1vv`, `1vn`, `1nv`, `1nn`, `2vv`, … (letters = in→out category) | `Suffixes.csv` |
| **inflectional ending** | `^t[nvd]-` / `^t[ap]d-` — `tn-nom-d`, `tv-imp-1d`, `tad-loc`, `tpd-dat-s` | `Endings_noun/verb/…csv` |
| **enclitic** | `\d+q` — `1q` | small closed set |

Demonstratives/pronouns (`pd-…`, `ad-…`, `rad-…`, `rpd-…`; ~0.5% of
occurrences, a closed ~30-form class) are the residue — treat their
stem+deixis as root material, their `t[ap]d-` case suffix as inflectional,
or bucket demonstrative words separately.

```
stem-sig(D) = the tuple of morpheme IDs, from the root through the last
              derivational suffix — i.e. drop trailing enclitics, then cut
              before the first segment whose tag matches ^t[nvd]- / ^t[ap]d- .
```

- By **morpheme ID sequence**, not surface — collapses segmentation
  variants (`{mi:miik/1vn}{ik:k/tn-nom-d}` ≡ `{mii:miik/1vn}{k:k/tn-nom-d}`),
  homograph-safe (`amma/1c` ≠ `angmaq/1v`).
- Inuktitut has exactly **one** inflectional slot, always after all
  derivation and before enclitics, so "everything before the ending" is a
  well-defined cut. A bare particle (`amma/1c`, no ending) → stem-sig = the
  whole thing → singleton paradigm. Correct.
- "D1 is an inflection of D2" ≡ `stem-sig(D1) == stem-sig(D2)` (symmetric —
  "same lexeme").

## "Word M1 is the same lexeme as word M2" — the hard part

A word has several grammatically-valid decomps, so which stem "is" the
word's stem is ambiguous, and **grammar alone can't decide** (every R2L
decomp is grammatical). Need usage evidence (Part 3).

- `lexemes(M) = { stem-sig(D) : D ∈ decomps(M) }`.
- Pairwise rule "∀ D∈M1 ∃ D'∈M2 that inflects D" ≡ `lexemes(M1) ⊆
  lexemes(M2)`. Strictness spectrum: intersection (over-merges on one stray
  decomp) / subset (asymmetric) / equality (under-merges).
- **Recommended: lexeme-level clustering, not pairwise.** Bipartite graph
  words ↔ stem-signatures; edge M—L if some decomp of M has stem-sig L.
  Each L is a paradigm; the words on it are its attested forms. A genuinely
  ambiguous word with k decomps for k distinct lexemes belongs to k
  paradigms — correct, not a bug. No "choice of decomp" needed at this
  step.
- **Noise control:** drop `decomps_lenient == true`; cap at top-N decomps;
  weight each decomp by idiomaticity (Part 3). Measured: using rank-1 only
  → 26,246 stems; using *all* decomps naively → 317,945 stems (12× noise),
  76.8% of words in >1 paradigm. Rank-1 / re-ranked weighting is required.

**Measured paradigm structure (Mode A, rank-1 non-lenient, current 100k):**
14,896 stems with 1 form; 4,265 with 2; …; 1,477 with ≥10. Largest
paradigms are linguistically sane: `qaujima/1v` "know" 156, `ila/1n`
"part/relative" 156, `asi/1n` "other" 145, `nuna/1n` "land" 133,
`pi/1n+liri/1nv+vik/3vn` "work place" 168 (a lexicalized derived stem).

## The Word-Lookup artifact

A **SQLite index** keyed by stem-sig, plus a `surface_form → stem-sig`
index, mirroring `NunavutHansardLocalIndex` / `NunavutHansardDownloader`
(build step → GitHub release asset → app downloads + queries,
`SCHEMA_VERSION` pinned to code). Size — between the ~270 KiB S2 Kotlin and
the ~550 MiB Hansard DB — decides ship-in-APK vs download; measure.

---

# Part 3 — idiomaticity: which decompositions reflect real usage

## The problem

A "good example" of morpheme **M** is a word whose decomposition contains
M. The risk: the analyzer emits a decomposition containing M that is not
what is actually going on in the word, so the example misleads a learner.
Two facts from Benoît Farley narrow it:

1. **Every decomposition R2L produces is grammatically valid.** R2L is
   lexicon + morphotactic adjacency + morphophonology, not a statistical
   segmenter — no garbage parses. This removes the worst case: an example
   word with *no* legitimate reading containing M.
2. **Benoît cannot vouch that every grammatically valid decomposition
   corresponds to a plausible idiomatic usage.** A word form can have
   several valid analyses; in real use one is meant. And a productive
   grammar *licenses* segmentations a linguist would call wrong for a
   particular word (a root whose final syllable is homophonous with a
   suffix; a promiscuous affix the morphotactics let attach almost
   anywhere).

So the target: **a decomposition matched against real bilingual sentence
pairs.** Two layers, cheap then expensive.

## Cheap layer — the R2L `reference@1` re-ranker

R2L's own rank-1 is the Hansard-attested reading ~73% of the time (fair
gold). A re-ranker trained on **R2L's own candidate lists** toward the
single reference decomp does better, deterministically, at no API cost.

Objective: for each gold word, put its **single reference decomp** at rank
1 (`reference@1`). Not `all_correct_decomps` — for R2L that set is R2L's
whole output by construction, so "rank a member of it first" is vacuous.

**Result (2026-09-08).** 10-fold grouped CV, `reference` train target:

| model | fair reference@1 | P@3 | MRR |
|---|---|---|---|
| R2L native rank-1 | 73.1% (670/917) | 85.8% | 0.808 |
| linear pairwise-logistic, all features, `--loo` | 81.8% (750/917) | 97.8% | 0.898 |
| linear + quantile bucketing (`--buckets 10 --loo`) | 87.7% (804/917) | 98.3% | 0.930 |
| **GBDT, nested CV, 3 seeds** | **90.6 / 90.8 / 91.1% (mean 90.8)** | ~98.9% | ~0.948 |

**+17.5 points / ~+161 words over R2L parity** — the same ~90% plateau the
FST re-ranker hit (`data/grammar/fst/reranker-experiment.md`), slightly
higher and tighter on R2L's shorter, all-valid candidate lists. Table
builder: `data/grammar/fst/build_reranker_table_r2l.py`, then the existing
`reranker_*.py` with `RERANKER_TABLE=scratchpad/reranker_table_r2l.jsonl`
(recreatable code in the Appendix). Full numbers, error analysis, and the
per-objective picture:
`doc/dev/plans/reranker-objectives-and-analyzers.md` §3.

**Not yet done:** freeze the GBDT as a Kotlin `reference` re-ranker in
`:core` with a committed per-word snapshot + regression gate (sequencing
step 3); the stem-recurrence feature below; sentence-context re-ranking for
the ~15-word genuinely-ambiguous residual core (the LLM silver standard).

**Not yet added — the S1-enabled feature:** stem-recurrence. A decomp whose
stem-sig has a large attested paradigm in the mined corpus is more likely
the reference. Circular (stems need a re-ranker, the re-ranker wants stem
frequency) → bootstrap: rank-1 → provisional stems → stem-frequency feature
→ re-rank → recompute. One pass probably suffices.

## Expensive layer — the LLM annotation pass (S3)

For each of the top **N** Hansard word forms `W`:

1. **Decompose.** R2L on `W`; keep the top 5 decompositions in **re-ranker
   order**. (R2L, not FST: Benoît's grammaticality claim is about R2L; the
   FST emits ≈ 2× more decomps per word.)

2. **Structural pre-filter.** `W` is a candidate example for morpheme `M`
   only if `M` appears in the re-ranker's **top-1 or top-2** decomposition,
   **and** `n_decomps(W) ≤ 7` (ambiguity cap — precision falls to ≈ 91% for
   `n ∈ [8,10]` per the examples design). Free, deterministic; removes the
   systematic-artifact category before any API call.

3. **Dictionary lookup.** Find an entry for `W`, or its **longest prefix**,
   in **Spalding** (embedded, rights cleared) and **Tusaalanga** (queried
   live). Record source, exact/prefix, matched form. (Prefix fallback is
   only for the sense-guessing side output; example eligibility needs
   exact-word evidence, step 4.) Recovered Living Dictionary word lists are
   **not** used here — see "Rights".

4. **Corpus evidence.** Gather bilingual Hansard sentence pairs containing
   **`W` exactly**. Target 3–5, require ≥ 2. Effectively guaranteed for the
   frequent forms.

5. **Build the LLM prompt.** `W` in Roman + syllabics; the 5
   decompositions each rendered **with per-morpheme glosses and a
   plain-language paraphrase of the whole word** (glosses from the Morpheme
   Dictionary pass-1 data / Benoît's morpheme DB) — turns the task into
   "does this paraphrase match the English side", a translation check; the
   sentence pairs; the dictionary entry if found.

6. **Ask Claude, per decomposition** (not "pick one"):
   ```
   for each of the 5 decompositions:
     { matches_usage: "yes" | "no" | "uncertain",
       confidence:    0.0 – 1.0,
       english_span:  "<the exact English words W corresponds to in the pair>",
       per_pair:      [ { pair_id, matches: bool, english_span } , ... ] }
   ```
   A **quoted English span per pair** forces real word alignment and gives
   something to spot-check. If no dictionary entry was found, also request
   `candidate_senses: [ { gloss, confidence } ]` — the sense-guessing side
   output, not on the examples critical path.

7. **Resolve `usage_attested` per decomposition.** `"yes"` iff Claude's
   `matches_usage == "yes"` with `confidence ≥ τ` (start `τ = 0.75`) **and**
   supported by **≥ 2 sentence pairs**, *or* 1 pair at `confidence ≥ 0.90`.
   Else `"no"` / `"uncertain"`.

8. **Emit the annotated record** (schema below).

Operational: one Claude call per word (batch a few if context allows),
one-time at generation, strong model, shared instructions prompt-cached.
Store **raw model verdicts keyed by word** so regeneration is incremental
and auditable. Home: a `:cli` subcommand (e.g. `--build-offline-dictionary`)
or a script under `data/grammar/fst/`; committed output is the JSONL plus
generated Kotlin.

## Corpora

| corpus | role | notes |
|---|---|---|
| **Nunavut Hansard** | the sentence-pair evidence corpus | already cached & aligned: `data/grammar/fst/hansard-cache/top10k_words_benoit_decomps.jsonl`. Bilingual, drivable. |
| **gov.nu.ca** (Alain's ~5-year-old crawl) | frequency / attestation cross-check **only** | pre-dates the site's protection + "no AI training" notice. Used strictly as **aggregate word counts** — never sent to the LLM, never redistributed. Lets us prefer example words that also occur outside legislative debate. Live crawling is separately blocked (`gov-nu-ca-crawling-investigation.md`). |

Rationale for the split: Hansard sentence pairs to the LLM is clean (public
parliamentary record). gov.nu.ca *content* to an LLM is in the grey zone of
that notice; aggregate frequency counts are not. gov.nu.ca contributes one
integer per word and nothing else.

## Rights: third-party dictionaries

This pipeline is **deliberately designed to need no new dictionary
licences.** Copyright protects the *expression* of a definition, not the
facts of which words exist or which morphemes a word contains. The design
relies only on:

- **Spalding** — rights already cleared, embedded in the app.
- **Tusaalanga** — a live third-party service under its own terms; we query
  it, we don't redistribute it.
- **LLM-generated senses** — the fallback for the (large) set of words no
  cleared dictionary covers.

The recovered **Living Dictionary** and any other unlicensed dictionary
(e.g. Dorais) are **not inputs to this pipeline** — not as content, and not
as a validation yardstick. Fair dealing (Canada) is a closed list of
purposes; "build a validation set / feed a scoring step" fits none of them
cleanly, and the rights holders here are a government Alain has an ongoing
relationship with. The clean uses of the recovered data (extraction for
the Bureau, informing Alain's own linguistic judgement, aggregate
coverage/frequency analysis, the Schneider subset if Benoît's licence
covers it) stay outside this plan.

---

# File Schemas

**SUPERSEDED (2026-09-11).** This section's reconstruction (stems.jsonl /
words.jsonl as a stem-first design with a rule engine deriving each
inflected form's meaning) is being replaced by a simpler direct-per-word
design: see **`data/lexicon/iutools2-dictionary/README.md`**, a new
dataset directory with its own schema draft. Read that instead; this
section is kept as historical record of how the design got there.

The offline dictionary consists of **two JSONL files**:

- **`stems.jsonl`** — one record per paradigm (stem). This is where the
  expensive, authored content lives. The LLM powered Guess Meaning runs **once per row**,
  on the most frequent word form of the paradigm.
- **`words.jsonl`** — one record per surface word form. Mostly a foreign
  key into `stems.jsonl` plus the tail (inflectional) morphemes and a
  meaning that is **rule-derived** from the stem's meaning and those tail
  morphemes — no LLM call, except for the inflection types a rule can't
  safely render.

Together these replace the single per-word `offline-dictionary.jsonl`
sketched under "The artifacts" below. The Word-Lookup SQLite index becomes
a **compiled form** of these two files (a `stems` table, a `words` table,
a `surface → stem` index), not a separate source of truth.

The two files are the end of a short generation pipeline, described in
**Generation process** just below; the field-by-field rationale is in
**Decisions** after that.

## Generation process

The goal is a list of Inuktitut *lexemes* (stems), each with a
plain-language meaning established from real corpus usage, plus a row for
every surface word form that maps to one of those lexemes. We get there by
over-generating candidate lexemes cheaply and then using one LLM call per
candidate to keep the real ones.

**Step 1 — word list.** The 100 000 most frequent Nunavut Hansard word
forms. This is stage S1 (Part 1), already built and published.

**Step 2 — candidate stems.** Decompose every word (R2L, then the
`reference` re-ranker) and cluster the words into candidate stems by the
**stem signature** of each word's top-ranked decomps — the morpheme-ID
sequence from the root through the last derivational suffix (Part 2).

- Not the top-1 decomp only. The re-ranker puts the corpus-attested decomp
  first for ~91% of words, so clustering on rank-1 alone would silently
  drop the correct lexeme for the other ~9%. Each word therefore
  contributes a candidate stem for each of its top-**N** decomps.
- **N is an open choice — measure it.** The number to look at is the
  re-ranker's recall of the *reference* decomp within rank ≤ N: it is
  ~91% at N=1 and ~98.9% at N=3 (`doc/dev/plans/reranker-objectives-and-analyzers.md`
  §3); the N=2 figure is not yet pulled out. The cost that N drives is the
  count of candidate stems, hence of Guess Meaning calls in step 4 — each
  extra rank adds roughly another word-count's worth of candidates before
  dedup. Pick the smallest N past which reference recall barely moves.
  Resulting stem count sits between ~26k (N=1) and ~318k (all decomps).
- Structural only. A candidate stem is just a hypothesis that this partial
  decomp is the real lexeme of the words under it.
- Tooling today: `data/grammar/fst/score_100k.py` (re-ranked top-2 per
  word) → `data/grammar/fst/build_inflection_links.py` (clustering; today
  top-1, to be lifted to top-N).

**Step 3 — pick the two representative forms per candidate stem.**

- `most_frequent_form` — the highest-frequency word in the cluster. This
  is the one sent to the LLM, because frequency ⇒ the best supply of
  bilingual Hansard sentence pairs, which Guess Meaning leans on.
- `headword` — the linguists' citation form (verb → 3sg non-past, noun →
  bare singular), constructed by rule if not itself in the 100k. This is
  what the dictionary entry displays. It is usually *not*
  `most_frequent_form`.

**Step 4 — attestation gate.** Run Guess Meaning on each candidate stem's
`most_frequent_form`, with its Hansard sentence pairs, to establish what
the word actually means in the corpus. From that verdict set
`was_attested_by_llm`:

- `true` — the stem's partial decomp is consistent with that meaning; the
  stem is a real lexeme.
- `false` — it is one of the word's many grammatically-possible but
  non-idiomatic (sometimes nonsensical) decomps.

The verdict is **per stem, not per decomp** (a stem is a partial decomp —
there is nothing finer to attest), and it **propagates to the whole
paradigm**: a `true` attests every word clustered under the stem, though
only `most_frequent_form` was checked directly (`is_stem_most_frequent_form`
marks it). The same call also records polysemy / homonymy signals
(`llm_has_multiple_meanings`, `llm_notes`).

**Step 5 — prune and re-cluster.** Keep only stems with
`was_attested_by_llm == true`. A word left with no attested stem (its
top-N all rejected) re-clusters under the decomp Guess Meaning preferred,
forming a new candidate stem that goes back through step 4. How many
re-cluster rounds, and the stopping rule, is an **open pipeline-design
point**. `was_attested_by_llm` is `null` on a candidate stem until step 4
reaches it; the pruned, shipped `stems.jsonl` has it `true` on every row,
kept as an explicit provenance marker.

**`words.jsonl` is produced alongside**, not as a separate pass: every
analyzable word in the 100k gets a row (its `stem_key` = the attested stem
it landed under), and its per-form readings are rule-rendered from that
stem's `llm_meanings`. Words with no decomposition are covered in the open
points below.

## Decisions

Running record of the schema. Every decision is logged here as it is made,
newest first, so the reasoning survives — the last reconstruction of this
discussion was lost because it was only in a chat session.

- **2026-09-10 — one field, `was_attested_by_llm`, for LLM attestation on
  the stem record; a stem is attested as a whole, never per-decomp.** See
  **Generation process** above: candidate stems from each word's top-**N**
  decomps (N open — 2 vs 3, decided by reference-decomp recall), an LLM
  attestation gate on `most_frequent_form`, then a prune.
  - `was_attested_by_llm`: `true` / `false` / `null`. `null` = no LLM has
    weighed in yet (pre-gate). `true` = Guess Meaning on
    `most_frequent_form` established that the stem's partial decomp matches
    the word's real Hansard meaning. `false` = it is just one of the
    word's many non-idiomatic / possibly nonsensical decomps.
  - One field, not two: `null` already carries "not inspected", so a
    separate `was_inspected_by_llm` is redundant. (Supersedes the earlier
    two-field / `llm_attestation` enum / `llm_inspected` +
    `llm_attests_this_stem` proposals.)
  - **Attestation propagates stem → paradigm**: a `true` verdict attests
    every word clustered under that stem, not just `most_frequent_form`.
  - The pruned, shipped `stems.jsonl` has `was_attested_by_llm == true` on
    every row — kept explicit as a provenance marker. `null` / `false`
    rows exist only in the pipeline's working data.
  - `model` / `generated_at` stay (provenance of the gate call).
  - Words orphaned by the prune re-cluster under the reading Guess Meaning
    preferred, then re-gate — **open pipeline-design point**.
  - Attestation is per stem, never per decomp: no `llm_attested_decomps`.
  - Naming: `stem_attested_as_word` is a *different* fact (the bare stem
    surface is itself a 100k word) — renamed `bare_stem_is_a_word`.

- **2026-09-10 — drop `llm_attested_decomps` from `stems.jsonl`; keep
  `llm_attests_this_stem` as a direct boolean.** A `stems.jsonl` row is
  one stem, and a stem is only a *prefix* of a decomposition — so "a list
  of attested decomps" has no natural home on a stem row. What the LLM
  pass reports about the stem is just:
  - `llm_attests_this_stem` (bool) — does the LLM confirm `most_frequent_form`
    is genuinely used *with this lexeme*? `false` ⇒ the re-ranker founded
    this paradigm on a reading the LLM rejects → flag for review.
  - `llm_has_multiple_meanings` (bool) + `llm_notes` — the homonymy signal
    and its free-text detail (what the other reading is, which stem).
  Per-*decomp* "reflects real usage" is a `words.jsonl` concern — decomps
  live there (`decomps[]`). If the Morpheme Dictionary example gate needs
  it, add an `attested` flag to `words.jsonl` `decomps[]` entries then;
  not built now.

- **2026-09-10 — `words.jsonl`: `is_inflection` → `is_stem_headword`
  (inverted); add `is_stem_most_frequent_form`; `inflection` →
  `inflection_from_stem`; drop `relation_to_headword`.**
  - `is_stem_headword` = `not is_inflection`: true ⇔ this attested word is
    its stem's headword. When the headword is synthetic there is simply no
    row with it true. (`is_inflection` conflated "carries inflection" —
    readable off the inflection block — with "is the headword".)
  - `is_stem_most_frequent_form` — true ⇔ this word is its stem's
    `most_frequent_form` (the Guess Meaning input; its `llm_meanings` are
    `source: "copy"`). Symmetric with `is_stem_headword`; both are
    join-derivable from `stems.jsonl` but kept for convenience.
  - `inflection_from_stem` — the endings/enclitics this form adds to the
    **stem**, not a delta against the headword or `most_frequent_form`.
    (The stem is the shared prefix, so "the form's own tail" and "relative
    to the stem" are the same thing.)
  - **`relation_to_headword` dropped.** It was `build_inflection_links.py`'s
    `base_relation` — one coarse enum (`verbal-agreement` /
    `verbal-mood-change` / …) got by comparing this form's ending-kind to
    the *headword's*. It is redundant twice over: the linguistic content
    is `inflection_from_stem.features`, and its only real job — "can the
    rule render this, or fall back to the LLM" — is recorded as the
    *outcome* per sense in `llm_meanings[].source` (`rule` vs `llm`). A
    build step still makes that rule-vs-LLM call, reading
    `inflection_from_stem.features` directly; it needs no stored enum.

- **2026-09-10 — `words.jsonl` carries the full decomp list, best-first,
  each with its own `lenient` flag.** `decomps` is **every** decomposition
  R2L produces for the word, in best-first order. How that order is
  produced (R2L emission, then a re-ranker) is an implementation detail
  and is **not** represented in the JSONL — no per-decomp rank, no
  `margin`, no `re_ranker_*`, no per-decomp score. (The word-level `rank`
  field stays — it is the word's frequency position in the 100k, from S1,
  nothing to do with the re-ranker.) Each entry is
  `{ "decomp": [canonical/id…], "lenient": bool }`. Convenience
  denormalisations: `top_decomp` (= `decomps[0].decomp`),
  `top_decomp_is_lenient` (= `decomps[0].lenient`),
  `some_lenient_decomps_included` (= any `decomps[*].lenient`). `stem_key`
  is the stem of `top_decomp`. The old single `decomp` + word-level
  `lenient` + `margin` fields are removed.

- **2026-09-10 — `roman` / `syll` suffixes everywhere.** `words.jsonl`
  uses `word_roman` / `word_syll` (was `word` / `word_syl`), matching
  `stems.jsonl`'s `headword_roman` / `_syll` and
  `most_frequent_form_roman` / `_syll`.

- **2026-09-10 — `stems.jsonl.llm_meanings` is a flat list of `{ "en": …,
  "fr": … }`, position = sense id.** After stripping `decomp`,
  `usage_attested` and `paraphrase` from the sense object, a sense *is*
  just a bilingual dictionary-phrasing pair. So: no `sense_id` field
  (position is the id; safe because `words.jsonl` is regenerated with
  `stems.jsonl`), no wrapper object. Usually one entry; >1 = polysemy
  (same stem, related senses — kept, unlike the <1% homonymy which is only
  flagged). This settles the (b)/(c) question — it is (b)'s intent
  (multiplicity per language) in (c)'s shape (one list, en/fr can't drift
  in length). `stems.jsonl.paraphrase` is **dropped**: it was
  `most_frequent_form`'s own reading, which already lives on
  `most_frequent_form`'s `words.jsonl` row.

- **2026-09-10 — no per-sense `usage_attested` on `stems.jsonl`.** The
  senses were generated *from* Hansard sentence pairs handed to the LLM,
  so they are Hansard-attested by construction — a per-sense "is it
  attested" verdict says nothing. Idiomaticity/attestation stays at the
  **decomp** level (`llm_attested_decomps`, for the Morpheme Dictionary
  example gate). The `offline-dictionary.jsonl` sketch's per-decomp
  `usage_attested` block does not carry over to the sense objects. Open:
  whether to keep 1–2 illustrative Hansard pairs and/or an LLM
  `confidence` per sense, or nothing. Consequence: the only language-
  neutral per-sense field left is `sense_id`, so option (b)
  (`llm_meanings: {en: […], fr: […]}` with `sense_id` = list position)
  is now as clean as (c); still leaning (c) for the explicit `sense_id`
  and room to grow.

- **2026-09-10 — meanings are bilingual, as `{ "en": …, "fr": … }` on one
  sense object; and every `stems.jsonl` row gets `llm_inspected`.**
  - The app UI is English + French, so the user-facing strings need both.
    Not two parallel lists (`_en` / `_fr` drift out of sync and duplicate
    the language-neutral fields). Instead **one list of sense objects**,
    each with `sense_id` (language-neutral) and `meaning` / `paraphrase`
    as `{ "en": "to hear", "fr": "entendre" }`. Same on `words.jsonl`
    (`paraphrase: {en, fr}`). (No per-sense `decomp` or `usage_attested`
    — see the entries below.)
    French is a translation of the English (AGENTS.md i18n rule); it may
    be filled by a later pass, so `fr` can be absent/null until then.
    `llm_notes` stays English-only (dev/audit field, not user-facing).
  - **`llm_inspected`** (bool) on every `stems.jsonl` row — did the Guess
    Meaning / LLM pass run for this stem? Distinct from "did it produce
    senses": a stem can be inspected with `llm_meanings: []` (LLM
    uncertain). S3 is incremental, so consumers need to tell "not done
    yet" from "done, nothing found". `model` / `generated_at` stay as the
    provenance detail (null when `llm_inspected` is false). Chosen over
    `llm_augmented` — the pass *inspecting* the word is the fact; whether
    it augmented anything is what the other fields say.

- **2026-09-09 — the Guess Meaning call also does stem-attestation and
  homonymy detection; `stems.jsonl` records both.** *(`llm_attested_decomps`
  from this entry is dropped — see the 2026-09-10 entry at top.)* The call
  sees the word, its candidate decomps, and the Hansard sentence pairs.
  Ask it, in the same call, to also report:
  - does `most_frequent_form`'s real usage go with this stem → the bool
    `llm_attests_this_stem`; `false` ⇒ the re-ranker founded the paradigm
    on a reading the LLM rejects → flag for review;
  - whether this surface form carries more than one distinct meaning in
    common use → `llm_has_multiple_meanings` (bool);
  - free-text rationale → `llm_notes` (why senses were split, what the
    other reading is, caveats — the auditing hook).
  **Sense↔decomp is a row-level fact, not a per-sense field.** A
  `stems.jsonl` row is one stem, and a stem is the start of exactly one
  decomp of `most_frequent_form` — so every sense on the row shares that
  one decomp (multiple senses on a row = polysemy, not homonymy). No
  per-sense `decomp`. A sense the LLM ties to a *different* decomp belongs
  to a different stem's row; that surfaces via `llm_attests_this_stem` /
  `llm_has_multiple_meanings` / `llm_notes`.
  Homonymy handling for **v1**: still one paradigm + one primary sense per
  surface form (the re-ranker's #1); `llm_has_multiple_meanings=true`
  forms are **flagged** for Benoît's review, not auto-split. Measured rate
  is <1% (top-1-vs-top-2 view of Hansard, `analyze_top2.py`); externally
  sourced homonym example lists proved unreliable (LLM filler). If review
  shows the rate is materially higher, switch the flagged forms — only
  those — to a multi-`stem_key` `words.jsonl` row (option b).

- **2026-09-09 — terminology: `headword`, `meaning_en`, `paraphrase_en`;
  no `lemma`, no `gloss_en`.**
  - `headword` (kept, not renamed to `lemma`) — the Inuktitut citation
    form shown to the user. "Headword" reads clearly for non-linguists;
    "lemma" is the same concept but jargon. Note `lemma ≈ headword`, **not**
    `lemma ≈ stem` — `stem` is the morpheme skeleton, a headword is a word.
  - `meaning_en` — one string per sense: the sense stated the way a
    bilingual dictionary would, at English's own citation phrasing
    (`"to hear"`, `"house"`, `"big"`), independent of whatever inflection
    the Inuktitut headword carries. Replaces both `gloss_en` and
    `lemma_gloss`.
  - `paraphrase_en` — the reading of **one specific inflected form**
    (`most_frequent_form` on `stems.jsonl`; the form itself on
    `words.jsonl`): `"we hear"`, `"you hear"`, `"he heard"`.
  - `gloss` stays reserved for per-morpheme glosses (the S3 concern), and
    is not used in these two files.
  - The meaning rule: `paraphrase_en(form) = meaning_en ⊕ render(that
    form's absolute inflection features)`.

- **2026-09-09 — the LLM-derived senses field is `llm_meanings`, not
  `meanings`.** So it never gets confused with senses drawn from a human
  dictionary (the bottom "Definitions found in human-produced dicts"
  concern). Applies to both files: `stems.jsonl.llm_meanings` (Guess
  Meaning's output for `most_frequent_form`) and `words.jsonl.llm_meanings`
  (the per-form rule-rendered senses). Each `stems.jsonl.llm_meanings`
  entry carries `meaning_en` + `paraphrase_en`; each `words.jsonl` entry
  carries `paraphrase_en`.

- **2026-09-09 — keep a corpus-attestation flag, `stem_attested_as_word`,
  on `stems.jsonl`, separate from `headword_is_inflected`.** Boolean: true
  ⇔ the bare stem surface form (no inflectional ending, no enclitic) is
  itself one of the 100k word forms. `headword_is_inflected` is a fact
  about the *constructed citation form*; this is a fact about the
  *corpus*. They move independently — a noun whose citation form is the
  bare stem (`headword_is_inflected=false`) may still never occur
  un-possessed in Hansard (`stem_attested_as_word=false`, headword then
  synthetic).

- **2026-09-09 — `words.jsonl` `meaning` → `llm_meanings`, a list, one
  entry per stem sense**, aligned to `stems.jsonl.llm_meanings` by
  `sense_id`. Each entry `{ sense_id, paraphrase_en, source }`, `source` ∈
  `rule` | `copy` | `llm`. Every sense is pre-rendered for every form — it
  is an offline dictionary, do the work once; the app ships no rule engine.

- **2026-09-09 — the headword is ALWAYS a real word, in the linguists'
  citation convention, and two fields describe it.** (Alain's recollection;
  supersedes the "least-inflected attested tier" mechanics.)
  - Not "the least-inflected attested form" — the **conventional citation
    form a linguist would use**: for a verb, the 3rd person singular
    non-past (`tusaqtuq`, *he hears*); for a noun, the bare singular.
    Constructed by rule if that form is not itself attested (needs a form
    generator — we have the FST; flag the entry when synthetic).
  - **`headword_is_inflected`** — whether the stem is identical to the
    headword, i.e. whether any morpheme had to be added to the stem to
    make the citation form. `false` for a noun / particle whose citation
    form *is* the bare stem; `true` for every verb (the convention adds
    3sg) and for nouns whose citation form carries a marker.
  - **`headword_inflection`** — the morpheme(s) added to the stem to form
    the headword, as `{canonical, id}` segments; `[]` when
    `headword_is_inflected` is `false`. This is the delta that reduces the
    headword's own displayed reading (*he hears*) back to the dictionary
    meaning (*to hear*).
  - Whether we *also* keep a separate corpus-attestation flag — "does the
    bare stem ever occur as a word in the 100k" (the old
    `base_is_inflected`) — is open; it is a different question.

- **2026-09-09 — `headword` and `most_frequent_form` are two distinct
  fields on `stems.jsonl`, computed differently.**
  - `headword` = the canonical citation form shown to the user for this
    stem — see the entry above for how it is derived.
  - `most_frequent_form` = the form Guess Meaning is run on. Picked purely
    for **highest frequency overall**, because that maximises the odds of
    bilingual Hansard sentence pairs, which Guess Meaning leans on heavily.
    Often inflected (a 3sg verb form outranks any citation form).
  - They coincide only when the most frequent form is also the least
    inflected. The headword may have **no** bilingual examples at all —
    reason enough not to send it to Guess Meaning. So every non-most-
    frequent form's meaning, the headword's included, is rule-derived from
    the `llm_meanings` block (which is for `most_frequent_form`).

- **2026-09-09 — the pre-interruption agreed schema survives as the field
  list `build_inflection_links.py` adds to each word.** That script is the
  record of the earlier discussion. Per-word fields it writes: `stem_key`,
  `paradigm_size`, `is_inflection`, `base_word` / `base_word_syl` /
  `base_rank` (the base = headword, see above), `base_is_inflected`,
  `base_relation`. The two-file split puts the paradigm-level fields
  (`stem_key`, `paradigm_size`, the headword fields, `base_is_inflected`)
  on `stems.jsonl` and the per-word ones (`is_inflection`, `base_relation`)
  on `words.jsonl`.

- **2026-09-09 (SUPERSEDED same day — see top: `headword_is_inflected`)** —
  `base_is_inflected` was first read as "is the stem itself a bare word in
  the 100k?" and renamed `stem_attested_as_bare_word`. Alain's later
  recollection is that the field is about the *headword* (is the stem
  identical to the citation form), not corpus attestation. The
  corpus-attestation question may still be worth a separate flag — left
  open.

- **2026-09-09 (supersedes the "no `base_*`, spell out `most_frequent_form_*`"
  entry below)** — that rename assumed "base" meant "most frequent form".
  It does not: `build_inflection_links.py`'s `base_*` describe the
  **headword** (least-inflected-tier pick). Corrected mapping:
  `base_word` → `headword_roman`, `base_word_syl` → `headword_syll`,
  `base_rank` → `headword_rank`; `most_frequent_form_roman` / `_syll` /
  `_rank` / `_freq` are a **separate, new** field group (the Guess Meaning
  input). `base_relation` is computed against the base, so it is
  **`relation_to_headword`**, not `relation_to_most_frequent_form`.
  `stem_decomp` and `is_inflection` are unchanged.

- **2026-09-09 — `dict_attestation` is NOT part of the two-file schema as
  originally discussed.** The original discussion had no "which human
  dictionaries define this" field. The attestation it did discuss was
  **corpus attestation — presence in the analyzed lexicon (the 100k)** —
  see the open point below. The human-dictionary-attestation idea is the
  later "Definitions found in human-produced dicts" section at the end of
  this doc; it still needs its own home and is not folded into `stems.jsonl`
  / `words.jsonl` here yet. Pulled from both reconstruction drafts.

- **2026-09-09 — `meaning` → `meanings` (a list), and it holds the senses
  of `most_frequent_form_roman`.** Guess Meaning commonly returns more than
  one sense, so the field is a list (best sense first). The senses are for
  `most_frequent_form_roman` as it stands — an actual, possibly inflected
  word form — not for an abstract stem lemma. A `words.jsonl` form that is
  not the most frequent one derives its own meaning(s) by rule from these,
  plus its inflectional delta. *(Open: does `words.jsonl` `meaning`
  likewise become `meanings`, one per stem sense?)*

- **2026-09-09 — no `base_*` field names; spell out `most_frequent_form_*`
  (except `stem_decomp`).** Verbose and clear beats short and abstract.
  Full rename:
  `stems.jsonl` — `base_rank` → `most_frequent_form_rank`, `base_freq` →
  `most_frequent_form_freq`, `base_is_inflected` →
  `most_frequent_form_is_inflected`.
  `words.jsonl` — `is_base` → `is_most_frequent_form`, `base_relation` →
  `relation_to_most_frequent_form`.
  `base_decomp` is the exception: it is **`stem_decomp`**, because it is not
  the whole decomp of the most frequent form — it is that form's rank-1
  (re-ranked) decomp **truncated to its stem**, i.e. the leading segments
  from the root through the last derivational suffix, the ending and any
  enclitics dropped. It is `stem_key` re-expressed as an array of
  `canonical/id` segments (`stem_key.split("+")`), kept in the record for
  convenience.

- **2026-09-09 — the paradigm's representative form is `most_frequent_form_roman`,
  not `base_word`.** "Base word" read as too abstract. The field holds the
  most frequent word form of the paradigm — the form Guess Meaning is run
  on. Its syllabic counterpart is `most_frequent_form_syll`. (Both `stems.jsonl`.)

- **2026-09-09 — join key is `stem_key` (the morpheme-ID signature string),
  not an opaque `stem_id`.** `stem_key` = `"tusaq/1v+lauq/1vv"`: root
  through last derivational suffix, canonical/id tuples joined with `+`.
  Self-describing (you can read `words.jsonl` without a join), a
  deterministic function of the decomp (already computed by
  `build_inflection_links.py`), diff-friendly. It changes only if the
  stemming rule changes — which is the wanted behaviour, that is a
  different clustering. The ~30 bytes × ~100k rows cost is irrelevant for
  an intermediate artifact; the SQLite build may intern it to an integer
  `rowid` FK, so the shipped index pays nothing.

Open, under discussion:

- **How `meaning.en` is produced.** Guess Meaning runs on
  `most_frequent_form` (e.g. `tusaqtugut`, *we hear*) and returns a reading
  of **that inflected form**. `meaning.en` is that reading reduced to the
  dictionary phrasing (*to hear*) — `most_frequent_form`'s own inflection
  (*we*, present) stripped off. Stored once per sense so the rule is one
  step from a clean base:
  `paraphrase.en(form) = meaning.en ⊕ render(form's absolute inflection features)`
  (`tusaqtutit` → *you hear*; headword `tusaqtuq` → *he hears*;
  `tusalauqtunga` → *I heard*). Open: get `meaning.en` from the LLM in the
  same call (English wording is easier for the model than a rule), or
  strip it mechanically from the returned `paraphrase.en`. The build step
  decides rule vs LLM fallback per form from `inflection_from_stem.features`
  directly, and records the outcome in `llm_meanings[].source`.
  (`meaning.fr` / `paraphrase.fr`: translate `meaning.en` once per sense,
  then the same `⊕ features` rule runs in French from `meaning.fr`.)

## `stems.jsonl`

*Reconstruction draft, 2026-09-09 — not yet agreed. Fields will move to
"Decisions" as they are confirmed.*

```jsonc
{
  "stem_key": "tusaq/1v",              // signature: root → last derivational suffix (join key)
  "stem_decomp": ["tusaq/1v"],         // stem_key as an array of canonical/id segments
  "paradigm_size": 27,                 // number of 100k word forms on this paradigm

  // -- headword: the linguists' citation form shown to the user (verb → 3sg non-past; noun → bare sg).
  //    Constructed by rule if not itself attested. --
  "headword_roman": "tusaqtuq",        // "he hears"
  "headword_syll": "ᑐᓴᖅᑐᖅ",
  "headword_rank": 512,                // frequency rank if the citation form is itself in the 100k; null if synthetic
  "headword_is_inflected": true,       // false ⇔ the stem IS the headword (nothing added). true here: 3sg was added.
  "headword_inflection": [             // the morpheme(s) added to the stem to form the headword; [] if not inflected
    { "canonical": "juq", "id": "tv-ger-3s" }
  ],
  "bare_stem_is_a_word": false,        // is the BARE stem (no ending, no enclitic) itself one of the 100k word forms?
                                       //   a corpus-form fact — NOT the same as "this stem is attested" (every row here is)

  // -- most_frequent_form: the form Guess Meaning is run on (highest frequency overall
  //    ⇒ best odds of bilingual Hansard sentence pairs). May be inflected; == headword only sometimes. --
  "most_frequent_form_roman": "tusaqtugut",   // "we hear" — most frequent in Hansard
  "most_frequent_form_syll": "ᑐᓴᖅᑐᒍᑦ",
  "most_frequent_form_rank": 342,
  "most_frequent_form_freq": 118,

  "was_attested_by_llm": true,         // true / false / null. null = no LLM has weighed in yet (pre-gate).
                                       //   true  = Guess Meaning on most_frequent_form established this stem's
                                       //           partial decomp matches the word's real Hansard meaning.
                                       //   false = just one of the word's many non-idiomatic / nonsensical decomps.
                                       //   shipped stems.jsonl: always true (pruned). propagates to all paradigm words.

  "llm_meanings": [                    // the sense(s) Guess Meaning derived for this stem, from most_frequent_form +
                                       // its Hansard pairs. One entry per sense, best first; POSITION = sense id
                                       // (words.jsonl aligns by it — the two files are regenerated together).
                                       // Usually length 1; length >1 = polysemy (same stem, related senses).
                                       // Each entry is dictionary phrasing — most_frequent_form's own inflection
                                       // stripped off; every form's reading, headword included, is rule-derived
                                       // from it. fr translates en, may be null until the translation pass runs.
                                       // No per-sense attested/decomp/paraphrase field — see Decisions.
    { "en": "to hear", "fr": "entendre" }
    // { "en": "to heed", "fr": "tenir compte de" }   // rare: a second, polysemous sense
  ],
  "llm_has_multiple_meanings": false,  // LLM's judgment: does this surface form carry >1 distinct meaning in common use?
                                       //   true ⇒ flagged for review (v1 does not auto-split — see Decisions)
  "llm_notes": "…",                    // LLM free-text rationale: sense splits, the other reading + its stem, caveats

  // human-dictionary attestation is a SEPARATE concern — the bottom section, not folded in here.

  "model": "claude-…",
  "generated_at": "2026-…Z"
}
```

## `words.jsonl`

*Reconstruction draft, 2026-09-09 — not yet agreed.*

```jsonc
{
  "word_roman": "tusaqtutit",
  "word_syll": "ᑐᓴᖅᑐᑎᑦ",

  // this word's frequency rank in the 100k (from S1)
  "rank": 8123,
  // its token count in the corpus (from S1)
  "count": 9,
  // → stems.jsonl (stem of top_decomp; null if the word has no decomp)
  "stem_key": "tusaq/1v",

  // the COMPLETE set of decompositions for this word, ordered best-first.
  // How that order is arrived at is not this file's concern.
  "decomps": [
    { "decomp": ["tusaq/1v", "jutit/tv-decl-2s"], "lenient": false },
    { "decomp": ["tusaq/1v", "juti/1vn", "t/tn-nom-p"], "lenient": true }
    // …
  ],
  // convenience: == decomps[0].decomp
  "top_decomp": ["tusaq/1v", "jutit/tv-decl-2s"],
  // convenience: == decomps[0].lenient
  "top_decomp_is_lenient": false,
  // convenience: any decomps[*].lenient
  "some_lenient_decomps_included": true,

  // true ⇔ this attested word IS its stem's headword (citation form).
  // absent-as-true when the headword is synthetic (no row for it)
  "is_stem_headword": false,
  // true ⇔ this word IS its stem's most_frequent_form (the Guess Meaning
  // input; its llm_meanings entries have source "copy", not "rule")
  "is_stem_most_frequent_form": false,

  // the endings / enclitics THIS form adds to the STEM (not a delta vs
  // the headword or most_frequent_form) — the input to the meaning rule
  "inflection_from_stem": {
    "endings": ["tv-decl-2s"],
    "enclitics": [],
    "features": { "class": "verbal", "mood": "declarative", "person": "2s" }
  },

  // no relation_to_headword / relation_to_stem: the linguistic content is
  // inflection_from_stem.features, and whether the rule could render each
  // sense is recorded per sense in llm_meanings[].source ("rule" vs "llm").

  // -- OPEN: does words.jsonl carry a per-form reading at all? (does the
  //    app show "you hear" for tusaqtutit, or just the stem's meaning
  //    "to hear" for every form of the paradigm?) --
  // one entry per stem sense, position-aligned to stems.jsonl.llm_meanings
  "llm_meanings": [
    {
      // this sense's stem meaning ⊕ this form's features (2s, decl)
      "en": "you hear", "fr": "tu entends",
      // "rule" | "copy" (this word IS most_frequent_form) | "llm" (rule can't render it)
      "source": "rule"
    }
  ]
}
```

If the app only ever shows the stem's `meaning`, drop `llm_meanings` here
entirely — a `words.jsonl` row is then just `stem_key` + `sense_id`(s) it
resolves to + the morphology fields.

---

# The artifacts

## S1 dataset — `data/lexicon/decompositions/`

Shipped as GitHub release `analyzed-lexicon-v1`; `datapackage.json` +
`fetch.sh` + `regenerate.sh` in git. See Part 1 and that directory's
`README.md`.

## Per-word idiomaticity record — `offline-dictionary.jsonl` (S3 intermediate, committed)

```jsonc
{
  "word": "tusalauqtuq",
  "word_syllabic": "ᑐᓴᓚᐅᖅᑐᖅ",
  "hansard_rank": 342,
  "hansard_freq": 118,
  "govnu_freq": 41,                    // null if absent from the gov.nu.ca crawl
  "dict_entry": {                      // null if none found
    "source": "spalding",
    "match": "exact",                  // "exact" | "prefix"
    "matched_form": "tusaq"
  },
  "decomps": [
    {
      "rank": 1,                       // re-ranker rank among W's decompositions
      "morphemes": ["tusaq/1v", "lauq/1vv", "juq/1vn"],
      "gloss":     ["hear", "PAST", "3sg.IND"],
      "paraphrase_en": "he/she heard",
      "usage_attested": {
        "verdict": "yes",              // "yes" | "no" | "uncertain"
        "confidence": 0.92,
        "corpus": "hansard",
        "pairs_supporting": 3,
        "pairs_examined": 4,
        "evidence": [
          { "iu": "…tusalauqtuq…", "en": "…we heard…", "en_span": "we heard" }
        ]
      }
    }
    // … up to 5
  ],
  "generated_senses": [                // present only when dict_entry == null
    { "gloss": "to hear (something)", "confidence": 0.8 }
  ],
  "model": "claude-…",
  "generated_at": "2026-09-06T…Z"
}
```

Key point — **how the dictionary specifies which decompositions match
corpus usage**: every decomposition carries a `usage_attested` block, not a
bare flag. It states a `verdict`, a `confidence`, **which corpus** the
evidence came from (always `hansard`), how many sentence pairs supported
vs. were examined, and the pairs themselves with the aligning English span
quoted. A downstream consumer can re-apply its own threshold instead of
trusting ours.

## Inverted example-words index (S2, consumed by Morpheme Dictionary pass 2)

`morphemeId → [ { word, score, evidence_strength }, … ]`, pre-scored,
root-balanced, capped at 20 — **exactly the encoding and machinery already
in `morpheme-dictionary-examples-design.md`**, with only the eligibility
gate changed:

> **W is eligible as an example of M** iff some decomposition `D` of `W`
> has `M ∈ D` **and** `D.usage_attested.verdict == "yes"` **and** `D` is
> within the re-ranker top-2 **and** `n_decomps(W) ≤ 7`.

Scoring (fitness, `rootHasDefinition`, normalised frequency, cleaner
decomposition), root balancing (`ExamplesRootsBalancer`), cap-20 truncation
— unchanged from that document. `evidence_strength` becomes an additional
tie-break, below fitness and above raw frequency. Optional second tier,
de-emphasised in the UI: `verdict == "uncertain"` but `M` in the re-ranker
top-1 — shown only when a morpheme has too few `"yes"` examples.

## Word-Lookup stem index

SQLite: `stem-sig → { canonical decomp, gloss, forms[] }` + `surface_form →
stem-sig`. See Part 2.

---

# Validation — before trusting the LLM judge

Run the full pipeline on the **919 `--fair` gold words** and compare
Claude's `usage_attested` verdicts against the gold's Hansard-attested
decomposition.

- **Morpheme-level precision** — of the decompositions Claude marks
  `"yes"`, what fraction contain the gold-relevant morphemes. This (not
  whole-decomposition precision) is the real target: an example only needs
  *M* genuinely present.
- **Recall** — does the gold decomposition itself get a `"yes"`.
- **Lift over the re-ranker alone** — compare against taking the re-ranker
  top-1 with no LLM step. Quantify what the API cost buys.

**Ship criterion:** morpheme-level precision ≥ ~95% on the `"yes"` set. If
lower: raise `τ`, require more supporting pairs, or keep a strict "M in
every decomposition" structural floor as an additional AND-condition.

---

# Open decisions

- **N for S3.** S1 is already 100k. The LLM pass is one API call per word,
  so cost scales linearly — start smaller (over the **stem base**, not raw
  top-N: fewer, higher-value targets), expand if morpheme-inventory
  coverage against `LinguisticData.allMorphemeIDs()` (5211) disappoints.
- **N for a bigger S1 re-mine** (Part 2 wants a larger analysis to find the
  stem base): 200k / 500k / 1M → `analyzed-lexicon-v2`, with a junk filter
  for the tail (typos / names / borrowings; the 18k no-decomp words are a
  signal).
- `τ` and the minimum-pairs rule — set empirically from the gold
  validation, not guessed.
- Batch size per Claude call.
- Keep `"uncertain"` decompositions in the per-word record (recommended:
  yes, storage is cheap).
- Emit a single `best_decomp` per word (highest-confidence `"yes"`, else
  re-ranker top-1) for non-examples consumers.
- Ship the Word-Lookup stem index in the APK or download it (Part 2).
- Zenodo↔GitHub DOI webhook for S1 releases; Hugging Face Datasets mirror.

---

# Non-goals

- Not a runtime feature — the shipped artifacts are frozen; no live corpus
  queries, no live LLM calls.
- Not rebuilding Guess Meaning.
- Not using the recovered Living Dictionary (or Dorais, or any unlicensed
  dictionary) as pipeline input — content or validation (see "Rights").
- Not sending gov.nu.ca content to the LLM (frequency counts only).
- Not the gov.nu.ca live crawl (blocked; `gov-nu-ca-crawling-investigation.md`).

---

# Sequencing

1. **S1 v1 — DONE (2026-09-07).** 100k Hansard forms, R2L, no re-ranker,
   published as `analyzed-lexicon-v1`.
2. **Stem clustering.** Implement the Part 2 stemmer + lexeme-level
   clustering over S1 v1; report M (distinct stems), paradigm-size
   distribution, morpheme-inventory coverage. Cheap function of S1 —
   gitignore the output, track the script. Feeds both S2 and the
   Word-Lookup index.
3. **R2L `reference@1` re-ranker.** GBDT on the R2L table **done
   2026-09-08** — 90.8% fair reference@1 (nested CV, 3-seed mean), +17.5 pts
   over R2L's 73.1% native (Part 3 "Cheap layer"). **Frozen as a Kotlin
   `org.iutools.morph.rerank.ReferenceReranker` in `:core`, done 2026-09-11**
   — 90.1% fair reference@1 on a reduced, zero-new-data-table feature set
   (see "Post-validation" below); parity-tested against the Python model
   bit-for-bit, wired into the `guess_meaning` subcommand. Still to do: add
   the stem-recurrence feature; expand training data (LLM silver standard,
   `:cli --rank-decomps`).
4. **S2.** Invert S1 (re-ranked) into `morphemeId → [example words]`
   (eligibility gate, score, root-balance, cap-20). Ship Morpheme
   Dictionary pass 2.
5. **S3 — the LLM pass.** Validation run on the 919 gold words; set `τ` /
   min-pairs; full run over the stem base; emit `offline-dictionary.jsonl`;
   resolve S3 licensing before publishing an S3 dataset.
6. **Word-Lookup offline dict.** Build the stem SQLite index + a downloader,
   reusing the Hansard-DB pattern; wire into Word Lookup with a live-R2L
   fallback.
7. **Wire-in & record numbers.** Generate the Kotlin blobs (chunked for the
   65535-byte literal limit); wire into `:core`'s `MorphemeDictionary`;
   surface in `MorphemeDictionaryScreen.kt` with `values/` + `values-fr/`
   strings. Record measured coverage/precision back into
   `morpheme-dictionary-examples-design.md`, mark its Open items resolved.

---

# Post-validation: port the spike's Guess-Meaning step to Kotlin

**Status: all three steps DONE (2026-09-11).** Originally scoped as
"not yet triggered — after schema validation" (see the superseded text
below the status note); Alain decided to do the Kotlin work immediately
instead, partly to close the Python/Kotlin fidelity gap it surfaced
(cap=5 sent to the LLM in R2L's raw native order — no re-ranking — turned
out to be a real quality problem: 12/100 spike words got zero attested
decomps, traced to the sent top-5 not containing the word's true reading).

1. **Global `--pipeline` — done.** `Main.kt` dispatches; a bare stdin line
   still gets `segment_iu`'s original reply (backward-compat with
   `mine_decompositions.py` / `add_all_correct_decomps.py`), a JSON
   `{"cmd": ...}` line dispatches to any subcommand. See
   `PipelineDispatcher.kt`.

2. **GBDT R2L re-ranker — done, ported as `org.iutools.morph.rerank.
   ReferenceReranker` in `:core`.** Reduced feature set (drops `r2l`
   /`backoff`/`bigram` — ~0.7pt cost, but means **zero new corpus-derived
   data tables**: every kept feature is either a pure function of
   `(word, decomp)` or exactly `MorphemeFrequencyPrior`, verified
   byte-for-byte against the Python training data). **90.1% fair
   reference@1, honest nested CV** (826/917) vs R2L's native 73.2%. Trees
   converted to raw-threshold form (no binning logic needed in Kotlin),
   validated bit-for-bit against the Python model before shipping. Full
   design, training, and export details:
   `data/grammar/reference-reranker/README.md`. Cross-language parity is a
   real committed test (`ReferenceRerankerParityTest`, 224 golden-fixture
   rows) — not just a spot check.

3. **`guess_meaning` `:cli` subcommand — done.** `GuessMeaningCommand.kt`
   (single-shot) / `PipelineDispatcher.kt` (JSON pipeline form), backed by
   `org.iutools.llm.GuessMeaningStructuredEngine` in `:core`: up to 3
   English senses + French translations, and which of the word's
   (`ReferenceReranker`-sorted, capped) decomps the evidence attests.
   Token counts + `org.iutools.llm.estimatedCostUsd` are in every reply —
   measured at ~$0.002/word with real Hansard evidence on the 100-word
   spike sample.

The API key itself (`ANTHROPIC_API_KEY` env var, or
`~/.iutools2/local.secrets.properties` — see `ApiKey.kt`) is resolved the
same way regardless of which of the two invocation forms is used.

<details>
<summary>Superseded original framing (kept for history)</summary>

**Condition — not yet triggered.** The `data/lexicon/iutools2-dictionary/`
spike (README.md's "Use cases" section) is validating the new
words.jsonl/stems.jsonl schema against a 1883-word sample before any of
this is built for real. Phase 1 (structural, no LLM) is done; Phase 2 (the
LLM pass, done in Python for the spike) is next. **Once the schema/approach
is validated**, that Python Phase-2 logic gets ported to Kotlin as three
pieces, in this order (Alain, 2026-09-11).

</details>

---

# Appendix — how to reproduce the R2L re-ranker (2026-09-08)

All in `data/grammar/fst/`.

- **`reranker_cv.py`** — `TABLE` is now `os.environ.get("RERANKER_TABLE",
  "scratchpad/reranker_table.jsonl")`, so the CV/GBDT scripts (which all
  import it) run on either table unchanged.
- **`build_reranker_table_r2l.py`** (new) — R2L analogue of
  `build_reranker_table.py`. Candidates from
  `hansard-cache/top10k_words_benoit_decomps.jsonl` (R2L over the top-10k;
  covers all rankable fair gold words → no live CLI run), deduped to
  (canonical, id) sequences at their earliest native rank; same
  identity-free `features()` / `add_relative_features()`, imported
  unchanged. `label` = candidate == the single reference decomp;
  `feat["weight"] = 0.0` for all (the cache records lenient-ness per
  request, not per decomp). Output `scratchpad/reranker_table_r2l.jsonl`,
  schema identical to the FST table.

```sh
cd data/grammar/fst
python3 build_reranker_table_r2l.py                                              # -> scratchpad/reranker_table_r2l.jsonl ; prints the 73.1% baseline
RERANKER_TABLE=scratchpad/reranker_table_r2l.jsonl python3 reranker_cv.py --loo               # linear, 81.8%
RERANKER_TABLE=scratchpad/reranker_table_r2l.jsonl python3 reranker_cv.py --loo --buckets 10  # bucketed linear, 87.7%
RERANKER_TABLE=scratchpad/reranker_table_r2l.jsonl python3 reranker_gbdt_nested.py --seed 20260902  # GBDT nested, 90.6% (seeds 7 / 101 -> 90.8 / 91.1)
RERANKER_TABLE=scratchpad/reranker_table_r2l.jsonl python3 reranker_gbdt_errors.py            # -> scratchpad/gbdt_errors.jsonl (miss taxonomy)
```

`scratchpad/` is git-ignored; the two source files above are committed. The
`reranker_cv.py` "current 5-key hand sort" line IS the R2L-native baseline
on this table (`rank_current_sort` = R2L's own order). The `--target` /
`--drop` flags behave as documented for the FST table; for R2L the `r2l`
feature group is redundant with `rank_current_sort` and the graded
objective is vacuous (correct@1 ≈ 100% for every config).

---

# Related

- `doc/morpheme-dictionary-examples-design.md` — pass-2 design; this plan
  produces its input. (Still refers to `tools/fst/` — now `data/grammar/fst/`.)
- `doc/dev/plans/reranker-objectives-and-analyzers.md` — re-ranker
  objectives (reference@1 vs correct@1 vs R-precision) and per-analyzer
  best models.
- `data/grammar/fst/reranker-experiment.md` — FST-side re-ranker detail.
- `doc/dev/plans/gov-nu-ca-crawling-investigation.md` — the blocked live
  crawl.


# Definitions found in human -produced dicts

For each word and stem, we should also have a field that says which human-produced dictionaries (if any) a defintion was found.

This should cover more than Tusalung and Schneider. It should cover all the dicts in Living Dictionary. But don't include the definitions themselves. Just the fact that a defintion was found.

And by "a definition was found", we don't only mean that the word appears as a headword. If the word appears inside the defintion of another headword, that counts too. In many IU dictionaries, the "headword" is often a stem that is not an actual word. The actual words and their definitions appear inside the defintion for that stem. Even in cases where the headword is an actual word, its definition often contains definitions of other words that are derived from the headword. 