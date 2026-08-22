"""
Replicates the real analyzer's DecompositionState.removeCombinedSuffixes()
(core/.../morph/r2l/DecompositionState.kt) -- see that function's own
comment: eliminates a decomposition that uses a SEQUENCE of separate
suffixes when an alternative decomposition (for the same word) uses the
single COMPOSITE suffix covering the exact same morphemes instead, e.g.
"apiqsuqtaujuksaq": juq/1vn+ksaq/1nn (separate) is dropped in favour of
juksaq/1vn (composite) when both are candidates for the same word.

Suffixes.csv/Suffixes_additional.csv's own "combination" column names
the parts directly (e.g. juksaq/1vn's own combination is
"juq/1vn+ksaq/1nn") -- 23 rows total, all suffixes (no roots or endings
carry this column). Same "canonical form encoded directly in the id tag"
convention as everywhere else in this project makes each part directly
comparable to parse_hfst_analysis()'s own (canonical, id) output.

Python translation for now (same status as benoit_sort.py's own sort --
Alain's explicit direction: Python now, converge to shared Kotlin code
later if this proves worth keeping).
"""
import csv
from pathlib import Path

from histogram import parse_hfst_analysis

DATA_DIR = Path(__file__).resolve().parents[2] / "core/src/commonMain/resources/org/iutools/linguisticdata/dataCSV"


def load_combination_lexicon() -> dict[str, list[str]]:
    """{"juksaq/1vn": ["juq/1vn", "ksaq/1nn"], ...} -- composite morpheme
    id -> ordered list of its own separate-morpheme part ids, both sides
    in "canonical/id" form."""
    lex = {}
    for fn in ("Suffixes.csv", "Suffixes_additional.csv"):
        with (DATA_DIR / fn).open(encoding="utf-8") as f:
            for row in csv.DictReader(f):
                combo = (row.get("combination") or "").strip()
                if not combo:
                    continue
                morpheme, nb, function = row["morpheme"], row["nb"], row["function"]
                own_id = f"{morpheme}/{nb}{function}"
                parts = combo.split("+")
                # "#incho#/1vv" (ttuq/1nv's own combination) references a
                # marker morpheme this project's FST doesn't generate at
                # all (not a real suffix) -- skip rows referencing it,
                # nothing to collapse against.
                if any(p == "#incho#/1vv" for p in parts):
                    continue
                lex[own_id] = parts
    return lex


COMBINATION_LEXICON = load_combination_lexicon()


def remove_combined_suffixes(analyses: list[str]) -> list[str]:
    """Given the (already deduped) analysis strings for ONE word, drop
    any analysis whose own id-sequence contains, as a contiguous
    subsequence, the parts of some composite suffix, IF another analysis
    in the SAME list has that exact subsequence collapsed to the
    composite's own id instead (everything else in the chain identical).
    Comparison is by id sequence only (not canonical text), matching
    this project's own "id tag is a stable identifier" convention --
    surface-spelling differences elsewhere in the chain don't matter for
    this check."""
    parsed = [parse_hfst_analysis(a) for a in analyses]
    id_seqs = [tuple(f"{canon}/{mid}" for canon, mid in p) for p in parsed]
    id_seq_set = set(id_seqs)

    keep = [True] * len(analyses)
    for i, ids in enumerate(id_seqs):
        for comp_id, parts in COMBINATION_LEXICON.items():
            n = len(parts)
            for start in range(len(ids) - n + 1):
                if ids[start:start + n] == tuple(parts):
                    collapsed = ids[:start] + (comp_id,) + ids[start + n:]
                    if collapsed in id_seq_set and collapsed != ids:
                        keep[i] = False
                    break
    return [a for a, k in zip(analyses, keep) if k]
