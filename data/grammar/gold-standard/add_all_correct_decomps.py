#!/usr/bin/env python3
"""Adds an `all_correct_decomps` column to gold-standard.csv: for every
"fair" word (no is_misspelled/is_possibly_misspelled/is_borrowed/
is_proper_name/decomp_unknown flag -- the same population :cli's own
accuracy suites evaluate, across both the "hansard" and
"words_that_failed_before" sources), runs R2L via
`cli --pipeline --lenient-decomps` and records EVERY decomposition it
produces, `;`-separated. This is a much looser, larger set than the single
Hansard-attested decomp already in `decomp_as_found_in_source` -- R2L
explores every grammatically valid parse it can find, most of which are
plausible alternate readings never attested in this particular Hansard
occurrence (see the [[project_gold_standard_multiple_correct_decomps]]
discussion: several grammatically valid decomps often exist beyond the one
Benoit attested). Rows for flagged words are left with an empty
`all_correct_decomps` (R2L's own decomposability isn't a fair signal there).

This column is a derived/regenerable one (recomputed from the current R2L
analyzer + lexicon, not hand-authored), unlike the rest of the CSV: re-run
this script after export_gold_standard.py regenerates the base columns from
Kotlin, since that script doesn't know about this column and would
otherwise leave it stale, not present.

Prerequisite: cli/build/install/cli/bin/cli must exist -- build it with
`./gradlew :cli:installDist` from the repo root.

Usage (from data/grammar/gold-standard/):
    python3 add_all_correct_decomps.py
"""

import csv
import json
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent.parent
CSV_PATH = Path(__file__).resolve().parent / "gold-standard.csv"
CLI_BIN = REPO_ROOT / "cli/build/install/cli/bin/cli"


def is_flagged(row: dict) -> bool:
    return any(
        row[flag] == "True"
        for flag in (
            "is_misspelled",
            "is_possibly_misspelled",
            "is_borrowed",
            "is_proper_name",
            "decomp_unknown",
        )
    )


def run_r2l_pipeline(words: list[str]) -> dict[str, list[str]]:
    """Runs `cli --pipeline --lenient-decomps` over `words` (one process,
    one word per stdin line) and returns word -> every decomposition string
    R2L produced for it (in R2L's own {surface:canonical/id} notation,
    already identical to decomp_as_found_in_source's)."""
    result = subprocess.run(
        [str(CLI_BIN), "--pipeline", "--lenient-decomps"],
        input="\n".join(words) + "\n",
        capture_output=True,
        text=True,
        check=True,
    )
    decomps_by_word: dict[str, list[str]] = {}
    for line in result.stdout.splitlines():
        if not line.strip():
            continue
        record = json.loads(line)
        decomps_by_word[record["word"]] = record["decompositions"]
    return decomps_by_word


def main() -> None:
    if not CLI_BIN.is_file():
        sys.exit(
            f"{CLI_BIN} not found -- build it first with "
            "`./gradlew :cli:installDist` (from the repo root)."
        )

    with CSV_PATH.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        rows = list(reader)

    fair_words = sorted({row["word"] for row in rows if not is_flagged(row)})
    print(f"Running R2L over {len(fair_words)} fair words (all sources)...")
    decomps_by_word = run_r2l_pipeline(fair_words)

    missing_from_r2l_output = set(fair_words) - set(decomps_by_word)
    if missing_from_r2l_output:
        print(f"WARNING: {len(missing_from_r2l_output)} words got no pipeline result at all: "
              f"{sorted(missing_from_r2l_output)}")

    gold_not_in_r2l_output = []
    for row in rows:
        if is_flagged(row) or row["has_no_correct_decomp"] == "True":
            continue
        word = row["word"]
        all_decomps = decomps_by_word.get(word, [])
        if row["decomp_as_found_in_source"] not in all_decomps:
            gold_not_in_r2l_output.append((word, row["decomp_as_found_in_source"], all_decomps))

    if gold_not_in_r2l_output:
        print(f"\nNOTE: {len(gold_not_in_r2l_output)} word(s) where the Hansard-attested "
              f"decomp is NOT among R2L's own output (a real, known gap -- R2L doesn't "
              f"find the correct decomp for every fair word):")
        for word, gold_decomp, all_decomps in gold_not_in_r2l_output:
            print(f"  {word}: gold={gold_decomp!r} not in R2L's {len(all_decomps)} decomps")

    for row in rows:
        if not is_flagged(row):
            row["all_correct_decomps"] = ";".join(decomps_by_word.get(row["word"], []))
        else:
            row["all_correct_decomps"] = ""

    new_fieldnames = list(fieldnames)
    if "all_correct_decomps" not in new_fieldnames:
        insert_at = new_fieldnames.index("has_no_correct_decomp") + 1
        new_fieldnames.insert(insert_at, "all_correct_decomps")

    with CSV_PATH.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=new_fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(f"\nWrote all_correct_decomps for {len(fair_words)} fair words to {CSV_PATH}")


if __name__ == "__main__":
    main()
