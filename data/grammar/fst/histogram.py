"""
Milestone 4 of doc/dev/plans/fst-analyzer-plan.md: runs hfst-lookup over the
gold standard's "fair" words (see gold_standard_csv_reader.py, skipping
misspelled/possibly-misspelled/borrowed/proper-name/decomp-unknown cases,
same population as :cli's own accuracy suite) and reports the same 4
metrics as MorphologicalAnalyzer__AccuracyTest.kt: Recall and Precision
against all_correct_decomps (every decomposition R2L itself can produce for
that word), and reference-decomp-present / reference-decomp-in-top-N
against decomp_as_found_in_source (the single Hansard-attested reading).

This is also the "real, working answer instead of eyeballing results" the
plan calls for on the output-format question: HFST's own analysis string
(e.g. "amit+1v++juq+1vn") is converted here into a list of (canonical, id)
pairs and compared against the same pairs parsed out of Benoit's own
{surface:canonical/id} notation.

Deliberate scope limit: the comparison uses (canonical, id) pairs only, not
full {surface:canonical/id} triples -- it does not attempt to recover which
substring of the input word each morpheme matched. That's a real, separate
problem (HFST's optimized-lookup only exposes whole-string input/output
pairs, not an internal per-morpheme alignment) that doesn't need solving
for *this* comparison: the surface word is already known correct by
construction (it's literally the string fed to hfst-lookup, taken from the
gold standard), so checking that the analyser's own canonical+id sequence
matches the gold canonical+id sequence is sufficient to confirm a correct
decomposition, without also reconstructing surface spans per morpheme.

The words checked here used to be a ~340-word snapshot hand-copied from
MorphAnalGoldStandard_Hansard.kt (never retyped by hand -- see AGENTS.md's
"Preserving data integrity"), taken once because :cli wasn't built as part
of this dev-time tool and the whole gold standard was tedious to keep
hand-copied in sync. That workaround is gone now that
data/grammar/gold-standard/gold-standard.csv exists: GOLD_CASES below reads
the live, complete gold standard from it via gold_standard_csv_reader.py.

Usage (from data/grammar/fst/, after building lexicon-analyser.hfstol per
phonology.xfscript's header):
    python3 histogram.py
"""
import re
import subprocess
from pathlib import Path

from gold_standard_csv_reader import (
    AnalyzerCase,
    WordMetrics,
    aggregate_metrics,
    load_gold_standard,
    print_aggregate_metrics,
    word_metrics,
)

ANALYSER = Path(__file__).parent / "lexicon-analyser.hfstol"


def _is_fair(case: AnalyzerCase) -> bool:
    return not (
        case.is_misspelled
        or case.is_possibly_misspelled
        or case.is_borrowed
        or case.is_proper_name
        or case.decomp_unknown
    )


# (surface word, [gold decomposition strings]) for every "fair" Hansard word
# that has a real decomposition -- more than one string means more than one
# gold-accepted parse (e.g. "anginngittut"'s root is ambiguous between
# "angi" and "angiq").
GOLD_CASES = [
    (word, case.correct_decomps)
    for word, case in load_gold_standard("hansard").items()
    if _is_fair(case) and case.correct_decomps
]

GOLD_MORPHEME_RE = re.compile(r"\{[^:]+:([^/]+)/([^}]+)\}")


def parse_gold(decomposition: str) -> list[tuple[str, str]]:
    """'{aki:aki/1n}{nga:nga/tn-nom-s-4s}' -> [('aki','1n'), ('nga','tn-nom-s-4s')]"""
    return GOLD_MORPHEME_RE.findall(decomposition)


def parse_hfst_analysis(analysis: str) -> list[tuple[str, str]]:
    """'aki+1n++nga+tn-nom-s-4s' -> [('aki','1n'), ('nga','tn-nom-s-4s')]"""
    pairs = []
    for morpheme in analysis.split("++"):
        canonical, _, tag_id = morpheme.partition("+")
        pairs.append((canonical, tag_id))
    return pairs


def hfst_analyses_weighted(word: str, lenient: bool = False) -> list[tuple[str, float]]:
    """Runs hfst-lookup on `word`, returns (analysis, weight) pairs (upper-side
    output plus its own weight), in the order hfst-lookup printed them,
    excluding unrecognized-word placeholders (HFST prints "word+?" with
    weight inf).

    `lenient`: phonology.xfscript's own LENIENT rule (mirroring the real
    analyzer's own --lenient-decomps extension) gives every ordinary
    analysis weight 0.0, but ALSO adds parallel weight-1.0 paths for
    vowel-final words that assume a final k/p/q/t was silently dropped --
    see that rule's own comment for why weight, not a tag, is what
    distinguishes them. Default False (weight 0 only) excludes those
    paths entirely; pass lenient=True to also include the weight-1.0
    ones."""
    result = subprocess.run(
        ["hfst-lookup", str(ANALYSER)],
        input=word + "\n",
        capture_output=True,
        text=True,
        check=True,
    )
    max_weight = 1.0 if lenient else 0.0
    analyses = []
    for line in result.stdout.splitlines():
        fields = line.split("\t")
        if len(fields) != 3:
            continue
        _, analysis, weight_str = fields
        if weight_str.strip() == "inf":
            continue
        weight = float(weight_str)
        if weight > max_weight:
            continue
        analyses.append((analysis, weight))
    return analyses


def hfst_analyses(word: str, lenient: bool = False) -> list[str]:
    """Same as hfst_analyses_weighted, but returns just the analysis
    strings -- for the many existing callers that only check PRESENCE of
    a correct decomposition (full_corpus_check.py, snapshot_correct.py,
    this module's own GOLD_CASES check) and never needed weight."""
    return [analysis for analysis, _weight in hfst_analyses_weighted(word, lenient=lenient)]


def categorize(gold_parses: list, hfst_parses: list) -> str:
    """Shared with full_corpus_check.py: same 4 categories as the existing
    919-word :cli regression suite."""
    if not hfst_parses:
        return "no-decomps"
    if hfst_parses[0] in gold_parses:
        return "first-decomposition-correct"
    if any(p in gold_parses for p in hfst_parses):
        return "correct-but-not-first"
    return "correct-not-present"


def main():
    fair_cases = {word: case for word, case in load_gold_standard("hansard").items() if _is_fair(case)}

    metrics_by_word: dict[str, WordMetrics] = {}
    all_correct_count_by_word: dict[str, int] = {}
    for word, case in fair_cases.items():
        analyses = hfst_analyses(word)
        hfst_parses = [tuple(parse_hfst_analysis(a)) for a in analyses]
        all_correct_parses = [tuple(parse_gold(g)) for g in case.all_correct_decomps]
        reference_parses = [tuple(parse_gold(g)) for g in (case.correct_decomps or [])]

        m = word_metrics(hfst_parses, all_correct_parses, reference_parses)
        metrics_by_word[word] = m
        all_correct_count_by_word[word] = len(set(all_correct_parses))

        print(f"{word:28s} matched={m.matched}/{all_correct_count_by_word[word]:<4d} produced={m.produced:<4d} referenceRank={m.reference_rank}")

    agg = aggregate_metrics(metrics_by_word, all_correct_count_by_word)
    print_aggregate_metrics(agg)


if __name__ == "__main__":
    main()
