# The Inuktitut word-decomposition dataset

This folder holds a dataset: for each of the 100,000 most common words in
the Nunavut Hansard, a list of grammatically correct morphological
decompositions of that word. Every decomposition in the list is correct;
the list is not guaranteed to be exhaustive.

"The morphological analyzer" used here is **R2L** —
`org.iutools.morph.r2l.MorphologicalAnalyzer_R2L` in `:core`, this
project's port of Benoit Farley's Uqailaut analyzer. It is run once,
offline, over a big word list; the result is this dataset.

Analyzed Inuktitut data is scarce, so this is meant to be useful beyond
iutools and is treated as a real, versioned, published dataset. It is the
first stage (sometimes called "S1") of a longer plan — later stages attach
plain-language meanings to the morphemes — described in
[`doc/dev/plans/analyzed-lexicon-dataset.md`](../../../doc/dev/plans/analyzed-lexicon-dataset.md).

## What if I just want the data

```sh
data/lexicon/decompositions/fetch.sh
```

This downloads the dataset file (`hansard-top100k-decomps.jsonl`, ~240 MB)
and checks it wasn't corrupted in transit. Needs `curl` and `python3`,
nothing else.

## Why isn't the data file in the repo?

It is 240 MB (15 MB compressed). A file that big in git would bloat every
clone of the project forever, and this repo deliberately rejects commits
over 5 MB.

Instead the file is attached to a **GitHub release** of this repo. On the
repo's Releases page, look for a tag like `analyzed-lexicon-v1`; `fetch.sh`
downloads that asset for you.

What *is* in the repo is [`datapackage.json`](datapackage.json) — a
catalogue card for the dataset. It records the download URL, the file's
checksum and size, what each field means, where the data came from, and the
exact command that produced it. `fetch.sh` reads it to know what to
download and how to verify it. (Its format is a small dataset-description
standard called "Frictionless Data Package" — it's just JSON, nothing to
install.)

## What one line of the data looks like

The file has one JSON object per line, most frequent word first. Here is
the entry for *ammalu* ("and"):

```jsonc
{
  "rank": 2,                       // 2nd most common word in the corpus
  "count": 127462,                 // it occurs 127,462 times
  "word_syl": "ᐊᒻᒪᓗ",              // the word, in syllabics
  "word_rom": "ammalu",            // the same word, in Roman letters
  "analyzer_outcome": "completed", // the analyzer finished on this word
                                   //   (else: "timed_out" or "crashed")
  "decomps": [                     // every analysis R2L found, best guess first:
    "{ammalu:ammalu/1c}",                    //   one morpheme
    "{amma:amma/1c}{lu:lu/1q}",              //   two morphemes
    "{amma:angmaq/1v}{lu:luk/tv-imp-1d}"     //   a verb reading
  ],
  "decomps_lenient": [false, false, true],   // for each analysis above: was it
                                             //   only possible by assuming the
                                             //   word dropped a final consonant?
  "n_decomps": 3                             // == number of analyses
}
```

Each analysis is a run of `{written form : morpheme / tag}` pieces. In
`{amma:amma/1c}{lu:lu/1q}`, the letters "amma" spell the morpheme *amma*
and "lu" spells the morpheme *lu*; `1c` / `1q` / `tv-imp-1d` are grammatical
tags that identify the morpheme and its category.

**"lenient" analyses.** Inuktitut words ending in a vowel sometimes drop a
final consonant. R2L can find extra analyses by assuming that happened.
Those are genuine but more speculative, so each is flagged `true` in
`decomps_lenient` and ranked below the solid ones.

*(`r2l_timed_out` and `r2l_error` also appear on each line. They are
leftovers kept so older code doesn't break — `r2l_timed_out` just restates
`analyzer_outcome == "timed_out"`.)*

## What's in this folder

| File | |
|---|---|
| `README.md` | this file |
| `datapackage.json` | the catalogue card (see above); hand-edited when a new version is published |
| `fetch.sh` | download and checksum the published data file |
| `mine_decompositions.py` | the program that builds the dataset from the Hansard corpus |
| `regenerate.sh` | run `mine_decompositions.py` the right way, in one step |
| `hansard-freq-top100k.tsv` | the word list (rank, count, word) — small enough to keep in git |

Not in git: the dataset file itself (`*.jsonl` — it's a release asset) and
`run-manifest.json` (a scratch log the miner writes each run).

## Rebuilding the dataset yourself

You only need this to make a **new version**: a newer corpus, a different
word count, or a change to the analyzer. It takes about 2 hours.

```sh
data/lexicon/decompositions/regenerate.sh
```

It will, in order:

1. **Check you are on the right commit.** `datapackage.json` records which
   commit of iutools2 the current version was built from; the analyzer's
   behaviour depends on it.
2. **Build the command-line analyzer** (`./gradlew :cli:installDist`).
3. **Check the corpus** is present and unmodified against the checksum in
   `datapackage.json`. The corpus is not in git — download the *Nunavut
   Hansard Inuktitut-English Parallel Corpus 3.0.1* from the NRC-CNRC
   Digital Repository and unpack it under
   `tools/hansard-corpus/Nunavut-Hansard-Inuktitut-English-Parallel-Corpus-3.0/`.
4. **Run the miner** (~2 h).
5. **Show how the new word counts differ** from the published version's.

## Why you cannot reproduce it byte-for-byte

The analyzer gives each word up to 20 seconds. About 190 words sit close
enough to that limit that they sometimes finish and sometimes time out,
depending on machine load. One word hangs entirely and is dropped. So two
runs of the exact same command produce slightly different files (~0.2 % of
words differ) with different checksums.

That is why the dataset is *stored and published* rather than *rebuilt on
demand*: the published file is the reference, and `regenerate.sh` gives you
an equivalent one, not an identical one.

## Publishing a new version

1. Build the data file — `regenerate.sh`, or reuse one you already have.
2. Compress it: `gzip -k hansard-top100k-decomps.jsonl`, then note its
   SHA-256 and byte size (`sha256sum`, `stat -c%s`).
3. Update `datapackage.json`:
   - bump `version`
   - set `resources[0].path` to the new asset's URL (predictable:
     `…/releases/download/analyzed-lexicon-v<N>/hansard-top100k-decomps.jsonl.gz`)
   - fill `resources[0].hash` (the `.gz` SHA-256, `sha256:` prefixed) and `bytes`
   - copy the fresh numbers from `run-manifest.json` into `record_counts`
     and the `provenance` block
   - add a row to the table below
4. Commit `datapackage.json`, then `git push`. The release tag will point
   at this commit.
5. Write **Markdown** release notes — lead with a one-paragraph summary,
   then the asset details, a sample record, provenance, checksums, licence.
   Do *not* pass `datapackage.json` as the notes (GitHub shows it as a wall
   of raw text); link to it instead. The v1 notes are a template.
6. Cut the release:
   ```sh
   gh release create analyzed-lexicon-v<N> hansard-top100k-decomps.jsonl.gz \
     --title "Inuktitut word decompositions — v<N>" --notes-file <your-notes>.md
   ```
7. Verify: `fetch.sh` should download and checksum the new asset cleanly.

Never replace the file on an existing release. New data = new version
number, new release tag, new `datapackage.json` commit.

## Versions

| version | release tag | date | built from commit | words | notes |
|---|---|---|---|---|---|
| 1.0.0 | `analyzed-lexicon-v1` | 2026-09-06 | `df0889c` | 100,000 | first version; not yet released as of this commit |
