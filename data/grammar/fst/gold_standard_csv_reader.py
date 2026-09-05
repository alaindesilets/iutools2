"""Reads data/grammar/gold-standard/gold-standard.csv and builds the same
word -> AnalyzerCase mapping that MorphAnalGoldStandard_Hansard /
_WordsThatFailedBefore build in Kotlin via addCase(AnalyzerCase(...)) calls
-- see cli/src/test/kotlin/org/iutools/morph/GoldStandardCsvReader.kt for
the Kotlin counterpart this mirrors.
"""

import csv
import re
from dataclasses import dataclass, field
from pathlib import Path

GOLD_STANDARD_CSV = Path(__file__).resolve().parent.parent / "gold-standard" / "gold-standard.csv"

SURFACE_FORM_RE = re.compile(r"\{([^:}]+):[^}]*\}")


def concatenated_surface_forms(decomp: str) -> str:
    """'{amit:amit/1v}{tuq:juq/1vn}' -> 'amittuq' -- the part of each
    {surface:canonical/id} component BEFORE the colon is the surface
    substring matched in the word; concatenating them in order must
    reconstruct the original word."""
    return "".join(SURFACE_FORM_RE.findall(decomp))


@dataclass
class AnalyzerCase:
    word: str
    correct_decomps: list[str] | None
    is_misspelled: bool = False
    is_possibly_misspelled: bool = False
    is_borrowed: bool = False
    decomp_unknown: bool = False
    is_proper_name: bool = False
    comments: str = ""
    all_correct_decomps: list[str] = field(default_factory=list)


def add_case(case4word: dict[str, AnalyzerCase], case: AnalyzerCase) -> None:
    """Same semantics as MorphAnalGoldStandardAbstract.addCase(): the last
    case added for a given word replaces any earlier one."""
    case4word[case.word] = case


def load_gold_standard(source: str, csv_path: Path = GOLD_STANDARD_CSV) -> dict[str, AnalyzerCase]:
    """Builds the word -> AnalyzerCase mapping for one gold-standard source
    ("hansard" or "words_that_failed_before") from the CSV export."""
    rows_by_word: dict[str, list[dict]] = {}
    with csv_path.open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            if row["source"] != source:
                continue
            rows_by_word.setdefault(row["word"], []).append(row)

    case4word: dict[str, AnalyzerCase] = {}
    for word, rows in rows_by_word.items():
        first = rows[0]
        has_no_correct_decomp = first["has_no_correct_decomp"] == "True"
        correct_decomps = None if has_no_correct_decomp else [r["decomp_as_found_in_source"] for r in rows]
        all_correct_decomps_field = first.get("all_correct_decomps", "")
        all_correct_decomps = all_correct_decomps_field.split(";") if all_correct_decomps_field else []
        case = AnalyzerCase(
            word=word,
            correct_decomps=correct_decomps,
            is_misspelled=first["is_misspelled"] == "True",
            is_possibly_misspelled=first["is_possibly_misspelled"] == "True",
            is_borrowed=first["is_borrowed"] == "True",
            decomp_unknown=first["decomp_unknown"] == "True",
            is_proper_name=first["is_proper_name"] == "True",
            comments=first["comments"],
            all_correct_decomps=all_correct_decomps,
        )
        add_case(case4word, case)
    return case4word


def is_flagged(case: AnalyzerCase) -> bool:
    return (
        case.is_misspelled
        or case.is_possibly_misspelled
        or case.is_borrowed
        or case.is_proper_name
        or case.decomp_unknown
    )


@dataclass
class WordMetrics:
    """Per-word Recall/Precision/reference-rank facts for one analyzer run
    -- mirrors MorphAnalCurrentExpectationsAbstract.WordExpectation in
    GoldStandardCsvReader.kt. `matched`/`produced` are exact integer counts
    (the shared numerator/denominators of recall and precision); allCorrect
    isn't stored here since it's a property of the gold data, recomputed
    live by the caller, not of the analyzer being measured."""

    matched: int
    produced: int
    reference_rank: int | None


def word_metrics(produced_decomps: list[str], all_correct_decomps: list[str], reference_decomps: list[str] | None) -> WordMetrics:
    """produced_decomps: the analyzer's own ranked output for this word (as
    decomp strings, same notation as the gold CSV). all_correct_decomps /
    reference_decomps: this word's gold data (already normalized to the
    analyzer's own notation by the caller, e.g. stripping "surface:" for
    the FST)."""
    produced_set = set(produced_decomps)
    correct_set = set(all_correct_decomps)
    matched = len(produced_set & correct_set)
    reference_rank = None
    if reference_decomps:
        reference_set = set(reference_decomps)
        for i, d in enumerate(produced_decomps):
            if d in reference_set:
                reference_rank = i
                break
    return WordMetrics(matched=matched, produced=len(produced_set), reference_rank=reference_rank)


@dataclass
class AggregateMetrics:
    recall: float
    precision: float
    precision_word_count: int
    no_output_count: int
    reference_present_rate: float
    topn_rates: dict[int, float]
    total_words: int


def aggregate_metrics(
    metrics_by_word: dict[str, WordMetrics],
    all_correct_count_by_word: dict[str, int],
    max_n: int = 5,
) -> AggregateMetrics:
    """Macro-averages (mean of per-word %, not pooled) Recall and Precision
    -- "example-based" in multi-label-classification terms, since each word
    is an example with its own correct-answer set. Precision excludes words
    with produced == 0 (undefined, 0/0) from its average, tallying them
    separately instead (no_output_count)."""
    recall_sum = 0.0
    recall_count = 0
    precision_sum = 0.0
    precision_count = 0
    no_output_count = 0
    reference_present_count = 0
    topn_counts = {n: 0 for n in range(1, max_n + 1)}
    total_words = len(metrics_by_word)

    for word, m in metrics_by_word.items():
        all_correct_count = all_correct_count_by_word.get(word, 0)
        if all_correct_count > 0:
            recall_sum += m.matched / all_correct_count
            recall_count += 1
        if m.produced > 0:
            precision_sum += m.matched / m.produced
            precision_count += 1
        else:
            no_output_count += 1
        if m.reference_rank is not None:
            reference_present_count += 1
            for n in range(1, max_n + 1):
                if m.reference_rank < n:
                    topn_counts[n] += 1

    return AggregateMetrics(
        recall=recall_sum / recall_count if recall_count else 0.0,
        precision=precision_sum / precision_count if precision_count else 0.0,
        precision_word_count=precision_count,
        no_output_count=no_output_count,
        reference_present_rate=reference_present_count / total_words if total_words else 0.0,
        topn_rates={n: (c / total_words if total_words else 0.0) for n, c in topn_counts.items()},
        total_words=total_words,
    )


def print_aggregate_metrics(agg: AggregateMetrics) -> None:
    print("\n== Accuracy metrics (macro-averaged where noted) ==\n")
    print(f"Recall    (macro-avg over {agg.total_words} words) : {agg.recall * 100:.1f}%")
    print(
        f"Precision (macro-avg over {agg.precision_word_count} words with output; "
        f"{agg.no_output_count} produced nothing) : {agg.precision * 100:.1f}%"
    )
    present_count = round(agg.reference_present_rate * agg.total_words)
    print(f"Reference decomp present anywhere    : {present_count}/{agg.total_words} ({agg.reference_present_rate * 100:.1f}%)")
    print("Reference decomp in top-N:")
    for n, rate in sorted(agg.topn_rates.items()):
        count = round(rate * agg.total_words)
        print(f"  N={n}: {count}/{agg.total_words} ({rate * 100:.1f}%)")
