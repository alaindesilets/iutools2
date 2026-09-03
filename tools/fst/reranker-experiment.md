# A learned re-ranker over the FST's decomposition candidates

An offline experiment (Alain's idea, started 2026-09-02): the FST usually
*produces* the correct decomposition of a word somewhere in its candidate
list, but not always first. Can a model trained on the gold standard pick
the correct one out of that list better than the current hand-tuned sort?

Short answer: **yes, by a lot.** A pure-standard-library gradient-boosted
decision-tree re-ranker reaches **~90.6% first-decomposition-correct** on
the `--fair` gold standard (nested cross-validation, 3 seeds), versus
**73.2%** for the current 5-key sort. That is +17 points / ~+160 words.

Nothing here is wired into `:core`, `:cli`, or the shipped FST yet -- it is
tooling under `tools/fst/`, and the model is a set of `.py` scripts plus a
feature table. Porting the model to a Kotlin re-ranker in the FST path is a
separate, not-yet-done step.

---

## The number to beat

The baseline is **671 / 919 fair first-decomposition-correct (73.2%)** --
the FST's own candidates *ranked by the shared 5-key sort*
(`benoit_sort.sort_with_frequency_tiebreak`, which mirrors
`MorphologicalAnalyzer.sortDecompositions` and already equals R2L /
Uqailaut: 673/919).

The often-quoted **261** is `full_corpus_check.py`'s *unranked* number
(raw `hfst-lookup` emission order) -- not a ranking baseline. Real headroom
over the hand sort is ~248 words, not ~658.

The **P@3** of the hand sort is 84.6%, and of the re-ranker ~98.5%: once a
re-ranker is in play, the correct parse is almost always in the top 3. The
ceiling for pure re-ranking is set by candidate *generation*, not ranking.

---

## Data

`build_reranker_table.py` -> `scratchpad/reranker_table.jsonl`
(git-ignored, ~18 MB, regenerate with `python3 build_reranker_table.py`
from `tools/fst/`):

| | |
|---|---|
| rows (one per word x candidate) | 20 886 |
| words | 955 (917 `--fair`) |
| words dropped (correct parse never produced by the FST -- unrankable) | 30 |
| positive candidates (match a gold parse) | 958 |

Each row: `word`, `fair`, `decomp`, `parts` (`[[canonical, id], ...]`),
`label` (0/1), and a `feat` dict.

### Feature groups (`reranker_cv.FEATURE_GROUPS`)

| group | what it captures |
|---|---|
| `shape` | n morphemes, canonical lengths, wordlen / n-morph, duplicate ids |
| `cat` | grammatical-category-code class counts, category switches, `type_violations` (dead -- see below), ends-with-ending |
| `surface` | edit distance canonical-concat vs. surface word, normalized, prefix match |
| `relative` | rank under the current 5-key sort, within-word feature ranks, n candidates |
| `weight` | FST path weight (only ever 0.0 strict / 1.0 lenient) |
| `freq` | sum / mean / min / root of `MorphemeFrequencyPrior` counts + n-zero-freq -- the identity-derived escalation tier |
| `bigram` | P(id_{k+1} \| id_k) from the 10k-Hansard cache: min / mean / n-zero transitions + root->first-affix |
| `backoff` | root-OOV flag, type-level root-id frequency, root char-bigram mean |
| `r2l` | per-word lookup of Uqailaut's output for the same word (`r2l_top1` / `r2l_produced` / `r2l_rank_inv`) |

`RELFREQ_GROUP` (within-word relative frequency) exists in `reranker_cv.py`
but is deliberately **not** in `FEATURE_GROUPS` -- added and rejected in
round 6, see "Piste 1" below.

The identity-derived groups (`freq`, `bigram`, `backoff`, `relfreq`) are
rebuilt from a count table in `reranker_cv.recompute_stats`, so
`reranker_cv.py --loo` can rebuild them per CV fold with that fold's gold
words excluded from the 10k cache -- the leak check. `r2l` is not a corpus
statistic and is not rebuilt per fold.

---

## Progression

All numbers are grouped-by-word cross-validation, `--fair` P@1, FST-only
(no `r2l` group -- see "the `r2l` feature" below).

| model / feature step | fair P@1 |
|---|---|
| current 5-key hand sort (= R2L) | 73.2% |
| linear pairwise-logistic, identity-free only (`--drop freq`) | 57.4% |
| linear, `freq` only | 62.5% |
| linear, all features | 80.4% |
| linear, + `bigram` | 82.1% |
| linear, + `r2l` feature | 82.9% |
| linear, + quantile bucketing (`--buckets 10`) | ~85.7% flat / ~86% nested |
| **GBDT (stdlib mini), settled config** | **~90.6% nested / 91.3% flat** |
| ceiling (correct parse present among candidates) | 917 / 917 |

Frequency is the dominant single signal: dropping it costs ~23 points;
identity-free features alone lose to the hand sort. But `shape` + `cat` +
`surface` add ~18 points *on top of* frequency -- they only looked like
noise in isolation. Leakage was a non-issue throughout: fixed vs. per-fold
(`--loo`) frequency table moved the number ~0.2 pt (the 10k-corpus counts
swamp the ~95 gold words removed per fold).

### The linear model, consolidated (nested CV)

`reranker_nested_cv.py` -- outer 10-fold grouped by word; inner 3-fold picks
`(buckets, L2)` from a grid so the held-out fold influenced no
hyperparameter. Features = `shape + cat + surface + relative + weight +
freq + bigram` (not `backoff`).

| | fair P@1 | P@3 | MRR |
|---|---|---|---|
| hand sort / R2L | 73.2% | 84.6% | 0.805 |
| FST-only re-ranker, nested CV, 3 seeds | 85.6 / 86.4 / 86.6 % (mean 86.2) | ~97.5% | ~0.92 |
| + `r2l` feature, nested CV | 86.9% | 98.5% | 0.926 |

Flat CV and nested CV agree within ~0.5 pt -- the pipeline is sound.

### The `r2l` feature

`r2l_top1` / `r2l_produced` / `r2l_rank_inv` per word require running
Uqailaut (R2L) on that word. In nested CV the feature adds only +0.3-0.7 pt.
**Not worth running a second analyzer for.** The deployable model is
FST-only.

---

## The GBDT (round 5)

This devcontainer has no `pip` / `numpy` / `sklearn` / LightGBM and no
`sudo`, so `reranker_gbdt.py` is a **pure-standard-library** histogram-based
GBDT: pairwise-logistic boosting, XGBoost-style Newton leaves
(`leaf = -sum g / (sum h + lambda)`), row + feature subsampling, every
feature pre-binned into quantile bins once. It reuses `reranker_cv` for the
data and features (raw values -- trees are invariant to monotone
transforms).

### Hyperparameter sweep (flat 10-fold CV, FST-only)

| trees | depth | lr | other | fair P@1 |
|---|---|---|---|---|
| 120 | 3 | 0.1 | (first run) | 89.5% |
| 250 | 3 | 0.05 | | 89.4% |
| **150** | **4** | **0.1** | | **91.3%** |
| 150 | 4 | 0.1 | featsub 1.0 | 91.4% |
| 150 | 4 | 0.1 | lambda 2.0 | 91.3% |
| 200 | 4 | 0.1 | | 91.3% |
| 300 | 4 | 0.05 | | 90.9% |
| 120 | 5 | 0.1 | | 90.7% |
| 100 | 6 | 0.1 | | 90.5% |
| 150 | 4 | 0.1 | leaf 10 | 90.6% |
| 200 | 4 | 0.07 | | 90.5% |
| 250 | 4 | 0.05 | | 90.3% |

**Tree depth 3 -> 4 is the only real lever (+1.8 pts).** Depth 5-6 slightly
worse; feature subsampling 0.6 vs 1.0 is a wash; smaller leaves hurt;
slower learning rate with more trees does not help. Settled config:

```
trees 150   depth 4   lr 0.1   leaf 20   lambda 1.0
bins 32     rowsub 0.4   featsub 0.6   neg 8 (subsampled negatives / positive)
```

### Nested CV (the honest number)

`reranker_gbdt_nested.py` -- outer 10-fold grouped by word; inner 3-fold
picks `(depth, trees)` from `{(3,120), (4,150), (4,200)}` (the only knob
that moved the sweep); everything else fixed at the settled config; bin
edges recomputed from each fit's training rows only.

| seed | fair P@1 | P@3 | MRR |
|---|---|---|---|
| 20260902 | 832 / 917 (90.7%) | 98.6% | 0.947 |
| 7 | 825 / 917 (90.0%) | 98.5% | 0.942 |
| 101 | 835 / 917 (91.1%) | 98.4% | 0.947 |

**Mean 90.6% (band 90.0-91.1), spread ~1.1 pt.** Every outer fold's inner
CV picks depth 4, never depth 3 -- the nonlinearity is robustly chosen.
~0.7 pt shrinkage from the flat-CV 91.3%, the same small gap the linear
model showed.

### Final comparison

| | fair P@1 | P@3 | MRR |
|---|---|---|---|
| hand sort / R2L | 73.2% | 84.6% | 0.805 |
| bucketed-linear, nested CV | ~86% | ~97.5% | ~0.92 |
| **GBDT, nested CV, 3 seeds** | **90.6% (90.0-91.1)** | ~98.5% | ~0.945 |

**Net: +17 points / ~+160 words over R2L parity, +4.5 points over the
bucketed-linear model.**

---

## Error analysis

`reranker_gbdt_errors.py` -> `scratchpad/gbdt_errors.jsonl`: flat 10-fold
CV at the settled config, every fair word held out once, recording where
the first correct candidate landed and the feature vectors of the model's
pick and of the correct parse. 80 misses out of 917.

**Taxonomy.** 62% of misses are rank-2 near-misses; 81% have the correct
parse in the top 3; only **15 words (1.6%)** are out of the top 3 --
unreachable by pure re-ranking without new features that massively
re-order.

**The GBDT has already extracted the easy signal.** Miss rate is 2% on
words the hand sort also gets right, 28% on words the hand sort gets wrong.
Only **12 regressions** where the GBDT breaks a hand-sort win, against
**178 recoveries**. The residual 68 shared misses are the hard core.

**The residual bias is clear and consistent.** On a miss, the model's wrong
pick has:

| feature | mean(pick - correct) | direction |
|---|---|---|
| `edit_dist` | -0.53 | pick is closer to the surface string (94% of misses) |
| `n_morphemes` | -0.48 | pick is shorter (92% of misses) |
| `freq_root` | pick's root is more frequent | |
| `type_violations` | **0.000** | **identical -- dead, every miss ties** |

The GBDT chases surface-literalness and frequency exactly where the correct
Inuktitut analysis needs a *rarer* root, *more* morphophonological
deformation, and *one more* morpheme. It mis-analyses `atuagaq` (the
canonical example from `AGENTS.md`): it picks a 2-morpheme parse with a
frequent root over the correct 3-morpheme parse whose root is OOV.

Conditioned miss rates: `edit_dist >= 3` misses at 20% (vs. 5% for
`<= 1`); `freq_root == 0` at 21% (12 words). About 15 misses have
*identical* shape features and differ only in `freq_sum` -- genuinely
ambiguous without sentence context.

---

## Directions tried and rejected (round 6)

### Piste 1 -- within-word relative frequency features: no gain

Added a `relfreq` group (`freq_root_rank`, `freq_sum_rank`,
`freq_root_rel`, `freq_sum_rel`, `freq_root_is_uniq_max`,
`freq_root_margin`, `freq_root_trap`), computed per word at the end of
`recompute_stats` so `--loo` covers it. The hypothesis: tell the model how
*decisive* a candidate's frequency lead is, relative to the other
candidates of the same word, so it stops chasing a marginal lead.

Every comparison came out <= baseline:

| | fair P@1 |
|---|---|
| GBDT settled config | 91.3% -> 91.1% with `relfreq` |
| GBDT `featsub 1.0` | 91.4% -> 90.7% with `relfreq` |
| linear + `buckets 10` | 86.8% -> 86.3% with `relfreq` |
| GBDT, `relfreq` *instead of* raw `freq` | 89.1% (vs. 91.3%) |

The relative signal is already captured by the raw `freq` features plus the
tree model's interactions, or it adds noise. The feature builder
(`add_relfreq_features`) and its group definition (`RELFREQ_GROUP`) are kept
in `reranker_cv.py` for the record, but the group is **not** in
`FEATURE_GROUPS` and the builder is **not** called -- so every script
reproduces the headline numbers with no extra flags. Re-testing it means
splicing `RELFREQ_GROUP` into `FEATURE_GROUPS` and calling
`add_relfreq_features(rows)` at the end of `recompute_stats`.

### Piste 2 -- real lexc continuation-class legality: mechanical dead end

The idea was to replace the dead `type_violations` / `cat_switches`
features with the grammar's actual morphotactic legality. It does not work,
for concrete reasons:

* **`type_violations == 0` on all 20 886 candidate rows.** The FST never
  emits an agrammatical parse -- the `.lexc` continuation classes already
  enforce exactly this. A "real legality" feature would also be
  ~always-true. There is no legality signal left to extract.
* **The `hfst-lookup` analysis string (`canonical+id` sequence) carries no
  lexicon provenance.** From a candidate alone you cannot tell whether it
  used a hand-picked, `*Generated`, or `*GeneratedSpeculative` entry. The
  speculative-path distinction (a real confidence tier the grammar author
  built) would need the FST rebuilt with marked arc weights.
* **The FST path weight is only 0.0 / 1.0** (strict / lenient). There is no
  fine-grained two-level / phonological-rule cost to split out either.
* `cat_switches` does vary (0-6) but it is legitimate derivational
  structure, not a violation count, and the error analysis shows it has
  ~zero discriminating power on the hard cases.

### The hand layer is not a re-ranker concern

`lexicon.lexc` still has a residual hand-authored layer (see
`gold-standard-fitting.md`). It does not affect the re-ranker:

* The re-ranker is scored only on the 917 `--fair` words where the correct
  parse is *already* among the candidates. *How* the FST produced a
  candidate is irrelevant to picking the right one.
* Hand entries are only ever removed against a rule that preserves
  "the correct decomposition is in the candidate list" coverage
  (the project's standing gold-fitting-cleanup rule). So the 917-word fair
  population -- and therefore the 90.6% -- is stable under that cleanup. No
  hand / non-hand evaluation split is needed.

---

## What is actually left

1. **More gold data.** The GBDT has only ~80 hard examples to learn the
   frequency <-> surface-literalness interaction from. Adjudicating
   200-300 more Hansard words sampled specifically from the hard region
   (`edit_dist >= 3`, `>= 3 morphemes`) is the one remaining features/data
   lever.
2. **Sentence context / an LLM** for the ~15 truly-ambiguous words whose
   shape features are identical. Separate, larger project.
3. **Freeze and port** the ~90.6% GBDT as a Kotlin re-ranker in the FST
   path. Solid as-is; not mutually exclusive with (1).
4. **LightGBM lambdarank** (nonlinear, listwise, monotone constraints,
   SHAP) is still blocked -- no `pip` / `sudo` in this devcontainer.

---

## Reproducing

From `tools/fst/`, with `lexicon-analyser.hfstol` built (see `README.md`):

```bash
python3 build_reranker_table.py          # -> scratchpad/reranker_table.jsonl

# linear baseline / ablations
python3 reranker_cv.py --drop r2l --buckets 10
python3 reranker_cv.py --drop r2l --loo          # per-fold freq table (leak check)
python3 reranker_nested_cv.py --drop r2l         # honest linear number

# GBDT
python3 reranker_gbdt.py --trees 150 --depth 4 --lr 0.1 --drop r2l
python3 reranker_gbdt_nested.py --drop r2l --seed 20260902   # honest GBDT number
python3 reranker_gbdt_errors.py                  # -> scratchpad/gbdt_errors.jsonl
```

`scratchpad/` is git-ignored -- every table, dump, and log is regenerated
by these scripts.

## Files

| script | purpose |
|---|---|
| `build_reranker_table.py` | enumerate FST candidates per gold word, label, extract identity-free features -> `scratchpad/reranker_table.jsonl` |
| `reranker_cv.py` | linear pairwise-logistic re-ranker, grouped 10-fold CV; `--drop` / `--loo` / `--buckets` / `--listwise` / `--dump` / `--ensemble` |
| `reranker_nested_cv.py` | nested CV for the linear model (`--drop`, `--seed`) |
| `reranker_gbdt.py` | pure-stdlib histogram GBDT, flat 10-fold CV; `--trees` / `--depth` / `--lr` / `--bins` / `--leaf` / `--lambda` / `--drop` |
| `reranker_gbdt_nested.py` | nested CV for the GBDT (`--drop`, `--seed`) |
| `reranker_gbdt_errors.py` | miss taxonomy + feature-delta analysis -> `scratchpad/gbdt_errors.jsonl` |
