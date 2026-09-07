# `:enrichment` -- the outside-world implementations behind `:core`'s enrichment interfaces

`:core` holds the pure logic and the *interfaces* for the word-enrichment
features -- "Guess Meaning" and the dictionary lookups that feed it: given a
word and its analysis, build the LLM prompt, interpret the reply, decide
what counts as a hit. This module holds the *implementations* of those
interfaces -- the parts that actually reach the outside world: the LLM call
(`LlmClient_Anthropic`), and, as they are moved here, the live web
dictionary fetchers and the parsed bundled dictionaries.

## Why it is not under `:core`

`:core` is a Kotlin Multiplatform library whose shared code deliberately
depends on nothing external and does no I/O. These implementations pull in
JVM-only libraries (the Anthropic SDK, an HTTP client, a JSON parser) that
cannot be dependencies of a multiplatform `commonMain` at all. Keeping them
in their own module lets `:core` stay dependency-free, holding only logic
and contracts.

This is the same move, for the same kind of reason, that `:fst` already
represents (see `fst/README.md`): analyzer-group code that a shipped app
calls at runtime, carved into its own module because of a mechanical
dependency constraint, not because it belongs to the UI.

## Why a plain JVM module, and not Android

So the exact same implementations can run headless. `:composeApp` uses them
today; a future command-line tool that produces the same word information a
user sees on screen would use them too. Android-specific mechanics (asset
loading, encrypted key storage, HTML handling) are kept out -- callers
inject what they need. For that reason `:enrichment` stays at the top level
next to `core/` and `fst/`, never under an eventual `apps/`.

## Status

New and being populated. `LlmClient_Anthropic` is here; `SpaldingDictionary`,
`TusaalangaFetcher`, and the Guess Meaning cost log are still in
`:composeApp` and move here as they are de-Androidified. See
`doc/dev/plans/guess-meaning-engine-to-core.md` and
`doc/dev/plans/module-architecture-migration.md`.
