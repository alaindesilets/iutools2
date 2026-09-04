"""
Helper for strict regression checking across a series of lexicon.lexc/
phonology.xfscript changes: writes the exact SET of gold-standard words
currently analyzed correctly (first-decomposition-correct OR
correct-but-not-first -- either means the right decomposition was found)
to a file, one word per line. Run before and after a change, then diff the
two files: every word in the "before" snapshot must still appear in the
"after" snapshot (no regressions), and "after" should be a strict superset
(new coverage) -- not just a bigger total count, which could hide a
regression offset by an unrelated gain.

Usage:
    python3 snapshot_correct.py <output-file>
"""
import sys
from collections import defaultdict

from affix_frequency import load_words
from histogram import categorize, hfst_analyses, parse_hfst_analysis


def main():
    out_path = sys.argv[1]
    grouped = defaultdict(list)
    for word, morphemes in load_words():
        grouped[word].append(list(morphemes))

    correct = []
    for word, gold_parses in grouped.items():
        # lenient, matching full_corpus_check.py's default and the real
        # :cli accuracy suite (which runs Benoit with --lenient-decomps).
        analyses = hfst_analyses(word, lenient=True)
        hfst_parses = [parse_hfst_analysis(a) for a in analyses]
        category = categorize(gold_parses, hfst_parses)
        if category in ("first-decomposition-correct", "correct-but-not-first"):
            correct.append(word)

    with open(out_path, "w", encoding="utf-8") as f:
        for word in sorted(correct):
            f.write(word + "\n")
    print(f"{len(correct)} correctly-analyzed words written to {out_path}")


if __name__ == "__main__":
    main()
