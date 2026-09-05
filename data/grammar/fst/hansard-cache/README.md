# Hansard-derived analysis cache

## What this is

`top10k_words_benoit_decomps.jsonl`: the real morphological analyzer's
own output (`cli --pipeline --lenient-decomps`, one JSON record per
line, same shape as the CLI's own `--pipeline` format --
`{"word":..., "decompositions":[...], "elapsedMSecs":...}`) for the
**10,000 most frequent distinct word forms** in the Nunavut Hansard
corpus (`tools/hansard-corpus/.../NunavutHansard.iu`).

This is a genuinely different population from the project's own
919/922/985-word gold standard
(`cli/src/test/kotlin/org/iutools/morph/MorphAnalGoldStandard_Hansard.kt`):
the gold standard is a small, hand-curated, hand-verified sample; this
file is the analyzer's raw, UNVERIFIED output over the most common
words in the full corpus -- nobody has confirmed these decompositions
are correct, they're just what the real analyzer itself currently
produces. Treat entries here the same way this project treats any
`*Speculative`-class data: plausible, not verified.

## How the word list was built

1. `tools/hansard-corpus/.../NunavutHansard.iu` is in Inuktitut
   **syllabics**, not the Roman orthography this project's own FST/
   lexicon work uses -- tokenized directly on the Unicode Canadian
   Aboriginal Syllabics block (`U+1400`-`U+167F`, regex `[᐀-ᙿ]+`), NOT
   on Latin letters (a naive `[a-zA-Z]+` tokenizer picks up nothing but
   the rare embedded English/URL fragments in the file -- confirmed
   this the hard way before switching to the syllabics-aware pattern).
2. Counted frequency of each distinct syllabics word FORM across the
   whole 2,585,641-line corpus (1,560,610 distinct types, ~7.75M
   tokens total).
3. Took the top 10,000 by raw frequency -- `top10k_words.txt` in this
   same directory (one syllabics word per line, most frequent first).
4. Ran `cli --pipeline --lenient-decomps` over that list. The CLI's
   own internal pipeline transcodes syllabics to Roman automatically
   (`Syllabics.transcodeToRoman`, already wired into
   `MorphologicalAnalyzer_R2L`'s own entry point) -- no separate
   transliteration step was needed on this project's side.
   `--lenient-decomps` matters: it's OFF by default in the CLI wrapper
   even though the core engine's own `extendedAnalysisIn` parameter
   defaults to `true` internally -- without the flag, ~2-3% of words
   spuriously show zero decompositions.

## A real crash hit while generating this, worth knowing about

One word (out of the original 10,000), `ᐃᓕᓴᖅᑐᑦ`, crashed the JVM with
`OutOfMemoryError: Java heap space` at the default heap size --
apparently a genuinely pathological case for the real analyzer's own
recursive search, encountered "in the wild" outside the curated gold
standard (which by construction never contains anything the analyzer
can't handle in reasonable time/memory). A larger heap (`-Xmx6g`) hit
a HARD container memory ceiling instead (`Killed`, SIGKILL from the
Linux OOM-killer, not a catchable JVM exception) -- `-Xmx3g` in
smaller batches (300 words/run) got through the rest cleanly. That one
word was skipped rather than chased further -- **9,999 of the 10,000
words have a result in this file; `ᐃᓕᓴᖅᑐᑦ` alone is missing.**
Worth a look some day as its own bug report (a real word blowing up
the real analyzer's memory), separate from anything in this cache.

## Files here

- `top10k_words.txt` -- the 10,000 syllabics word forms, most frequent
  first, one per line (the INPUT to step 4 above).
- `top10k_words_benoit_decomps.jsonl` -- the analyzer's own raw output
  for 9,999 of those words (see the crash note above), one JSON
  record per line.
- `top10k_words_roman.txt` -- the same 10,000 words, Roman-
  transliterated, line-aligned 1:1 with `top10k_words.txt` (line N of
  each file is the same word in the two orthographies). Built via a
  throwaway JVM entry point,
  `cli/src/main/kotlin/org/iutools/morph/cli/TranscodeTool.kt` (reads
  syllabics lines from stdin, prints `Syllabics.transcodeToRoman(...)`
  results to stdout -- reuses the real transcoding logic rather than
  reimplementing the Unicode mapping table in Python, per AGENTS.md's
  "never hand-retype linguistic data" -- not part of the CLI's own
  `--word`/`--interactive`/`--pipeline` argument surface). Needed
  because the FST prototype (`lexicon-analyser.hfstol`) only accepts
  Roman input, unlike Benoit's real analyzer which transcodes
  internally. Regenerate with:
  ```bash
  ./gradlew :cli:installDist
  java -cp "cli/build/install/cli/lib/*" org.iutools.morph.cli.TranscodeToolKt \
    < data/grammar/fst/hansard-cache/top10k_words.txt \
    > data/grammar/fst/hansard-cache/top10k_words_roman.txt
  ```

## FST vs Benoit: volume/speed comparison over this 10k-word population

`data/grammar/fst/hansard_volume_speed.py` runs the FST (`hfst-lookup`, one
batched call over all 10,000 Roman words) and compares raw
decomposition volume and wall-clock speed against this cache's own
Benoit numbers, over the 9,999-word population both sides cover. No
gold labels exist for this population (that's what the 922-word
`--fair` gold standard is for -- see `topn_stats.py`/
`full_corpus_check.py`), so this is volume/speed only, not accuracy.

Measured 2026-08-22:

| | FST | Benoit |
|---|---|---|
| total decompositions (deduped) | 217,459 | 152,153 |
| words with zero decomps | 2,081 | 1,547 |
| decomps per word (avg) | 21.75 | 15.22 |

FST produces 1.43x as many candidate decompositions per word as Benoit
over this much broader, uncurated population (vs 1.18x measured on the
922-word `--fair` gold standard -- expected, since that set is exactly
what the FST's own development has been tuned against, while this 10k
set reaches far more roots/words the lexicon was never specifically
checked against). The FST also returns zero decomps for more words
here (2,081 vs Benoit's 1,547) -- consistent with `--fair` coverage
being 920/922 rather than 100%, now visible at a larger, more
representative scale.

Speed: the FST's single batched `hfst-lookup` call took 12.36s for all
9,999 words; Benoit's own per-word `elapsedMSecs` sum to 789.38s
(**63.8x** more, though not a strictly apples-to-apples wall-clock
comparison -- Benoit's number is a sum of separate per-word timings
excluding JVM startup, run across several JVM invocations when this
cache was built, not one continuous run).

## Intended uses

1. **An analysis cache for common words** -- looking up a frequent
   word's decomposition(s) here avoids re-running the (comparatively
   slow) real analyzer for words already covered.
2. **A larger, independent source for morpheme-frequency-based tie-
   breaking** (see `data/grammar/fst/benoit_sort.py`'s own
   `sort_with_frequency_tiebreak`, which currently builds its
   frequency table from the gold standard alone). Tested this
   directly (2026-08-22): reranking the --fair gold set with a
   frequency table built from this Hansard cache's own top-1 picks
   performed slightly WORSE than the gold-only table (74.5% vs 74.9%
   at top-1), and combining both barely changed anything (74.3%).
   Read literally this looks like "more data didn't help", but it's
   confounded: the gold-only table has an "in-domain" advantage since
   it's built from (and tested against) the same 922 words, while this
   cache is genuinely held-out data whose value is more likely to show
   up on words OUTSIDE the gold standard -- which isn't measurable
   without a second independently-labeled test set. Not clear cut
   either way; noted here rather than silently discarded.

## To regenerate or extend

```bash
# From tools/hansard-corpus/Nunavut-Hansard-Inuktitut-English-Parallel-Corpus-3.0/:
python3 -c "
import re, collections
SYLL_RE = re.compile(r'[᐀-ᙿ]+')
counter = collections.Counter()
with open('NunavutHansard.iu', encoding='utf-8') as f:
    for line in f:
        for w in SYLL_RE.findall(line):
            counter[w] += 1
with open('top10k_words.txt', 'w', encoding='utf-8') as f:
    for w, _ in counter.most_common(10000):
        f.write(w + '\n')
"

# From the repo root, in manageable batches (a single huge --pipeline
# run risks losing everything after one crash -- see the crash note
# above):
split -l 300 top10k_words.txt /tmp/chunk_
for chunk in /tmp/chunk_*; do
  JAVA_OPTS="-Xmx3g" cli/build/install/cli/bin/cli --pipeline \
    --lenient-decomps --timeout-secs 5 \
    < "$chunk" >> top10k_words_benoit_decomps.jsonl \
    2>> top10k_words_benoit_decomps.err
done
```
