# `data/` -- iutools2 data and the scripts that generate it

Loosely modelled on the original iutools Java project's `iutools-data`.
Holds data that has value in its own right -- useful on its own, outside
this project, not just as fuel for the analyzer -- plus the build-time
scripts that generate or manage it. It does **not** hold application or
analyzer logic -- that lives in `:core`. See
[`doc/dev/plans/module-architecture-migration.md`](../doc/dev/plans/module-architecture-migration.md).

One goal here is discoverability: gather anything with standalone value
under one directory, rather than burying it inside whichever app module
happens to consume it. When a consuming module (`:core`, `:composeApp`)
needs the data at runtime, prefer pointing its build at `data/` directly
(a `resources.srcDir` / `assets.srcDirs` entry, as `:core`'s linguistic
CSVs and `:composeApp`'s Spalding dictionary already do) over duplicating
the file into the module's own tree -- one file to find, one copy to keep
correct. The FST's compiled transducer is the deliberate exception: it's a
build artifact with no use outside the app, so a plain copied file in
`:composeApp` (documented at its call site) is fine there.

Three kinds of data, by provenance / lifecycle / owner:

| directory | what | notes |
|---|---|---|
| `grammar/` | grammatical / linguistic-rules data: the FST (`grammar/fst/` -- `lexicon.lexc`, `phonology.xfscript`, the generated `*-generated.lexc`, the `generate_*.py` generators) and the linguistic CSVs (`grammar/linguistic-data/` -- roots, suffixes, endings, ...) | changes when the grammar is refined; small; the analyzer is built from it |
| `corpus/` | compilations derived from text corpora: Hansard frequency lists, the Benoit-decomposition cache, translation memory, the morpheme-frequency prior | batch-job output; can be large; usually frozen snapshots. **Empty for now** -- `grammar/fst/hansard-cache/` will move here. |
| `lexicon/` | lexicographic data: parsed dictionaries (the Spalding dictionary; the morpheme dictionary is a search over `grammar/linguistic-data/`, not a separate dataset) | lookup-oriented |

Membership test for anything under `data/`: *is it a data artifact, or a
thin script that generates/manages one, delegating domain logic to
`:core`?* If it is analyzer or app logic, it belongs in `:core`, not here.

Compiled FST binaries (`grammar/fst/*.hfstol`) and the re-ranker experiment
scratch (`grammar/fst/scratchpad/`) are git-ignored build output.
