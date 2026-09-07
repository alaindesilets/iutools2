# Plan: extract the Word Lookup orchestration into `:core`

Status: **DONE** (steps 1–5, 2026-09-08). `WordLookup` lives in `:core`
(`org.iutools.lookup`), the app's `WordLookupScreen` and the CLI's
`segment_iu --define` both drive it. Step 4 was done in a scoped form — the
screen collects `WordLookup.lookup(...)` onto its existing state fields;
still deferred (own later low-risk pass): deleting the old
`DictionaryLookupResult` / `ShorterWordDictionaryResult` (kept as a thin
view-model layer) and trimming `Idle` / `Loading` off `DecompositionOutcome`.

Part of
[`module-architecture-migration.md`](module-architecture-migration.md).
The leaf extractions (value types, small helpers, the Guess Meaning
enrichment adapters) are done; this is the one substantial piece of
genuinely reusable logic still trapped in `:composeApp`.

## Why

Today, "look up a word" is a ~120-line `findWord()` closure inside
`WordLookupScreen.kt` (a 1739-line Compose file). It runs the analyzer,
queries the two dictionaries, conditionally searches the Hansard corpus,
and decides what each result gates (Guess Meaning button, automatic vs
on-demand Hansard search, "found for a shorter word" handling). None of
that is Compose-specific, and Alain wants a `:cli --define <word>` in the
near future that produces the same answer.

This mirrors the original iutools Java split: **core** took a word plus a
prefs spec and returned one data structure holding everything that would
eventually be shown; the servlet ("model") and the HTML/JS ("view") were
thin layers on top. In iutools2 there is no server — the "view" is Compose
now and a CLI renderer later, and the thin per-app wiring replaces the
servlet.

## Target shape

New package `org.iutools.lookup` in `:core`:

- **`WordLookup`** — the use-case. Constructor-injected with the one data
  source `:core` can't reach on its own:
  - `hansardExamples: HansardExampleSource` (see below).
  - `analyzerFor: (AnalyzerChoice) -> MorphologicalAnalyzer?` — returns
    null when the choice is unavailable (FST transducer failed to load),
    which `WordLookup` reports as a typed failure. `WordLookup` does **not**
    own the analyzer's lifecycle; the caller constructs and `close()`s it.
  - Spalding and Tusaalanga are already in `:core` (`org.iutools.dictionary`)
    — `WordLookup` calls them directly, nothing to inject.
- **`WordLookupPrefs`** — `analyzerChoice`, `lenient`, and nothing else.
  **Not** the UI language and **not** localized section titles — those stay
  in the view. Display-script transcoding also stays a view concern: the
  result carries raw text + the word's entered `Script`, and the view calls
  the existing `:core` `displayForm` / `DisplayScript.resolve` helpers.
- **`WordLookupResult`** — the aggregate the old Java `core` layer returned:
  - `decomposition: DecompositionOutcome` (renamed from the screen's
    `DecomposeState`; `Idle`/`Loading` drop out — those are view state, not
    lookup outcomes — leaving `Success` / `Failure(FailureReason)`).
  - `dictionaryHits: List<DictionaryHit>` (exact matches, from either
    source; carries a `source: DictionarySource` enum — SPALDING /
    TUSAALANGA — not a title string).
  - `shorterWordHits: List<ShorterWordDictionaryHit>`.
  - `hansard: HansardExamplesOutcome`.
  - `tusaalangaFetchError: String?` (debug-only surface, as today).
  - computed: `hasDefinition` (any exact `dictionaryHits`), and the
    `shouldOfferGuessMeaning` rule currently in `WordLookupScreen.kt`.
- **`MultipleWordsSubmitted(words: List<String>)`** — `lookup()` rejects a
  multi-word string with this instead of analysing it; the view shows its
  "which word did you mean?" picker (the split itself is already
  `:core`'s `splitIntoWords`).

### One-shot vs progressive

The screen fills the card in over time: Spalding synchronously, Tusaalanga
async, Hansard only after the dictionaries answer with nothing, the
decomposition in parallel. A CLI just wants the finished answer.

**Recommendation:** `fun lookup(word: String, prefs: WordLookupPrefs):
Flow<WordLookupResult>` — emits a first result with the synchronous parts
filled and the slow parts still `Loading`, then an updated result per slow
part as it lands. Compose `collect`s it into `WordLookupScreenState`; the
CLI takes `.last()` (or `.toList().last()`). This keeps the GUI's
incremental feel and gives the CLI the whole structure, at the cost of a
slightly heavier API than a plain `suspend fun … : WordLookupResult`. If
the incremental UX turns out not to matter, fall back to the suspend form.

### Types that move with it

- `DecomposeState` → `DecompositionOutcome`, `FailureReason` →
  `org.iutools.lookup` (plain sealed types; only depend on `MorphemeRow` /
  `Script`, both already `:core`). `FailureReason.FstNotAvailable` stays —
  it is a lookup outcome, not a UI detail.
- `DictionaryLookupResult` / `ShorterWordDictionaryResult` → renamed to
  `DictionaryHit` / `ShorterWordDictionaryHit`, moved to `org.iutools.lookup`
  (or kept in `org.iutools.dictionary` — either is fine, pick one).
- `NunavutHansardResult` → `org.iutools.corpus` as
  `HansardExamplesOutcome` (`BilingualExample` already lives there). Its
  `IndexMissing` / `IndexVersionMismatch` cases stay — a CLI source can
  report them too, or never emit them.

### The Hansard seam

`NunavutHansardLocalIndex` is Android SQLite and stays in `:composeApp`.
`:core` gets:

```
interface HansardExampleSource {
    suspend fun examplesFor(word: String): HansardExamplesOutcome
}
```

`NunavutHansardLocalIndex` becomes an adapter implementing it (it already
has the matching `fetch()` shape, minus the `Context` — the adapter closes
over the `Context`). `:cli` supplies its own implementation later (JDBC
SQLite against a pushed db, or a no-op `HansardExampleSource` that always
returns `NotFound` so the CLI works with no 550 MiB file).

## What stays in the view

`AppSettings` persistence; the FST transducer asset → file materialization
(`fstTransducerFile()`); analyzer construction + `close()` lifecycle
(passed in as the `analyzerFor` lambda); localized section titles and "not
found" messages (view maps `DictionarySource` / outcome types to strings);
display-script transcoding of the result for rendering; the multi-word
picker dialog; "load more decompositions" button wiring (calls a
`WordLookup` expand entrypoint or re-runs `lookup` with a higher limit);
all Compose, navigation, Guess Meaning inline flow.

## Migration steps

Each step: run the gate, and hand Alain a debug APK for a phone check
before moving on. Gate:

```
./gradlew :cli:test :composeApp:compileDebugKotlin \
  :composeApp:compileDebugUnitTestKotlin :composeApp:testDebugUnitTest \
  :composeApp:compileDebugAndroidTestKotlin
```

`:cli:test` includes the Hansard accuracy suite — no word may regress.

1. **Move the plain result types** (`DecompositionOutcome`/`FailureReason`,
   the two dictionary-hit types, `HansardExamplesOutcome`) into `:core`,
   pure rename + move, update imports in `:composeApp`. No behaviour
   change.
2. **Introduce `HansardExampleSource`** in `:core`; make
   `NunavutHansardLocalIndex` an adapter for it. Screen still calls the
   index directly for now.
3. **Write `WordLookup`** in `:core`, porting the orchestration from
   `findWord()` + `analyze()`: direct Spalding/Tusaalanga calls, injected
   analyzer factory, injected Hansard source, multi-word precondition,
   `Flow<WordLookupResult>`. Unit-test in `:cli` (`WordLookupTest`) with a
   fake `HansardExampleSource` — encode every gating rule from
   `WordLookupScreen.kt`'s comments as a named case (multi-word rejection;
   exact Spalding hit turns Guess Meaning off; shorter-word hit does *not*;
   decomposition failure still yields dictionary hits; auto-Hansard only
   when `!hasDefinition`; Tusaalanga fetch failure surfaced but
   non-fatal). This is the payoff — that branching is currently reachable
   only through Robolectric Compose tests.
4. **Rewrite `WordLookupScreen.findWord()`** to collect
   `WordLookup.lookup(...)` into `WordLookupScreenState`, deleting the
   duplicated orchestration. `fstTransducerFile()` stays; analyzer
   construction moves into the `analyzerFor` lambda handed to `WordLookup`.
   Biggest-risk step — full manual pass on the phone (multi-word, a word
   with a Spalding hit, one with only Tusaalanga, one the analyzer fails,
   one with a shorter-word hit, Hansard present / missing / version
   mismatch).
5. *(Separate, later.)* `:cli --define <word>`: construct `WordLookup`
   with a no-op or JDBC `HansardExampleSource` and render
   `WordLookupResult` as text.

Steps 1–4 are one task — grow a single commit with `--amend`, unpushed,
Alain pushes. Step 5 is its own commit.

## Risks / call-outs

- **The orchestration is subtle.** `WordLookupScreen.kt`'s comments record
  real bugs Alain hit: script captured once up front vs re-detected;
  dictionaries checked even when decomposition fails; shorter-word hits
  gating nothing; Guess Meaning offered precisely when the analyzer fails.
  Every one must survive the port — that is what the `:cli` `WordLookupTest`
  cases are for.
- **`Flow` vs `suspend`** is a real API commitment (see above). Decide at
  step 3, not after.
- **`:cli` has no Hansard DB story.** Step 5 starts with a no-op source so
  the CLI is usable with no large file; a real JDBC source is a later
  follow-up.
- **Analyzer lifecycle.** `WordLookup` must not `close()` the analyzer it's
  given — the caller `remember`s/`DisposableEffect`s it today and will keep
  doing so.
- **No localized strings in `:core`.** The result carries source/outcome
  enums; the view owns every user-facing string, in both languages.
