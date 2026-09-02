"""Regenerates tools/fst/sort-sync-decomps.txt -- the frozen input for
SortSyncTest.kt, which checks the Python (benoit_sort.py) and Kotlin
(MorphologicalAnalyzer_FST) FST dedup+ranking produce the identical order.

It is a small CURATED sample, not the whole gold standard: ~30 words'
complete `hfst-lookup --lenient` output, deliberately biased so every part of
the pipeline is exercised in a ~30 KB file rather than 1 MB --

  * words whose output has the SAME analysis string at weight 0 AND weight 1
    (the min-weight dedup); a plain random sample almost never hits these
    (only ~65 of 919 gold words have any),
  * medium-ambiguity words (15-60 parses) so parses tie on the root-length /
    morpheme-count keys and the frequency + lexicographic tie-breaks decide,
  * a plain random handful for breadth on the primary keys.

Whole per-word blocks are kept (not random individual lines) so the weight-0
/ weight-1 pairs and the tie clusters stay intact.

Run from tools/fst/ after the transducer changes; commit the result.
"""
import random
import subprocess
import sys

from affix_frequency import load_words
from full_corpus_check import group_by_word

ANALYSER = "lexicon-analyser.hfstol"
OUT = "sort-sync-decomps.txt"
SEED = 20260902


def per_word_blocks() -> dict[str, list[str]]:
    words = sorted(group_by_word(load_words(exclude_flagged=True)))
    res = subprocess.run(
        ["hfst-lookup", ANALYSER],
        input="\n".join(words) + "\n",
        capture_output=True, text=True, check=True,
    )
    blocks: dict[str, list[str]] = {}
    for line in res.stdout.splitlines():
        f = line.split("\t")
        if len(f) != 3 or f[2].strip() == "inf" or float(f[2]) > 1.0:
            continue
        blocks.setdefault(f[0], []).append(f"{f[1]}\t{float(f[2]):g}")
    return blocks


def main() -> None:
    blocks = per_word_blocks()

    def has_weight_dup(w: str) -> bool:
        return len({ln.split("\t")[0] for ln in blocks[w]}) < len(blocks[w])

    rng = random.Random(SEED)
    weight_dup = sorted(w for w in blocks if has_weight_dup(w))[:12]
    mid = sorted(w for w in blocks if 15 <= len(blocks[w]) <= 60)
    sample = sorted(
        set(weight_dup)
        | set(rng.sample(mid, 12))
        | set(rng.sample(sorted(blocks), 12))
    )

    lines: list[str] = []
    for w in sample:
        lines.extend(blocks[w])

    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")

    distinct = len({ln.split("\t")[0] for ln in lines})
    kb = (sum(len(ln) for ln in lines) + len(lines)) // 1024
    n_dup = sum(has_weight_dup(w) for w in sample)
    n_tie = sum(len(blocks[w]) >= 10 for w in sample)
    print(
        f"{OUT}: {len(sample)} words, {len(lines)} lines (~{kb} KB); "
        f"dedup drops {len(lines) - distinct}; {n_dup} weight-dup words; "
        f"{n_tie} words with >=10 parses",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
