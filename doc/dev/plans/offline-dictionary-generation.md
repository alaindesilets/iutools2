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

**Preliminary result (2026-09-08, agent BLEU PALE):** linear
pairwise-logistic, 10-fold grouped CV, `--loo` (honest freq features):

| | fair reference@1 | P@3 | MRR |
|---|---|---|---|
| R2L native rank-1 | 73.1% (670/917) | 85.6% | 0.807 |
| **learned re-ranker** | **81.1% (744/917)** | **97.3%** | 0.893 |

**+8 points from the linear baseline alone.** The GBDT (the strong model,
`reranker_gbdt_nested.py`) was started, not finished — the FST version of
the same pipeline reaches ~90% P@1, so expect similar or better on R2L's
shorter, cleaner lists. Build work is for agent BLEU; recreatable code in
the Appendix. The strategic layer is
`doc/dev/plans/reranker-objectives-and-analyzers.md`; the FST-side detail
is `data/grammar/fst/reranker-experiment.md`.

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
3. **R2L `reference@1` re-ranker.** Finish the GBDT on the R2L table (agent
   BLEU); add the stem-recurrence feature; expand training data (LLM silver
   standard, `:cli --rank-decomps` — see the re-ranker plan). Freeze as a
   Kotlin re-ranker.
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

# Appendix — recreatable session artifacts (R2L re-ranker, 2026-09-08)

Not committed (agent BLEU owns this build). All in `data/grammar/fst/`.

**`reranker_cv.py` — one line:** make the table path overridable so the
existing CV/GBDT machinery runs on an R2L table unchanged:

```python
import os
TABLE = os.environ.get("RERANKER_TABLE", "scratchpad/reranker_table.jsonl")
```

**`build_reranker_table_r2l.py` — new:** R2L analogue of
`build_reranker_table.py`. Candidates from
`hansard-cache/top10k_words_benoit_decomps.jsonl` (R2L over the top-10k;
covers **all 919 fair gold words** → no live CLI run). Same identity-free
`features()` / `add_relative_features()`, imported unchanged. Output
`scratchpad/reranker_table_r2l.jsonl`, identical schema to
`scratchpad/reranker_table.jsonl`. Core logic:

- syl→rom map from `hansard-cache/top10k_words{,_roman}.txt`.
- per gold word (`load_gold_standard`, both sources, `is_flagged` → `fair`):
  candidates = R2L cache decomps (roman-keyed); parse each with
  `histogram.parse_gold` → `[(canonical, id), …]`.
  - `label = 1` iff the candidate tuple ∈ `{parse_gold(d) for d in
    case.correct_decomps}` (the single reference).
  - `is_correct = label or tuple ∈ {parse_gold(d) for d in
    case.all_correct_decomps}`.
  - `_sort_key = rank` (candidate index → `feat.rank_current_sort`).
  - `feat["weight"] = 0.0` (per-candidate strict/lenient not in this cache;
    later tier: pull `decomps_lenient` from the S1 mined file).
  - drop words with no R2L candidate, or where R2L never produced the
    reference.

**Run:**

```sh
cd data/grammar/fst
python3 build_reranker_table_r2l.py
RERANKER_TABLE=scratchpad/reranker_table_r2l.jsonl python3 reranker_cv.py --loo
RERANKER_TABLE=scratchpad/reranker_table_r2l.jsonl python3 reranker_gbdt_nested.py
```

Baseline to beat: R2L native = 73.1% fair `reference@1` (also
`reranker_cv.py`'s "current 5-key hand sort" line on an R2L table, since
`rank_current_sort` = R2L's own order there).

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
