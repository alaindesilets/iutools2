"""
Runs the FST prototype (tools/fst/lexicon-analyser.hfstol) over every word in
the real gold standard (cli/src/test/kotlin/org/iutools/morph/
MorphAnalGoldStandard_Hansard.kt and MorphAnalGoldStandard_WordsThatFailedBefore.kt)
and reports the same 4-category histogram as histogram.py and the existing
919-word :cli regression suite -- first-decomposition-correct /
correct-but-not-first / correct-not-present / no-decomps.

Unlike histogram.py (a small hand-picked batch, one gold string per word),
this groups ALL addCase entries by surface word first: a few words (e.g.
"anginngittut") have more than one addCase entry with different accepted
decompositions (root homograph ambiguity) -- treating those as one word with
several valid gold parses, not as separate records, matches how the real
:cli suite (AnalyzerCase.hasCorrectDecomposition(), effectively) judges
correctness: any of the listed parses counts.

This is expected to show low coverage for a long time -- tools/fst/
lexicon.lexc is still a minimal, hand-picked lexicon (see
doc/fst-analyzer-plan.md's Milestone 5+ discussion), not the full CSV data.
The point of running the whole gold standard now, this early, is to track
real coverage growth accurately as roots/affixes get added -- and to catch
any FALSE POSITIVE (a word our tiny grammar happens to accept, but with the
WRONG decomposition) immediately, which a small hand-picked batch could
easily miss.

Usage (from tools/fst/, after building lexicon-analyser.hfstol per
phonology.xfscript's header):
    python3 full_corpus_check.py [--show-wrong] [--show-missing N] [--fair] [--strict]

The FST is evaluated LENIENT by default (weight <= 1.0, i.e. including
phonology.xfscript's own LENIENT final-consonant-drop rule). That matches
the real :cli accuracy suite -- which evaluates Benoit with
extendedAnalysis/--lenient-decomps ON -- and matches how the gold standard
was built, so it's a like-for-like comparison. --strict counts only
weight-0 analyses (what this script used to do); the histograms happen to
be identical right now, but strict-FST vs. lenient-gold is not a fair
comparison and hand entries were being added to lexicon.lexc to paper over
the difference.

--fair drops the same misspelled/proper-name/borrowed/decomp-unknown
words the real :cli accuracy suite itself doesn't evaluate (see
affix_frequency.load_words's own docstring) -- use it specifically when
comparing this prototype's percentage against AGENTS.md's own :cli
figures (670+247=917/919, 99.8%), so the two numbers are over the same
population. Default stays permissive (includes everything with a
decomposition): this project's own FST development has deliberately
used the wider net throughout, since it finds more gaps to fix.
"""
import argparse
from collections import Counter, defaultdict

from affix_frequency import load_words
from histogram import categorize, hfst_analyses, parse_hfst_analysis


def group_by_word(entries):
    """[(word, morphemes), ...] -> {word: [morphemes, morphemes, ...]}
    (one list entry per addCase alternative for that surface word)."""
    grouped = defaultdict(list)
    for word, morphemes in entries:
        grouped[word].append(morphemes)
    return grouped


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--show-wrong", action="store_true",
                         help="list every 'correct-not-present' (false positive) word")
    parser.add_argument("--show-missing", type=int, default=0, metavar="N",
                         help="list the first N 'no-decomps' (rejected) words")
    parser.add_argument("--fair", action="store_true",
                         help="drop the words the real :cli accuracy suite itself "
                              "skips (misspelled/proper-name/borrowed/decomp-unknown), "
                              "for a like-for-like comparison against AGENTS.md's own "
                              "919-word :cli figures")
    parser.add_argument("--strict", action="store_true",
                         help="count only weight-0 FST analyses. The DEFAULT is "
                              "lenient (weight <= 1.0), matching the real :cli "
                              "accuracy suite -- which evaluates Benoit with "
                              "extendedAnalysis/--lenient-decomps ON -- and matching "
                              "how the gold standard itself was built. Strict FST vs. "
                              "lenient gold is not a like-for-like comparison.")
    args = parser.parse_args()

    lenient = not args.strict
    grouped = group_by_word(load_words(exclude_flagged=args.fair))

    histogram = Counter()
    wrong_words = []
    missing_words = []

    for word, gold_morph_lists in grouped.items():
        gold_parses = [list(morphemes) for morphemes in gold_morph_lists]
        analyses = hfst_analyses(word, lenient=lenient)
        hfst_parses = [parse_hfst_analysis(a) for a in analyses]

        category = categorize(gold_parses, hfst_parses)
        histogram[category] += 1

        if category == "correct-not-present":
            wrong_words.append((word, gold_parses, hfst_parses))
        elif category == "no-decomps":
            missing_words.append(word)

    total = len(grouped)
    print(f"Full gold-standard corpus check ({total} distinct words, "
          f"{'STRICT' if args.strict else 'lenient'} FST):")
    for category in [
        "first-decomposition-correct",
        "correct-but-not-first",
        "correct-not-present",
        "no-decomps",
    ]:
        count = histogram[category]
        print(f"  {count:4d}  ({100 * count / total:5.1f}%)  {category}")

    if args.show_wrong and wrong_words:
        print(f"\n'correct-not-present' words (FST accepted, but with the wrong analysis):")
        for word, gold_parses, hfst_parses in wrong_words:
            print(f"  {word}: gold={gold_parses} hfst={hfst_parses}")

    if args.show_missing:
        print(f"\nFirst {args.show_missing} 'no-decomps' (rejected) words:")
        for word in missing_words[: args.show_missing]:
            print(f"  {word}")


if __name__ == "__main__":
    main()
