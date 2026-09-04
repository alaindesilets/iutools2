"""
"Correct decomposition is within the top N" metrics, N=1..5, for three
conditions over the same --fair gold population:
  1. FST, hfst-lookup's own raw (unranked) output order.
  2. FST, re-sorted with benoit_sort.py's Python translation of the real
     analyzer's own DecompositionState.compareTo() ranking.
  3. The real analyzer itself (--pipeline output, already in its own
     real sorted order -- no re-sorting needed).

Requested by Alain after comparing FST-vs-Benoit "first decomposition
correct" rates and asking for a finer-grained (top-1..5) breakdown on
both sides, plus a Kotlin-algorithm-equivalent sort for the FST (see
benoit_sort.py's own header for why that's a Python translation for now,
not literally shared code with the JVM side yet).

Usage:
    python3 topn_stats.py [--fair]
    (needs benoit_timing.jsonl, the captured `cli --pipeline` output over
    the gold corpus, already in the session's scratchpad -- see
    BENOIT_TIMING_PATH below; regenerate it if missing/stale by re-running
    `cli --pipeline` over affix_frequency.load_words()'s own word list.)
"""
import argparse
import json
import re
from pathlib import Path

from affix_frequency import load_words
from benoit_sort import sort_like_benoit, sort_with_frequency_tiebreak
from full_corpus_check import group_by_word
from histogram import hfst_analyses_weighted, parse_hfst_analysis

BENOIT_TIMING_PATH = Path(
    "/tmp/claude-1000/-workspace/d28f3993-4f10-4c08-b1c1-cc54b237bfcd/scratchpad/benoit_timing_v3.jsonl"
)
# Regenerate with:
#   cat <word list> | cli/build/install/cli/bin/cli --pipeline --lenient-decomps > benoit_timing_v3.jsonl
# --lenient-decomps is REQUIRED: the CLI wrapper defaults it OFF, but
# MorphologicalAnalyzer_R2L.doDecompose()'s own `extendedAnalysisIn`
# parameter defaults to true internally -- without the flag, the CLI
# silently under-represents the real analyzer's own default behavior
# (confirmed: 22/922 --fair words showed a spurious empty decomposition
# list without it, e.g. "asingi" -- {asi:asi/1n}{ngit:ngit/tn-nom-p-4s},
# needs the final-consonant-after-vowel extension to be found at all).
BENOIT_DECOMP_RE = re.compile(r"\{([^:}]+):([^/}]+)/([^}]+)\}")

MAX_N = 5


def parse_benoit_decomp(decomp_str: str) -> list[tuple[str, str]]:
    """'{aa:aa/1v}{n:t/1vv}' -> [('aa','1v'), ('t','1vv')] -- (canonical,
    id) pairs, same shape as parse_hfst_analysis's own output, so both
    sides compare against gold_parses identically."""
    return [(canonical, tag_id) for _surf, canonical, tag_id in BENOIT_DECOMP_RE.findall(decomp_str)]


def load_benoit_decomps() -> dict[str, list[list[tuple[str, str]]]]:
    decomps = {}
    with BENOIT_TIMING_PATH.open(encoding="utf-8") as f:
        for line in f:
            rec = json.loads(line)
            decomps[rec["word"]] = [parse_benoit_decomp(d) for d in rec.get("decompositions", [])]
    return decomps


def dedup_preserve_order(pairs: list[tuple[str, float]]) -> list[tuple[str, float]]:
    """De-duplicate (analysis, weight) pairs by analysis string, keeping each
    string once at its LOWEST weight (first-seen order preserved).

    phonology.xfscript's LENIENT rule makes the same analysis string come out
    both at weight 0 (a strict parse) and weight 1 (that parse also reachable
    by assuming a dropped final consonant). A parse is strict if ANY path
    yields it strictly, so we keep the min. Keeping "whichever hfst-lookup
    emitted first" instead would tie the weight sort key to the reader's
    arbitrary path-enumeration order -- and diverge from Kotlin's
    MorphologicalAnalyzer_FST, which applies this same min-weight dedup."""
    min_weight: dict[str, float] = {}
    for analysis, weight in pairs:
        if analysis not in min_weight or weight < min_weight[analysis]:
            min_weight[analysis] = weight
    return list(min_weight.items())


def topn_histogram(word_to_parse_lists, grouped_gold) -> dict[int, int]:
    """word_to_parse_lists: {word: [parse, parse, ...]} (already ranked,
    already deduped). Returns {N: count of words where the correct parse
    is within the first N} for N in 1..MAX_N."""
    hist = {n: 0 for n in range(1, MAX_N + 1)}
    for word, gold_morph_lists in grouped_gold.items():
        gold_parses = [list(m) for m in gold_morph_lists]
        parses = word_to_parse_lists.get(word, [])
        for n in range(1, MAX_N + 1):
            if any(p in gold_parses for p in parses[:n]):
                hist[n] += 1
    return hist


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fair", action="store_true")
    parser.add_argument(
        "--lenient", action="store_true",
        help="include the FST's LENIENT-marker paths (phonology.xfscript's own "
             "mirror of the real analyzer's --lenient-decomps extension, weight "
             "1.0) alongside the normal weight-0 ones, instead of weight-0 only",
    )
    args = parser.parse_args()

    grouped = group_by_word(load_words(exclude_flagged=args.fair))
    total = len(grouped)

    fst_raw = {}
    fst_sorted = {}
    fst_sorted_freq = {}
    for word in grouped:
        pairs = dedup_preserve_order(hfst_analyses_weighted(word, lenient=args.lenient))
        fst_raw[word] = [parse_hfst_analysis(a) for a, _w in pairs]
        fst_sorted[word] = [parse_hfst_analysis(a) for a, _w in sort_like_benoit(pairs)]
        fst_sorted_freq[word] = [parse_hfst_analysis(a) for a, _w in sort_with_frequency_tiebreak(pairs)]

    benoit = load_benoit_decomps()

    hist_fst_raw = topn_histogram(fst_raw, grouped)
    hist_fst_sorted = topn_histogram(fst_sorted, grouped)
    hist_fst_sorted_freq = topn_histogram(fst_sorted_freq, grouped)
    hist_benoit = topn_histogram(benoit, grouped)

    mode = " (--fair)" if args.fair else ""
    mode += ", FST LENIENT" if args.lenient else ""
    print(f"Top-N accuracy over {total} words{mode}:\n")
    header = (
        f"{'N':>3} | {'FST (raw)':>10} | {'FST (Benoit sort)':>18} | "
        f"{'FST (+freq tie-break)':>22} | {'Benoit (real)':>14}"
    )
    print(header)
    print("-" * len(header))
    for n in range(1, MAX_N + 1):
        r = hist_fst_raw[n]
        s = hist_fst_sorted[n]
        f = hist_fst_sorted_freq[n]
        b = hist_benoit[n]
        print(
            f"{n:>3} | {r:>4} ({100*r/total:4.1f}%) | {s:>10} ({100*s/total:4.1f}%) | "
            f"{f:>14} ({100*f/total:4.1f}%) | {b:>6} ({100*b/total:4.1f}%)"
        )


if __name__ == "__main__":
    main()
