# `data/` -- iutools2 data and the scripts that generate it

Loosely modelled on the original iutools Java project's `iutools-data`.
Holds data that has value in its own right, plus the build-time scripts
that generate or manage it. It does **not** hold application or analyzer
logic -- that lives in `:core`. See
[`doc/dev/plans/module-architecture-migration.md`](../doc/dev/plans/module-architecture-migration.md).

Three kinds of data, by provenance / lifecycle / owner:

| directory | what | notes |
|---|---|---|
| `grammar/` | grammatical / linguistic-rules data: the FST (`grammar/fst/` -- `lexicon.lexc`, `phonology.xfscript`, the generated `*-generated.lexc`, the `generate_*.py` generators) and, later, the linguistic CSVs | changes when the grammar is refined; small; the analyzer is built from it |
| `corpus/` | compilations derived from text corpora: Hansard frequency lists, the Benoit-decomposition cache, translation memory, the morpheme-frequency prior | batch-job output; can be large; usually frozen snapshots. **Empty for now** -- `grammar/fst/hansard-cache/` will move here. |
| `lexicon/` | lexicographic data: parsed dictionaries (Spalding, the morpheme dictionary) | lookup-oriented. **Empty for now.** |

Membership test for anything under `data/`: *is it a data artifact, or a
thin script that generates/manages one, delegating domain logic to
`:core`?* If it is analyzer or app logic, it belongs in `:core`, not here.

Compiled FST binaries (`grammar/fst/*.hfstol`) and the re-ranker experiment
scratch (`grammar/fst/scratchpad/`) are git-ignored build output.
