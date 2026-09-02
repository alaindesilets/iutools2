"""Thin wrapper: apply the FST decomposition dedup + ranking to a bag of raw
transducer outputs and print the result, so a Kotlin test can diff Python's
ordering against Kotlin's MorphologicalAnalyzer_FST for the identical input
(tools/fst/sort-sync-decomps.txt). See SortSyncTest.kt.

Input  (stdin): one "<analysis>\\t<weight>" line per raw hfst-lookup upper-
                side output -- duplicates and weight variants included on
                purpose (they exercise the dedup). Blank lines ignored.
Output (stdout): the surviving decompositions, one "{canonical/id}{...}" per
                line, in ranked order.

Pipeline, identical to MorphologicalAnalyzer_FST.doDecompose:
  1. dedup_preserve_order  -- keep each analysis string once, at its MIN
                              weight (a parse is strict if any path is).
  2. sort_with_frequency_tiebreak -- weight, then longest root, then fewest
                              morphemes, then morpheme-frequency prior, then
                              the decomposition string lexicographically.
"""
import sys

from benoit_sort import sort_with_frequency_tiebreak
from histogram import parse_hfst_analysis
from topn_stats import dedup_preserve_order


def main() -> None:
    pairs = []
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        analysis, weight = line.split("\t")
        pairs.append((analysis, float(weight)))

    for analysis, _weight in sort_with_frequency_tiebreak(dedup_preserve_order(pairs)):
        parts = parse_hfst_analysis(analysis)
        print("{" + "}{".join(f"{c}/{i}" for c, i in parts) + "}")


if __name__ == "__main__":
    main()
