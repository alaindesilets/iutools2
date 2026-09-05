"""
Run the FST prototype on a single Inuktut word (or a stream of them).

This is the data/grammar/fst/ counterpart of the Kotlin CLI in cli/ (whose command
surface calls itself `segment_iu`, after the original iutools command):
same three modes, same option names, so whichever you learn first carries
over. The difference is what does the analysing -- here it is
data/grammar/fst/lexicon-analyser.hfstol via `hfst-lookup`, not
MorphologicalAnalyzer_R2L.decomposeWord(). The two are independent
implementations and will not agree while the .lexc lexicon is still a
partial, hand-picked subset of the CSV data.

Build lexicon-analyser.hfstol first if it is missing (see the header of
phonology.xfscript).

Usage (from data/grammar/fst/):
    python3 segment_iu.py --word WORD [--lenient-decomps] [--raw]
    python3 segment_iu.py WORD                       # --word is optional
    python3 segment_iu.py --interactive [--lenient-decomps]
    python3 segment_iu.py --pipeline [--lenient-decomps] < words.txt

Modes:
    --word WORD        Analyse one word and exit.
    --interactive      Prompt for words one at a time ('q' to quit).
    --pipeline         Read one word per line from stdin, print one JSON
                       result per line to stdout (keys match the Kotlin
                       CLI's --pipeline output).

    --lenient-decomps  Also keep the FST's weight-1.0 paths, which assume a
                       final k/p/q/t was dropped after a vowel (mirrors the
                       Kotlin analyzer's --lenient-decomps). Off by default.
    --raw              Print hfst-lookup's own `canonical+id++...` strings
                       (and weights) instead of the readable morpheme list.
    --analyser PATH    Use a different compiled transducer (default:
                       lexicon-analyser.hfstol next to this script).

There is no --timeout-secs: an hfst-lookup is effectively instantaneous, so
the Kotlin CLI's per-word timeout has no equivalent here.
"""
# Keep type annotations as strings so `str | None` parses on Python 3.9
# (still common on macOS), not just 3.10+.
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

import histogram
from histogram import hfst_analyses_weighted, parse_hfst_analysis


def readable(analysis: str) -> str:
    """'atuaq+1v++gaq+1vn' -> 'atuaq/1v  gaq/1vn'"""
    return "  ".join(f"{canonical}/{tag_id}" for canonical, tag_id in parse_hfst_analysis(analysis))


def analyse(word: str, lenient: bool) -> list[tuple[str, float]]:
    return hfst_analyses_weighted(word, lenient=lenient)


def print_for_user(word: str, analyses: list[tuple[str, float]], raw: bool) -> None:
    print(f"=== {word} ===")
    if not analyses:
        print("  No decompositions found")
        return
    for analysis, weight in analyses:
        shown = analysis if raw else readable(analysis)
        suffix = f"   (weight {weight:g})" if (raw or weight) else ""
        print(f"  {shown}{suffix}")


def pipeline_json(word: str, lenient: bool, elapsed_msecs: int,
                  analyses: list[tuple[str, float]], exception: str | None) -> str:
    return json.dumps({
        "word": word,
        "lenient": lenient,
        "elapsedMSecs": elapsed_msecs,
        "timedOut": False,
        "exception": exception,
        "decompositions": [analysis for analysis, _weight in analyses],
    }, ensure_ascii=False)


def run_pipeline(lenient: bool) -> None:
    for line in sys.stdin:
        word = line.strip()
        if not word:
            continue
        start = time.time()
        try:
            analyses = analyse(word, lenient)
            exception = None
        except subprocess.CalledProcessError as e:
            analyses, exception = [], f"hfst-lookup failed: {e}"
        elapsed_msecs = round((time.time() - start) * 1000)
        print(pipeline_json(word, lenient, elapsed_msecs, analyses, exception))


def run_interactive(lenient: bool, raw: bool) -> None:
    while True:
        try:
            line = input("\nEnter Inuktut word ('q' to quit).\n> ")
        except EOFError:
            break
        word = line.strip()
        if not word or word == "q":
            break
        print_for_user(word, analyse(word, lenient), raw)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run the FST prototype on an Inuktut word.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("word_positional", nargs="?", metavar="WORD",
                        help="word to analyse (same as --word)")
    parser.add_argument("--word", help="analyse a single word and exit")
    parser.add_argument("--interactive", action="store_true",
                        help="prompt for words one at a time ('q' to quit)")
    parser.add_argument("--pipeline", action="store_true",
                        help="read words from stdin, print one JSON result per line")
    parser.add_argument("--lenient-decomps", dest="lenient", action="store_true",
                        help="also keep the FST's weight-1.0 (dropped-final-consonant) paths")
    parser.add_argument("--raw", action="store_true",
                        help="print hfst-lookup's own analysis strings and weights")
    parser.add_argument("--analyser", type=Path, default=None,
                        help="compiled transducer to use (default: lexicon-analyser.hfstol here)")
    args = parser.parse_args()

    if args.analyser is not None:
        histogram.ANALYSER = args.analyser

    word = args.word or args.word_positional
    modes_given = sum([word is not None, args.interactive, args.pipeline])
    if modes_given > 1:
        parser.error("--word/WORD, --interactive and --pipeline are mutually exclusive")

    try:
        if args.pipeline:
            run_pipeline(args.lenient)
        elif args.interactive:
            run_interactive(args.lenient, args.raw)
        elif word is not None:
            print_for_user(word, analyse(word, args.lenient), args.raw)
        else:
            parser.error("give a WORD, --word, --interactive or --pipeline")
    except FileNotFoundError:
        sys.exit("error: 'hfst-lookup' not found on PATH (install the 'hfst' package).")
    except subprocess.CalledProcessError as e:
        sys.exit(f"error: hfst-lookup failed on {histogram.ANALYSER}: {e}\n"
                 f"Is the transducer built? See phonology.xfscript's header.")


if __name__ == "__main__":
    main()
