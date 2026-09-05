"""
Runs the FST prototype (data/grammar/fst/lexicon-analyser.hfstol) over every word in
the real gold standard (data/grammar/gold-standard/gold-standard.csv) and
reports the same 4 metrics as histogram.py and
MorphologicalAnalyzer__AccuracyTest.kt: Recall/Precision against
all_correct_decomps (every decomposition R2L itself can produce for that
word) and reference-decomp-present/reference-decomp-in-top-N against
decomp_as_found_in_source (the single Hansard-attested reading).

Unlike histogram.py (a small hand-picked batch, one gold string per word),
this groups ALL addCase entries by surface word first: a few words (e.g.
"anginngittut") have more than one addCase entry with different accepted
decompositions (root homograph ambiguity) -- treating those as one word with
several valid gold parses, not as separate records, matches how the real
:cli suite (AnalyzerCase.hasCorrectDecomposition(), effectively) judges
correctness: any of the listed parses counts.

This is expected to show low coverage for a long time -- data/grammar/fst/
lexicon.lexc is still a minimal, hand-picked lexicon (see
doc/dev/plans/fst-analyzer-plan.md's Milestone 5+ discussion), not the full CSV data.
The point of running the whole gold standard now, this early, is to track
real coverage growth accurately as roots/affixes get added -- and to catch
any FALSE POSITIVE (a word our tiny grammar happens to accept, but with the
WRONG decomposition) immediately, which a small hand-picked batch could
easily miss.

Usage (from data/grammar/fst/, after building lexicon-analyser.hfstol per
phonology.xfscript's header):
    python3 full_corpus_check.py [--show-wrong] [--show-missing N] [--all] [--strict]

The FST is evaluated LENIENT by default (weight <= 1.0, i.e. including
phonology.xfscript's own LENIENT final-consonant-drop rule). That matches
the real :cli accuracy suite -- which evaluates Benoit with
extendedAnalysis/--lenient-decomps ON -- and matches how the gold standard
was built, so it's a like-for-like comparison. --strict counts only
weight-0 analyses (what this script used to do); the histograms happen to
be identical right now, but strict-FST vs. lenient-gold is not a fair
comparison and hand entries were being added to lexicon.lexc to paper over
the difference.

By default this drops the same misspelled/proper-name/borrowed/
decomp-unknown words the real :cli accuracy suite itself doesn't evaluate
(see affix_frequency.load_words's own docstring), so the reported
percentage is directly comparable to AGENTS.md's own :cli figures
(670+247=917/919, 99.8%) with no extra flag needed. --all switches to the
permissive population instead (every gold word with a decomposition,
flagged ones included) -- use that for gap-finding runs, since it surfaces
more edge cases and catches FALSE POSITIVEs a fair-only run could miss;
just don't compare its percentage against the :cli figures, since it's a
different population.
"""
import argparse
from collections import defaultdict

from affix_frequency import load_words
from gold_standard_csv_reader import aggregate_metrics, is_flagged, load_gold_standard, print_aggregate_metrics, word_metrics
from histogram import hfst_analyses, parse_gold, parse_hfst_analysis


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
    parser.add_argument("--all", action="store_true",
                         help="include the words the real :cli accuracy suite itself "
                              "skips (misspelled/proper-name/borrowed/decomp-unknown) "
                              "instead of the default fair comparison against "
                              "AGENTS.md's own 919-word :cli figures -- use for "
                              "gap-finding runs, not for tracking the comparable "
                              "percentage")
    parser.add_argument("--strict", action="store_true",
                         help="count only weight-0 FST analyses. The DEFAULT is "
                              "lenient (weight <= 1.0), matching the real :cli "
                              "accuracy suite -- which evaluates Benoit with "
                              "extendedAnalysis/--lenient-decomps ON -- and matching "
                              "how the gold standard itself was built. Strict FST vs. "
                              "lenient gold is not a like-for-like comparison.")
    args = parser.parse_args()

    lenient = not args.strict
    grouped = group_by_word(load_words(exclude_flagged=not args.all))
    hansard_cases = load_gold_standard("hansard")

    metrics_by_word = {}
    all_correct_count_by_word = {}
    wrong_words = []
    missing_words = []

    for word, gold_morph_lists in grouped.items():
        reference_parses = [tuple(morphemes) for morphemes in gold_morph_lists]
        case = hansard_cases.get(word)
        # all_correct_decomps is only populated for FAIR words (empty for
        # flagged ones, and for words not in the "hansard" source) -- with
        # --all, a flagged word simply has no all_correct data to compare
        # against, so it's excluded from the Recall average (see
        # aggregate_metrics) but still contributes to Precision/reference-
        # present via reference_parses.
        all_correct_parses = (
            [tuple(parse_gold(g)) for g in case.all_correct_decomps] if case else []
        )
        analyses = hfst_analyses(word, lenient=lenient)
        hfst_parses = [tuple(parse_hfst_analysis(a)) for a in analyses]

        m = word_metrics(hfst_parses, all_correct_parses, reference_parses)
        metrics_by_word[word] = m
        all_correct_count = len(set(all_correct_parses))
        all_correct_count_by_word[word] = all_correct_count

        if m.matched == 0 and all_correct_count > 0:
            wrong_words.append((word, reference_parses, hfst_parses))
        if m.produced == 0:
            missing_words.append(word)

    agg = aggregate_metrics(metrics_by_word, all_correct_count_by_word)
    print(f"Full gold-standard corpus check ({agg.total_words} distinct words, "
          f"{'STRICT' if args.strict else 'lenient'} FST, "
          f"{'all words' if args.all else 'fair vs. :cli'}):")
    print_aggregate_metrics(agg)

    if args.show_wrong and wrong_words:
        print(f"\nWords where NONE of the FST's own output matched all_correct_decomps (matched == 0):")
        for word, reference_parses, hfst_parses in wrong_words:
            print(f"  {word}: reference={reference_parses} hfst={hfst_parses}")

    if args.show_missing:
        print(f"\nFirst {args.show_missing} words with no FST output at all:")
        for word in missing_words[: args.show_missing]:
            print(f"  {word}")


if __name__ == "__main__":
    main()
