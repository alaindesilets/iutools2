"""
Decomposition-volume and wall-clock comparison between the FST prototype
(lexicon-analyser.hfstol) and the real analyzer (Benoit's own
MorphologicalAnalyzer_R2L), over the 10,000-most-frequent-Hansard-word-form
population in hansard-cache/ -- a much larger and less curated population
than the 922-word --fair gold standard the earlier same-shaped comparison
(see project memory) was run over.

Inputs (all in hansard-cache/, see hansard-cache/README.md for how they
were built):
  - top10k_words.txt          -- syllabics word forms, most frequent first.
  - top10k_words_roman.txt    -- the SAME words, Roman-transliterated via
    the real Syllabics.transcodeToRoman (org.iutools.morph.cli.TranscodeTool,
    a throwaway JVM entry point -- not part of the CLI's own --word/
    --interactive/--pipeline argument surface), line-aligned with the file
    above. The FST only accepts Roman input; Benoit's own decomps file
    keeps the original syllabics "word" field but its decomposition
    strings are already Roman internally (transcoded by the real analyzer
    itself), so no separate Roman version of Benoit's own output is
    needed.
  - top10k_words_benoit_decomps.jsonl -- Benoit's own raw output (9,999 of
    the 10,000 words; one crashed the JVM, see the cache's own README).

This is a VOLUME/SPEED comparison only (no gold labels exist for this
population, so first-decomposition-correct/etc. can't be measured here --
that's what the --fair gold standard is for). Restricted to the 9,999
words Benoit's own cache actually covers, so both sides are compared over
the identical population.
"""
import json
import subprocess
import time
from pathlib import Path

HERE = Path(__file__).parent
ANALYSER = HERE / "lexicon-analyser.hfstol"
SYLLABICS_PATH = HERE / "hansard-cache" / "top10k_words.txt"
ROMAN_PATH = HERE / "hansard-cache" / "top10k_words_roman.txt"
BENOIT_PATH = HERE / "hansard-cache" / "top10k_words_benoit_decomps.jsonl"


def load_word_pairs() -> list[tuple[str, str]]:
    """[(syllabics, roman), ...], line-aligned, in frequency order."""
    syllabics = SYLLABICS_PATH.read_text(encoding="utf-8").splitlines()
    roman = ROMAN_PATH.read_text(encoding="utf-8").splitlines()
    assert len(syllabics) == len(roman), (
        f"{SYLLABICS_PATH.name} has {len(syllabics)} lines but "
        f"{ROMAN_PATH.name} has {len(roman)} -- re-run TranscodeTool"
    )
    return list(zip(syllabics, roman))


def load_benoit() -> dict[str, dict]:
    """{syllabics_word: {"decomp_count": int, "elapsed_msecs": int}}"""
    out = {}
    with BENOIT_PATH.open(encoding="utf-8") as f:
        for line in f:
            rec = json.loads(line)
            out[rec["word"]] = {
                "decomp_count": len(rec.get("decompositions") or []),
                "elapsed_msecs": rec["elapsedMSecs"],
            }
    return out


def fst_analyses_batch(roman_words: list[str]) -> dict[str, list[str]]:
    """Runs hfst-lookup ONCE over every word (one subprocess, not one per
    word -- 10,000 subprocess spawns would dominate the timing otherwise).
    hfst-lookup separates each input word's own block of output with a
    blank line and prefixes the first line of each block with '> ' (its
    interactive-mode prompt) -- splitting on blank lines and ignoring the
    prompt/word column (never used, same as histogram.hfst_analyses)
    keeps blocks aligned 1:1 with roman_words in input order."""
    result = subprocess.run(
        ["hfst-lookup", str(ANALYSER)],
        input="\n".join(roman_words) + "\n",
        capture_output=True,
        text=True,
        check=True,
    )
    blocks = result.stdout.split("\n\n")
    out = {}
    for word, block in zip(roman_words, blocks):
        analyses = []
        for line in block.splitlines():
            fields = line.split("\t")
            if len(fields) != 3:
                continue
            _, analysis, weight = fields
            if weight.strip() == "inf":
                continue
            analyses.append(analysis)
        out[word] = analyses
    return out


def main():
    pairs = load_word_pairs()
    benoit = load_benoit()

    # Restrict to the population Benoit's cache actually covers, so both
    # sides are measured over the identical set of words.
    pairs = [(syll, rom) for syll, rom in pairs if syll in benoit]
    roman_words = [rom for _syll, rom in pairs]
    print(f"Comparing over {len(pairs)} words (of 10,000; "
          f"{10000 - len(pairs)} missing from Benoit's cache -- see its own README)")

    start = time.perf_counter()
    fst = fst_analyses_batch(roman_words)
    fst_wall_secs = time.perf_counter() - start

    fst_raw_total = 0
    fst_dedup_total = 0
    fst_zero_decomp_words = 0
    benoit_total = 0
    benoit_elapsed_total_msecs = 0
    benoit_zero_decomp_words = 0

    for syll, rom in pairs:
        analyses = fst[rom]
        fst_raw_total += len(analyses)
        fst_dedup_total += len(set(analyses))
        if not analyses:
            fst_zero_decomp_words += 1

        b = benoit[syll]
        benoit_total += b["decomp_count"]
        benoit_elapsed_total_msecs += b["elapsed_msecs"]
        if b["decomp_count"] == 0:
            benoit_zero_decomp_words += 1

    print()
    print(f"{'':30} {'FST':>14} {'Benoit':>14}")
    print(f"{'total decompositions (raw)':30} {fst_raw_total:>14,}")
    print(f"{'total decompositions (deduped)':30} {fst_dedup_total:>14,} {benoit_total:>14,}")
    print(f"{'words with zero decomps':30} {fst_zero_decomp_words:>14,} {benoit_zero_decomp_words:>14,}")
    print(f"{'decomps per word (deduped, avg)':30} {fst_dedup_total / len(pairs):>14.2f} "
          f"{benoit_total / len(pairs):>14.2f}")
    print(f"{'FST-vs-Benoit decomp-count ratio':30} {fst_dedup_total / benoit_total:>14.2f}x")
    print()
    print(f"{'wall-clock (batched call)':30} {fst_wall_secs:>13.2f}s")
    print(f"{'summed per-word analysis time':30} {'':>14} {benoit_elapsed_total_msecs / 1000:>13.2f}s "
          f"(sum of each word's own elapsedMSecs, NOT one wall-clock run)")
    print(f"{'FST-vs-Benoit speed ratio':30} {benoit_elapsed_total_msecs / 1000 / fst_wall_secs:>13.1f}x faster")


if __name__ == "__main__":
    main()
