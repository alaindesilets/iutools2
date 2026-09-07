# Plan: extract `GuessMeaningEngine` + an `LlmClient` interface into `:core`

Status: **COMPLETE** (2026-09-07, all 4 sub-steps). Part of
[`module-architecture-migration.md`](module-architecture-migration.md)
Phase 1. This is the last substantial Phase 1 piece and the one that
unblocks Phase 2 (the LLM silver-standard generator needs to make the same
Claude call from a headless JVM tool).

Handoff note: written because the working session may be interrupted for
days (Claude quota). Everything needed to resume cold is here.

## Where the work lives

One **unpushed commit on `main`**, subject "REFACTORING. Move some classes
from app facing packages into core." Hash changes on every amend -- don't
cite it. **Keep amending that one commit** (`git commit --amend --no-edit`)
after each sub-step. **Do NOT push** -- Alain pushes himself.

Regression gate after every sub-step (all must stay green):

```
./gradlew :cli:test :composeApp:compileDebugKotlin \
  :composeApp:compileDebugUnitTestKotlin :composeApp:testDebugUnitTest
```

`:cli:test` includes the Hansard accuracy suite -- no word may regress.

## Current state of the code

`composeApp/src/main/kotlin/org/iutools/app/GuessMeaningEngine.kt`:

- Top-level `private suspend fun callClaude(history, systemPrompt, apiKey): ClaudeCallResult`
  builds an `AnthropicOkHttpClient` per call, sends `MessageCreateParams`
  (`model = "claude-haiku-4-5"`, `maxTokens = 4096`), returns
  `ClaudeCallResult(text, latencyMs, inputTokens, outputTokens)` (a
  `private class`).
- `private fun describeError(e: Throwable): String` -- walks the full
  `cause` chain joining distinct messages, because the Anthropic SDK wraps
  every network failure in an `AnthropicIoException` whose own message is
  always "Request failed".
- `private fun ChatRole.toAnthropic(): MessageParam.Role` -- maps the
  vendor-neutral `org.iutools.llm.ChatRole` to the SDK's role enum.
- `internal object GuessMeaningEngine { suspend fun send(...) }` -- the
  entry point. Parameters:
  - `context: Context` -- used for **one thing only**:
    `GuessMeaningCostLog.record(context, MODEL, costUsd)`.
  - `history: List<ChatMessage>`, `text: String`, `systemPrompt: String`
  - `useLocalModel: Boolean` -- **dead**: no caller passes `true` (the
    on-device backend is disabled, out-of-tree, see
    `composeApp/disabled-features/local-llm/README.md`). Kept as a guard
    that refuses rather than silently falling through to Claude.
  - `apiKey: String` -- the user's own Claude.ai key (from
    `AppSettings.loadApiKey`); caller has already checked it's non-blank.
  - `modelStats: SnapshotStateMap<String, AggregatedBackendStats>` --
    **Compose type**. `send()` does `modelStats[MODEL] = (modelStats[MODEL]
    ?: AggregatedBackendStats()) + BackendCallStats(latency, in, out)`.
  - `unauthorizedErrorMessage`, `localModelDisabledMessage`,
    `genericErrorTemplate` -- pre-resolved localized strings passed in
    (same pattern as `GuessMeaningSeedLabels`). `genericErrorTemplate` is
    `"Error: %1$s"` / `"Erreur : %1$s"`, filled with `describeError(e)`
    via `String.format` (**not in commonMain** -- see below).
  - `onMessagesChanged: (List<ChatMessage>) -> Unit` -- push-based result:
    called once with the optimistic `history + user turn`, then again with
    the final list (assistant reply, or an error `ChatMessage` with
    `isError = true`). No return value.
- Side effects inside `send()`: (1) `onMessagesChanged`, (2) `modelStats`
  update, (3) `estimatedCostUsd(MODEL, in, out)?.let { GuessMeaningCostLog.record(context, MODEL, it) }`.
- Error handling: `catch (UnauthorizedException)` -> `unauthorizedErrorMessage`;
  `catch (Exception)` -> `genericErrorTemplate` filled with `describeError(e)`.

Callers (both in `:composeApp`):
- `GuessMeaningInline.kt` -> `GuessMeaningSection` (the normal inline flow
  on `WordLookupScreen`).
- `ExplanationScreen.kt` -- debug-only prompt resubmission.
Both read/write the same `conversations` / `modelStats` `SnapshotStateMap`s
hoisted in `MainActivity`.

No `GuessMeaningEngineTest` exists today. It's untested -- moving it behind
an interface is the chance to fix that (Alain wants `:core` deeply tested;
see the `feedback_core_move_tests_and_header` memory).

## Target design

### `:core/commonMain` (package `org.iutools.llm`)

```kotlin
interface LlmClient {
    suspend fun send(request: LlmRequest): LlmResponse
}

data class LlmRequest(
    val model: String,
    val maxTokens: Long,
    val systemPrompt: String,
    val messages: List<ChatMessage>,   // ChatMessage already lives here
)

sealed interface LlmResponse {
    data class Ok(
        val text: String,
        val latencyMs: Long,
        val inputTokens: Long,
        val outputTokens: Long,
    ) : LlmResponse
    data object Unauthorized : LlmResponse
    data class Failed(val message: String) : LlmResponse   // already cause-chain-flattened
}
```

Errors are modelled as `LlmResponse` variants, **not** thrown -- commonMain
can't reference the Anthropic SDK's `UnauthorizedException` /
`AnthropicIoException`. The impl catches those and maps them.

Then a pure engine (keep the name `GuessMeaningEngine`, or rename to e.g.
`GuessMeaningConversation` -- Alain's call):

```kotlin
class GuessMeaningEngine(private val llm: LlmClient) {
    suspend fun send(
        history: List<ChatMessage>,
        userText: String,
        systemPrompt: String,
        labels: GuessMeaningErrorLabels,       // unauthorized + generic template
        onMessagesChanged: (List<ChatMessage>) -> Unit,
    ): CallOutcome?     // null on the optimistic-only / error path; non-null carries stats+cost inputs
}

data class CallOutcome(
    val model: String,
    val latencyMs: Long,
    val inputTokens: Long,
    val outputTokens: Long,
)
```

- No `Context`, no Compose types, no `Dispatchers.IO` (the `LlmClient`
  impl owns threading).
- `modelStats` update and cost recording move to the **caller**: it takes
  the returned `CallOutcome`, does `modelStats[outcome.model] = ... +
  BackendCallStats(...)`, and `estimatedCostUsd(...)?.let { costLog.record(...) }`.
- `MODEL = "claude-haiku-4-5"` and `MAX_TOKENS = 4096L` -> constants in
  `:core` (on the engine, or a small `GuessMeaningModel` object).
- `useLocalModel` guard: keep it (cheap), as a `Boolean` param that makes
  `send()` emit the `localModelDisabled` message and return `null` without
  calling `llm`.
- `String.format` for the generic error template: reuse the
  `fillPlaceholder` helper -- currently `private` in
  `GuessMeaningSeedPrompt.kt`; promote it to `internal` in a shared file
  (e.g. `Placeholders.kt`) in `org.iutools.llm`.

### The Anthropic impl -- placement decision (MADE 2026-09-07: option C)

The decision (interface can't be a `commonMain` dep of a KMP module, so the
impl has to sit in a JVM/Android source set of `:core` OR a sibling module)
went to **C: a new plain-JVM `:enrichment` module**, not A (source set
inside `:core`). Reasons: (1) a `jvmAndroidMain` intermediate source set
under `:core`'s `com.android.kotlin.multiplatform.library` plugin is
*more* fiddly than a plain `kotlin("jvm")` module; (2) the other Guess
Meaning adapters still to move -- `SpaldingDictionary` (`context.assets`),
`GuessMeaningCostLog` (`SharedPreferences`), `TusaalangaFetcher`
(`android.text.Html`) -- are Android-coupled, so `:core/androidMain` would
put them out of reach of a headless CLI; a plain-JVM `:enrichment` forces
the de-Androidification the CLI needs anyway, and the app + CLI share one
module. `:core` stays dependency-free, holding only pure logic +
interfaces.

`:enrichment` = `plugins { \`java-library\`; kotlin("jvm") }`, `api(project(":core"))`,
`implementation("com.anthropic:anthropic-java:2.34.0")`,
`implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")`,
`testImplementation(kotlin("test"))`. Modelled on `fst/build.gradle.kts`.

### `:composeApp` (sub-step 4, NOT done yet)

- Add `implementation(project(":enrichment"))`.
- `GuessMeaningSection` / `ExplanationScreen` construct
  `LlmClient_Anthropic { apiKey }` (from `:enrichment`), build a
  `org.iutools.llm.GuessMeaningEngine(client)` (the `:core` class), call
  its `send(...)`, and from the returned `CallOutcome?` do the
  `modelStats[outcome.model] = ... + BackendCallStats(...)` update and
  `estimatedCostUsd(...)?.let { GuessMeaningCostLog.record(context, ...) }`.
- The pre-translated strings currently passed as
  `unauthorizedErrorMessage` / `localModelDisabledMessage` /
  `genericErrorTemplate` now go in a `GuessMeaningErrorLabels(...)`.
- Delete `composeApp/.../org/iutools/app/GuessMeaningEngine.kt` entirely
  (its `callClaude` / `describeError` / `toAnthropic` / `ClaudeCallResult`
  are now `:enrichment`'s `LlmClient_Anthropic` + `:core`'s
  `causeChainMessage`; its `send()` branching is `:core`'s
  `GuessMeaningEngine`).
- `conversations` / `modelStats` `SnapshotStateMap` wiring in
  `MainActivity` is unchanged.
- Gate must include `:composeApp:testDebugUnitTest`
  (`GuessMeaningSectionApiKeyTest`, `GuessMeaningStringsTest`).

## Sub-steps (each: edit -> gate -> `commit --amend --no-edit`)

1. **[DONE 2026-09-07]** commonMain interface + types. Added `LlmClient`,
   `LlmRequest`, `LlmResponse`, `CallOutcome` (`core/.../llm/LlmClient.kt`),
   `GuessMeaningErrorLabels` (own file), and moved `fillPlaceholder` to its
   own file `core/.../llm/Placeholders.kt` (removed the `private` copy in
   `GuessMeaningSeedPrompt.kt`). Made it `public` + added
   `cli/.../llm/PlaceholdersTest.kt` (5 cases) -- `:core` has no test
   source set, so a directly-tested helper has to be public. Nothing
   consumes the new interface/types yet. Gate green, amended.
2. **[DONE 2026-09-07]** Pure `GuessMeaningEngine` in `:core`
   (`core/.../llm/GuessMeaningEngine.kt`): `class GuessMeaningEngine(private
   val llm: LlmClient)`, `suspend fun send(history, userText, systemPrompt,
   useLocalModel, labels, onMessagesChanged): CallOutcome?`. `MODEL` /
   `MAX_TOKENS` are `const` in its companion. No `Context`, no Compose, no
   coroutines library (just `suspend`). Test:
   `cli/.../llm/GuessMeaningEngineTest.kt`, 8 cases with a fake `LlmClient`
   (Ok reply + returned `CallOutcome`; optimistic first `onMessagesChanged`
   = user turn only; request carries model/maxTokens/systemPrompt/full
   history; Unauthorized + Failed -> error turn + null; blank text ->
   no-op, model untouched; `useLocalModel` -> disabled notice, model
   untouched). Added `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")`
   to `:cli` for `runBlocking` (`:core` stays coroutine-lib-free). Not
   wired to the app yet. Gate green (the R2L `AssertRuntime` timing flake
   fired once -- "IMPROVED" -- and cleared on re-run; accuracy metrics
   unchanged: R2L R/P 100%, 917/919, 673/919).
3. **[DONE 2026-09-07]** Anthropic impl -- went with **C** (see the
   revised placement section above). New `:enrichment` module
   (`enrichment/build.gradle.kts` + `settings.gradle.kts`).
   `enrichment/.../llm/LlmClient_Anthropic.kt` -- `class
   LlmClient_Anthropic(private val apiKey: () -> String) : LlmClient`,
   `withContext(Dispatchers.IO)` around the SDK call, maps
   `UnauthorizedException` -> `LlmResponse.Unauthorized` and any other
   `Exception` -> `LlmResponse.Failed(causeChainMessage(e))`, success ->
   `LlmResponse.Ok`. `describeError` moved to `:core` as the pure public
   `causeChainMessage(Throwable)` (`core/.../llm/CauseChainMessage.kt`)
   with `cli/.../llm/CauseChainMessageTest.kt` (5 cases). `:composeApp`
   NOT touched yet -- its old `GuessMeaningEngine.kt` still has the
   duplicate `callClaude`; sub-step 4 deletes it. Gate green (R2L timing
   flake fired, cleared on re-run; `:enrichment` compiles against
   `anthropic-java:2.34.0`).
4. **[DONE 2026-09-07]** Rewire `:composeApp`.
   `composeApp/build.gradle.kts`: `implementation(project(":enrichment"))`
   replaces the direct `com.anthropic:anthropic-java` dep (nothing in
   `:composeApp` imports the SDK any more -- only `LlmClient_Anthropic`
   does, from `:enrichment`). `composeApp/.../app/GuessMeaningEngine.kt` ->
   `GuessMeaningTurn.kt`, now a single `suspend fun sendGuessMeaningTurn(...)`
   that builds `GuessMeaningEngine(LlmClient_Anthropic { apiKey })`, passes
   a `GuessMeaningErrorLabels`, and -- only on a non-null `CallOutcome` --
   does the `modelStats` update + `GuessMeaningCostLog.record(context, ...)`.
   The two call sites (`GuessMeaningInline.kt`, `ExplanationScreen.kt`)
   call `sendGuessMeaningTurn(...)` with the same named args as before.
   `callClaude` / `describeError` / `toAnthropic` / `ClaudeCallResult` are
   gone from `:composeApp`. Stale `GuessMeaningEngine.kt` comment refs
   fixed in `MainActivity.kt`, `WordLookupScreen.kt`. Gate green
   (`:composeApp:testDebugUnitTest` incl. `GuessMeaningSectionApiKeyTest` /
   `GuessMeaningStringsTest`).

## Gotchas

- **Compose leak**: `modelStats: SnapshotStateMap` must not cross into
  `:core`. Return `CallOutcome`, let the caller mutate the map.
- **`Context` is only for the cost log** -- removing the cost-recording
  call from the engine removes the `Context` dependency entirely.
- **Error text stays translated**: the engine returns
  `LlmResponse.Failed(rawEnglishMessage)`; the caller (or the engine, via
  the passed-in `genericErrorTemplate`) wraps it in the localized
  template. Do not surface raw SDK text in the UI (Alain's standing rule).
- **No `String.format` in commonMain** -- use `fillPlaceholder`.
- **`useLocalModel`** is dead but the guard is deliberate; keep it.
- **`AnthropicOkHttpClient` is built per call** today (the key can change
  at runtime via Settings). Keep per-call construction, or have the impl
  take a `() -> String` key provider.
- **`anthropic-java` version** `2.34.0` is a bare string in
  `composeApp/build.gradle.kts` (near a comment about its bundled
  `META-INF/DEPENDENCIES`). If option A, move/duplicate that coordinate;
  consider finally adding a `libs.versions.toml`.
- Package `org.iutools.llm` already holds: `ChatMessage`/`ChatRole`,
  `CandidateMeanings`, `GuessMeaningCacheKey`, `LlmCost`
  (`estimatedCostUsd`), `ModelStats` (`AggregatedBackendStats` /
  `BackendCallStats`), `MorphemeRow`, `GuessMeaningSeedLabels`,
  `guessMeaningSeedPrompt`, `README.md`.

## Not in this plan

`GuessMeaningCostLog` (SharedPreferences + Calendar), `SpaldingDictionary`
(`org.json` + assets), `TusaalangaFetcher` (`HttpURLConnection` +
`android.text.Html`) -- the other Phase 1 leftovers. Each needs its own
small design decision; see the migration plan's Phase 1 section.
