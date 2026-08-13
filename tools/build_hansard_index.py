"""
Converts the Nunavut Hansard Inuktitut-English Parallel Corpus 3.0.1 (NRC
Digital Repository, DOI 10.4224/40001819, CC BY 4.0) into a SQLite index the
app can query locally for bilingual examples of a word's use -- see
NunavutHansardLocalIndex.kt for why this replaced an earlier plan to query
inuktitutcomputing.ca's live (undocumented) search over the network: this
corpus covers 1999-2017 (inuktitutcomputing.ca's data stops around 2005),
its CC BY 4.0 license explicitly permits derivative works, and an on-device
index needs no network access at all.

Not part of the app build: the corpus is huge (202 MiB compressed, ~350 MiB
extracted) and the resulting hansard.db lands around 550 MiB (the `pairs`
table -- the actual sentence text -- accounts for most of that; `words`/
`word_index` are normalized to avoid repeating word text per occurrence,
see that schema's comment below) -- both are gitignored, and the db is
never bundled as an app asset either (would make
every `installDebug`/Run push hundreds of MiB to the device). Instead it's
generated here, once, then pushed straight to a device/emulator's
app-specific external storage with tools/push_hansard_db.sh -- see
tools/README-hansard.md for the full one-time setup.

Source layout (confirmed from the corpus's own README, not guessed):
NunavutHansard.{en,iu,id} are line-aligned -- the Nth line of each forms the
Nth sentence pair. Blank-on-both-sides lines mark paragraph boundaries that
weren't collapsed during alignment, and 0-1/1-0 alignments (blank on one
side only) are also preserved in the source; both are noise for a
word-lookup index and are filtered out here, keeping only lines where both
sides are non-blank (~1.31M of the corpus's ~2.59M lines). NunavutHansard.id
gives each pair's provenance as "<source file> <line number>", e.g.
"Hansard_19990401 65" -- the date is the 8 digits in the filename.

Usage:
    python3 tools/build_hansard_index.py tools/hansard-corpus/Nunavut-Hansard-Inuktitut-English-Parallel-Corpus-3.0 tools/hansard.db
"""
import re
import sqlite3
import sys
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = 1
CORPUS_VERSION = "3.0.1"

WORD_RE = re.compile(r"\w+", re.UNICODE)


def real_pairs(corpus_dir: Path):
    """Yields (source_file, source_line, inuktitut, english) for every line
    where both sides are non-blank -- see the module docstring for why."""
    en_path = corpus_dir / "NunavutHansard.en"
    iu_path = corpus_dir / "NunavutHansard.iu"
    id_path = corpus_dir / "NunavutHansard.id"
    with en_path.open(encoding="utf-8") as en_f, \
         iu_path.open(encoding="utf-8") as iu_f, \
         id_path.open(encoding="utf-8") as id_f:
        for en_line, iu_line, id_line in zip(en_f, iu_f, id_f):
            english = en_line.rstrip("\n")
            inuktitut = iu_line.rstrip("\n")
            if not english or not inuktitut:
                continue
            source_file, source_line = id_line.rstrip("\n").split("\t")
            yield source_file, int(source_line), inuktitut, english


def build(corpus_dir: Path, db_path: Path) -> None:
    if db_path.exists():
        db_path.unlink()
    conn = sqlite3.connect(str(db_path))
    # Bulk-load performance pragmas -- this is a throwaway build process
    # (not something a crash should leave half-written and reuse), so
    # durability during the build itself doesn't matter.
    conn.execute("PRAGMA journal_mode = OFF")
    conn.execute("PRAGMA synchronous = OFF")
    conn.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")

    conn.execute("""
        CREATE TABLE pairs (
            id INTEGER PRIMARY KEY,
            source_file TEXT NOT NULL,
            source_line INTEGER NOT NULL,
            inuktitut TEXT NOT NULL,
            english TEXT NOT NULL
        )
    """)
    # words/word_index are split rather than one (word TEXT, pair_id) table:
    # Inuktitut's polysynthetic morphology means ~1.6M distinct wordforms
    # across the corpus, so a word repeats across its ~5 average occurrences
    # -- storing the word's text on every one of those ~8M occurrence rows
    # (rather than once per distinct word) was most of an earlier 818 MiB
    # build's size (a word_index(word TEXT, pair_id) table plus its index
    # alone was over 500 MiB). WITHOUT ROWID on word_index makes the
    # (word_id, pair_id) primary key itself the on-disk clustered index, no
    # separate index structure needed for it.
    conn.execute("CREATE TABLE words (id INTEGER PRIMARY KEY, word TEXT NOT NULL UNIQUE)")
    conn.execute("""
        CREATE TABLE word_index (
            word_id INTEGER NOT NULL,
            pair_id INTEGER NOT NULL,
            PRIMARY KEY (word_id, pair_id)
        ) WITHOUT ROWID
    """)
    conn.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")

    pair_count = 0
    pair_rows = []
    word_ids: dict[str, int] = {}
    word_index_rows = []
    for source_file, source_line, inuktitut, english in real_pairs(corpus_dir):
        pair_count += 1
        pair_id = pair_count
        pair_rows.append((pair_id, source_file, source_line, inuktitut, english))
        # A word repeated within one sentence only needs one index row
        # pointing at that sentence -- set() dedupes per-pair.
        for word in {w.lower() for w in WORD_RE.findall(inuktitut)}:
            word_id = word_ids.get(word)
            if word_id is None:
                word_id = len(word_ids) + 1
                word_ids[word] = word_id
            word_index_rows.append((word_id, pair_id))
        if pair_count % 200_000 == 0:
            print(f"  ... {pair_count} pairs processed", file=sys.stderr)

    print(f"Inserting {len(pair_rows)} pairs, {len(word_ids)} distinct words, "
          f"{len(word_index_rows)} word-index rows...", file=sys.stderr)
    conn.executemany("INSERT INTO pairs VALUES (?, ?, ?, ?, ?)", pair_rows)
    conn.executemany("INSERT INTO words VALUES (?, ?)", ((wid, w) for w, wid in word_ids.items()))
    conn.executemany("INSERT INTO word_index VALUES (?, ?)", word_index_rows)

    conn.executemany(
        "INSERT INTO meta VALUES (?, ?)",
        [
            ("schema_version", str(SCHEMA_VERSION)),
            ("corpus_version", CORPUS_VERSION),
            ("pair_count", str(pair_count)),
            ("generated_at", datetime.now(timezone.utc).isoformat(timespec="seconds")),
        ],
    )

    conn.commit()
    conn.execute("VACUUM")
    conn.close()
    print(f"Done: {pair_count} pairs -> {db_path} ({db_path.stat().st_size / 1_048_576:.1f} MiB)", file=sys.stderr)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
    build(Path(sys.argv[1]), Path(sys.argv[2]))
