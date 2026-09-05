#!/usr/bin/env python3
"""Export the Kotlin gold standard (cli/.../MorphAnalGoldStandard_*.kt) to CSV.

The Kotlin gold standard used to be the source of truth read at runtime;
`OBSOLETE_MorphAnalGoldStandard_Hansard.kt` and
`MorphAnalGoldStandard_WordsThatFailedBefore.kt` are now kept only as the
hand-authored input this script exports from. Both the `:cli` Kotlin test
suite and the `data/grammar/fst/` Python tooling read gold-standard.csv
directly now (via GoldStandardCsvReader.kt / gold_standard_csv_reader.py),
not the Kotlin files.

This script produces every column except `all_correct_decomps` -- that one
comes from actually running the R2L analyzer, not from the Kotlin files, so
it's generated separately by add_all_correct_decomps.py, which must be run
AFTER this script (this script doesn't know about that column and would
otherwise leave the CSV without it on a re-export).

Faithfully reproduces MorphAnalGoldStandardAbstract's own semantics,
including its one quirk: `case4word` is a `Map<String, AnalyzerCase>`, so if
the same word is passed to addCase() more than once, the later call silently
replaces the earlier one -- only the last-declared AnalyzerCase for a given
word is ever seen by the accuracy tests. This exporter reproduces that
overwrite exactly (see the module docstring for word-level classes) rather
than merging the discarded entries back in, so the CSV matches what the
tests actually evaluate today. Three words are affected
(amittuq, anginngittut, iqaluit); Benoit Farley has been asked which
decomposition should be kept for each -- see README.md.
"""

import csv
import re
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent.parent
GOLD_SOURCES = [
    ("hansard", REPO_ROOT / "cli/src/test/kotlin/org/iutools/morph/MorphAnalGoldStandard_Hansard.kt"),
    ("words_that_failed_before", REPO_ROOT / "cli/src/test/kotlin/org/iutools/morph/MorphAnalGoldStandard_WordsThatFailedBefore.kt"),
]
OUTPUT_CSV = Path(__file__).resolve().parent / "gold-standard.csv"

FLAG_METHODS = {
    "isMisspelled": "is_misspelled",
    "possiblyMisspelledWord": "is_possibly_misspelled",
    "isBorrowedWord": "is_borrowed",
    "correctDecompUnknown": "decomp_unknown",
    "isProperName": "is_proper_name",
}


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


def _matching_paren(text: str, open_index: int) -> int:
    """Index of the ')' matching the '(' at open_index, ignoring parens inside "..." strings."""
    depth = 0
    in_string = False
    i = open_index
    while i < len(text):
        c = text[i]
        if in_string:
            if c == '"':
                in_string = False
        elif c == '"':
            in_string = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError(f"Unbalanced parens starting at {open_index}")


def _quoted_strings(text: str) -> list[str]:
    raw = re.findall(r'"((?:[^"\\]|\\.)*)"', text)
    return [s.replace('\\"', '"').replace("\\\\", "\\") for s in raw]


def parse_add_case(call_text: str) -> AnalyzerCase:
    """Parse one `addCase(AnalyzerCase("word", ...)....)` call (with its trailing chained calls)."""
    ac_open = call_text.index("AnalyzerCase(")
    ac_paren = ac_open + len("AnalyzerCase")
    ac_close = _matching_paren(call_text, ac_paren)
    ac_args = call_text[ac_paren + 1 : ac_close]

    word_match = re.match(r'\s*"((?:[^"\\]|\\.)*)"\s*,\s*(.*)', ac_args, re.DOTALL)
    if not word_match:
        raise ValueError(f"Could not parse AnalyzerCase args: {ac_args!r}")
    word, rest = word_match.group(1), word_match.group(2).strip()

    if rest.startswith("null"):
        decomps = None
    elif rest.startswith("arrayOf("):
        array_open = rest.index("(")
        array_close = _matching_paren(rest, array_open)
        decomps = _quoted_strings(rest[array_open + 1 : array_close])
    else:
        raise ValueError(f"Unrecognized correctDecomps expression: {rest!r}")

    case = AnalyzerCase(word=word, correct_decomps=decomps)

    chained_text = call_text[ac_close + 1 :]
    for method_match in re.finditer(r"\.(\w+)\(", chained_text):
        name = method_match.group(1)
        if name in FLAG_METHODS:
            setattr(case, FLAG_METHODS[name], True)
        elif name == "comment":
            args_open = method_match.end() - 1
            args_close = _matching_paren(chained_text, args_open)
            # A long comment may be written as several Kotlin string literals
            # joined with `+` (adjacent quoted segments, no other operators
            # between them) -- concatenate all of them, not just the first.
            comment_strings = _quoted_strings(chained_text[args_open + 1 : args_close])
            case.comments = "".join(comment_strings)
        else:
            raise ValueError(f"Unrecognized chained method: {name}")

    return case


def _strip_line_comments(text: str) -> str:
    """Blank out `// ...` line comments, leaving `//` inside string literals alone."""
    out_lines = []
    for line in text.split("\n"):
        in_string = False
        comment_at = None
        i = 0
        while i < len(line):
            c = line[i]
            if in_string:
                if c == '"':
                    in_string = False
            elif c == '"':
                in_string = True
            elif c == "/" and i + 1 < len(line) and line[i + 1] == "/":
                comment_at = i
                break
            i += 1
        out_lines.append(line if comment_at is None else line[:comment_at])
    return "\n".join(out_lines)


def parse_gold_file(path: Path) -> dict[str, AnalyzerCase]:
    text = _strip_line_comments(path.read_text(encoding="utf-8"))
    case4word: dict[str, AnalyzerCase] = {}
    for m in re.finditer(r"addCase\(", text):
        open_index = m.end() - 1
        close_index = _matching_paren(text, open_index)
        call_text = text[m.start() : close_index + 1]
        case = parse_add_case(call_text)
        case4word[case.word] = case  # last addCase() for a word wins, same as the Kotlin Map
    return case4word


def export_to_csv(sources: list[tuple[str, Path]], output_path: Path) -> int:
    rows = []
    for source_name, path in sources:
        for case in parse_gold_file(path).values():
            has_no_correct_decomp = case.correct_decomps is None
            # A word can (rarely) carry a placeholder empty-string decomp
            # (`arrayOf("")`, distinct from `null`) -- keep it, rather than
            # folding it into the `null` case, so the CSV round-trips exactly.
            decomps = [""] if has_no_correct_decomp else case.correct_decomps
            for decomp in decomps:
                rows.append(
                    {
                        "source": source_name,
                        "word": case.word,
                        "decomp_as_found_in_source": decomp,
                        "has_no_correct_decomp": has_no_correct_decomp,
                        "is_misspelled": case.is_misspelled,
                        "is_possibly_misspelled": case.is_possibly_misspelled,
                        "is_borrowed": case.is_borrowed,
                        "decomp_unknown": case.decomp_unknown,
                        "is_proper_name": case.is_proper_name,
                        "comments": case.comments,
                    }
                )

    with output_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    return len(rows)


if __name__ == "__main__":
    row_count = export_to_csv(GOLD_SOURCES, OUTPUT_CSV)
    print(f"Wrote {row_count} rows to {OUTPUT_CSV}")
