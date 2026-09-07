# Plan: restructure the CLI as one `iutools2` with git-style subcommands

Status: **NOT STARTED** — design only, documented so it isn't lost. No code
yet; raised by Alain 2026-09-08 after the Word Lookup extraction gave the
CLI a second real command to host.

## Why

Today `:cli` ships one executable, `segment_iu`, whose whole identity is
"decompose a word" (`--word` / `--interactive` / `--pipeline`). The recent
work has bolted a second, unrelated feature onto it (`--define`, which runs
the shared `WordLookup`), and more are coming (morpheme dictionary,
transliteration). One flat option surface for several unrelated tools gets
confusing fast.

The original iutools Java CLI was structured like `git`: a single
entrypoint dispatching to named subcommands (`org.iutools.cli.*`, on top of
`ca.nrc`'s `SubCommand` + Apache Commons CLI). We deliberately did **not**
port that framework — it's shared machinery for many subcommands that are
out of scope here — but the *shape* (one binary, `iutools2 <command>
[options] args`) is worth adopting now that we have more than one command.

## Target shape

The executable is named **`iutools2-cli`**.

```
iutools2-cli <subcommand> [options] [arguments]
iutools2-cli                     # list subcommands
iutools2-cli help <subcommand>   # or: iutools2-cli <subcommand> --help
```

Each subcommand is a small self-contained unit: its own option parsing (the
existing `parseArgs` style in `Main.kt`), its own renderer, calling into
`:core`. A thin dispatcher in `main()` picks one by `args[0]`.

### Subcommands

| Subcommand | Replaces / adds | Calls into `:core` |
|---|---|---|
| `decompose_word` *(name TBD, see open qs)* | today's `segment_iu` — `--word` / `--interactive` / `--pipeline` / `--lenient-decomps` / `--timeout-secs` | `MorphologicalAnalyzer_R2L` |
| `word_info` *(name TBD)* | today's `--define` | `WordLookup` (offline: Spalding + decomposition) |
| `morpheme_def` *(name TBD)* | new — no CLI equivalent today | `org.iutools.morphemedict.MorphemeDictionary.search()` |
| `transliterate` | folds in `TranscodeTool.kt`'s standalone `main()` | `org.iutools.script.TransCoder` / `Syllabics` |
| `tokenize` | new, low priority | `org.iutools.text.segmentation.MyStringTokenizer` |

**Out of scope** (as before): the original's corpus / search / spell-check
subcommands (Elasticsearch-dependent), and the `SubCommand` /
Commons-CLI framework itself.

### Dispatcher: hand-rolled, no library

Consistent with the "didn't port the options framework" decision. A
`Subcommand` is just `{ val name; fun run(args: List<String>): Int }`; the
dispatcher is a `when (args.firstOrNull())` plus `help`. Revisit a library
(clikt is the idiomatic Kotlin one) only if the option complexity outgrows
this — not now, for ~5 small commands. Examples throughout this doc use the
`iutools2-cli` executable name.

### Shared conventions

- `--help` / `-h` on every subcommand; bare `iutools2` lists them.
- Consistent exit codes (0 ok, 1 usage error, 2 runtime failure).
- Consider standardising `--json` for machine-readable output across
  subcommands, replacing `decompose_word`'s current bespoke `--pipeline`
  JSON (open question).
- Common flags keep their current names (`--lenient-decomps`,
  `--timeout-secs`) so existing scripts/muscle memory survive.

## Packaging impact — not free

`doc/INSTALL.md` and the jpackage recipe there produce and distribute
`segment_iu.app` / a `segment_iu` binary; there are real users. Switching
to `iutools2-cli decompose_word …` means:

- `application { mainClass }` still points at one class — the dispatcher.
- jpackage `--name iutools2-cli` → `iutools2-cli.app` / `iutools2-cli`
  binary. `INSTALL.md` rewritten around the new name and subcommands.
- **Backward-compatibility decision needed** (see open qs): accept the
  break (update `INSTALL.md`, cut a new release, note it) vs. keep a
  `segment_iu` shim that forwards to `iutools2-cli decompose_word`. The
  *option* names inside the command don't need to change either way.
- `TranscodeTool.kt`'s separate `main()` goes away (becomes `transliterate`).

## Migration steps (each: gate green, one amended commit)

1. Add the dispatcher + `help`; move the current `Main.kt` behaviour behind
   the `decompose_word` subcommand unchanged. `mainClass` = dispatcher.
2. Move the `--define` path into `word_info`; drop `--define` from
   `decompose_word`.
3. Fold `TranscodeTool` into `transliterate`; delete its `main()`.
4. Add `morpheme_def` (wraps `MorphemeDictionary.search()`).
5. `INSTALL.md` + jpackage recipe + `application` docs; decide the
   `segment_iu` shim question; cut a release.
6. *(Optional, later)* `tokenize`; `--json` standardisation.

Each subcommand's arg parser + renderer gets unit tests (the
`DefineRenderingTest` / `parseArgs` pattern). Plus a dispatcher test:
unknown subcommand → usage + exit 1; `help` lists all.

## Open questions (resolve before starting step 1)

- **Exact original subcommand names.** Alain remembers `decompose_word` /
  `word_info` / `morpheme_def`; the code comments reference `CmdSegmentIU`
  (so the original's analyzer command may have been `segment_iu`). Check
  `org.iutools.cli` in the original iutools Java repo and match those names
  rather than inventing new ones.
- **`segment_iu` compatibility shim** — worth the maintenance, or just
  update `INSTALL.md` and move on?

Resolved: the executable is named `iutools2-cli` (Alain, 2026-09-08).
- **`word_info` scope on the CLI:** offline-only (as `--define` is now), or
  add `--online` (Tusaalanga) and/or `--hansard-db <path>` (bilingual
  examples against a local corpus DB)?
- **`--json`:** standardise one machine-output flag across subcommands, or
  leave `decompose_word --pipeline` as the only JSON path?
