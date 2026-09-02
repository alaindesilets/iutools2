# About iutools2

Started as a Kotlin Multiplatform port of the morphological analyzer core
from [iutools](https://github.com/iutools/iutools) (Java), which decomposes
Inuktitut words into their constituent morphemes (e.g. `atuagaq` →
`{atua:atuaq/1v}{gaq:gaq/1vn}`). That analyzer (`:core`) remains the single
source of truth for morphological analysis, but the app has grown beyond a
pure port: it also looks words up directly in dictionaries (Spalding,
parsed once and embedded locally; Tusaalanga, queried live over the network
— see `:composeApp`'s `SpaldingDictionary`/`TusaalangaFetcher`), and, when
no dictionary has the word, can ask an LLM (Claude) to guess its meaning
from the morphological decomposition ("Guess Meaning"). These are new
features built for this app, not ports of anything from the original
iutools project — its own spellchecker, concordancer, dictionary/
Elasticsearch, and web/servlet layers remain explicitly out of scope, and
nothing from them was reused.

The goal is to ship this as a real mobile app (Android/iOS), not just a
library — a CLI and a Compose UI both exist as ways of exercising the same
shared analyzer core.

## Technical constraints

- Target platforms: JVM (CLI, dev/test), Android, iOS. No web browser
  target — this is not a web app.
- Kotlin Multiplatform + Compose Multiplatform is the chosen architecture:
  one shared analyzer core, one shared UI codebase, native app shells per
  platform (no separate hand-written native UIs).
- Kotlin/Native (iOS) has a much smaller stdlib surface than JVM/Android —
  no `java.util.*`, no `java.io.*`, no `@JvmField`/`@JvmStatic`/
  `@JvmOverloads`. Code in `commonMain` must build for all three targets;
  don't assume something that compiles for JVM/Android will compile for
  iOS too.

## Architecture

Gradle modules:
- **`:core`** — the analyzer itself, as a Kotlin Multiplatform library
  (`core/src/commonMain/kotlin/org/iutools/**`). This is the single source
  of truth; `:cli` and `:composeApp` both depend on it and add no analyzer
  logic of their own. Linguistic data (CSV files) is embedded as generated
  Kotlin source under `core/src/commonMain/kotlin/org/iutools/linguisticdata/dataCSV/generated/`
  rather than loaded as a runtime resource — deliberate, not an oversight,
  see the file headers for why.
- **`:cli`** — JVM-only command-line entry point (`--word`/`--interactive`/
  `--pipeline`, modeled on the original iutools CLI's own option names) plus
  the full ported accuracy/regression test suite.
- **`:composeApp`** — the graphical app (Jetpack Compose / Compose
  Multiplatform). Named `composeApp` (rather than e.g. `:app`/`:mobile`)
  because that's the module name JetBrains' official Kotlin Multiplatform
  wizard (kmp.jetbrains.com) generates by default for the Compose
  Multiplatform UI module, paired with an `iosApp` module (see below) — not
  a project-specific naming choice.
- **`iosApp`** (once it exists) — thin native Xcode wrapper embedding the
  Kotlin/Native framework; no analyzer or UI logic of its own. Also the
  default name from the same KMP wizard scaffold.

The real entry point into the analyzer is
`org.iutools.morph.r2l.MorphologicalAnalyzer_R2L.decomposeWord()`.

### Scoping methodology (keep using this for any further porting work)

Before porting or keeping any code from the original Java project, confirm
it's actually reachable from `decomposeWord()` — grep first, don't assume.
This project's history has repeatedly found large chunks of faithfully-
portable-but-dead code (unused analyzer variants, display/debug-only
formatting methods, etc.) this way; when in doubt, prune rather than port,
and say so in a comment at the point of pruning.

## Design and Coding Guidelines

### Separate data, business logic, and presentation

As much as possible, keep business logic independent of the visual
appearance of the page or dialog. In this project specifically: `:core`
must never depend on Compose or any UI type — `:composeApp` calls into
`:core`, never the reverse.

### Comments versus proper naming

- Use comments sparingly.
- If you feel the need to write a comment to explain the purpose of a
  method, function, attribute, variable, see if changing the name might
  not achieve the same clarity.
- If you feel the need to write a comment to explain a section of a
  function/method, see if you can achieve the same clarity by turning that
  section into a function/method, and giving it a clear name.
- Appropriate use of comments:
  - Put a comment at the top of each package, file, class (compulsory).
  - If a section of a function/method does something that is not clear,
    and it is difficult to clarify that section by turning it into a
    properly named function/method, then by all means, write a comment.
  - If there is something non-obvious about the rationale for why a
    particular section is written the way it is, then by all means, write
    a comment — this project relies on this heavily for platform-
    portability workarounds (e.g. why a property was renamed to avoid a
    JVM/Kotlin-Native declaration clash) and pruning decisions (why some
    original Java code was dropped rather than ported).

## Preserving data integrity

This isn't a project with production databases or deployed installations,
but the equivalent concern here is the linguistic data (CSV files under
`core/.../dataCSV/generated/`) and the accuracy-test gold standard
(`cli/src/test/kotlin/org/iutools/morph/MorphAnalGoldStandard_*.kt`):

- Never hand-retype Inuktitut/linguistic data or large data files. Either
  copy bytes directly, or if a format conversion is needed, do it with a
  small script and verify the result byte-for-byte / string-for-string
  against the original — don't trust a manual transcription.
- Don't touch the gold-standard test data or its "current expectations"
  files to make a failing test pass. If the analyzer's behavior changed on
  purpose, that's a real finding to report, not something to quietly paper
  over by editing the fixture.

## Git History

- Commit messages should focus on the PURPOSE of the commit, not the HOW.
  If at all possible, write the message in terms that an end user might
  recognize. For example, "First draft of an Android UI for the
  morphological analyzer" is preferable to "Restructure into KMP modules,
  add Android GUI" — the former says what changed from a user's
  perspective; the latter describes implementation mechanics that are
  already visible in the diff. If the implementation detail is worth
  recording, put it in the commit body, not the subject line — the subject
  stays purpose-focused, the body can explain the mechanics.
- Don't commit automatically after every change — ask, unless explicitly
  told to commit freely for a given stretch of work.
- Large, exploratory, or likely-to-be-reverted work (e.g. a platform port
  that isn't finished) belongs on its own branch, not on `main` — `main`
  should stay in a state that actually builds and runs.

## Testing

- **The non-negotiable regression gate for any change to `:core`**: run
  `./gradlew :cli:test` and confirm the Hansard accuracy suite still shows
  the same outcome histogram (as of this writing: 673 first-decomposition-
  correct / 244 correct-but-not-first / 2 correct-not-present / 0 no-
  decomps, out of 919 evaluated words). Any change to that histogram is a
  real behavioral change and must be called out explicitly, not silently
  absorbed.
- Make sure to write automated or semi-automated tests for every new
  behavior you code. When you fix a bug, start by writing a test that
  fails (because of that bug), then fix the bug. That way we are sure the
  bug won't reappear later.
- The human's role shifts from sole author of the code and tests to
  curator of what the AI produces. Whenever you create or modify tests,
  ask the human to scrutinize them carefully.

### Division of labor: what the AI runs vs. what the human runs

An AI agent working in this repo has no emulator/device (no KVM in the
devcontainer sandbox), so it must run everything it can from the command
line, every time, and hand off only what genuinely needs a device:

- **AI runs after every batch of changes**: `./gradlew :composeApp:compileDebugKotlin`
  (or the relevant module's compile task) and the JVM/Robolectric unit
  tests below — no device needed for either. If the change touched
  `:core`, also `./gradlew :cli:test` (the non-negotiable gate above). The
  AI should say explicitly, at the end of each batch, which of the human's
  Android Studio test configurations (see below) covers what it could
  *not* run itself.
- **Human runs**: anything needing a real emulator/device — launching the
  app and clicking through it, verifying real network calls (e.g. the
  Guess Meaning spike's Claude API calls), and running the `:composeApp`
  `androidTest` source set (`./gradlew :composeApp:connectedDebugAndroidTest`,
  or Android Studio's test runner) — the AI can still write and compile
  these (`./gradlew :composeApp:compileDebugAndroidTestKotlin` needs no
  device), just not execute them, unlike the JVM `test` source sets in
  `:cli` and `:composeApp`. First real test there:
  `AppSettingsEncryptionInstrumentedTest.kt`, verifying the user's Claude.ai
  API key (see `AppSettings.kt`) is genuinely encrypted on disk — something
  Robolectric can't check, since it has no software equivalent of the real,
  hardware/OS-backed Android Keystore `EncryptedSharedPreferences` relies
  on.

**Android Studio's test dropdown, mapped to Gradle** (root project name is
`iutools2`, hence the `iutools2.*` label prefix): there is
no single dropdown entry that runs every test in the whole project at
once — run both rows below when work spans both modules (`:core` has no
test source set of its own; its tests live in `:cli`).

| Android Studio entry | Equivalent Gradle command | Scope |
|---|---|---|
| "Tests in 'iutools-mobile.cli.test'" or "cli - ALL tests" (same scope, two ways to launch it) | `./gradlew :cli:test` | `:cli` (includes the Hansard gold-standard suite) |
| "Tests in 'org.iutools.app'" | `./gradlew :composeApp:testDebugUnitTest` | `:composeApp` only |

Rule of thumb: run whichever module's tests actually changed
(`:composeApp`-only work → "Tests in 'org.iutools.app'"; any `:core`
change → "cli - ALL tests"); run both if a change spans modules.

### Semi-automated tests, for what neither pure automation nor a manual
### checklist covers well

Some behavior is hard to assert programmatically (visual/layout correctness,
things that only manifest on a real device) but still benefits from a test
*driving* the app into the right state, rather than a human doing every step
by hand each time. Alain has used this pattern successfully before (the
serve-tracking app): an instrumented (on-device) test that sets up a
specific scenario, then pauses at a checkpoint with an on-screen prompt
("Does the table show 2 rows? Tap Yes/No") for a human to visually confirm
before the test continues or records a result.

This is the right tool specifically when a *pure* UI test proves genuinely
impractical to automate fully — for example, `LanguageSwitchUiTest.kt`
originally attempted a second test (type a word, run the real analyzer,
confirm the rendered meaning switches language) that hit a real background-
coroutine-never-resumes issue specific to Robolectric's simulated
environment (see that file's header comment) — exactly the kind of case
where running for real on a device sidesteps the test-harness problem
entirely, and a pause-for-human-confirmation checkpoint covers what pure
semantics-tree assertions struggled with.

The pause-for-human-confirmation checkpoint itself isn't used by anything
yet — noted here as the pattern to reach for when a UI behavior needs
on-device verification but a fully-manual checklist would be too repetitive
to redo by hand every time. The `androidTest` source set it would live in
now exists (see the "Division of labor" section above); its first test,
`AppSettingsEncryptionInstrumentedTest.kt`, didn't need the checkpoint
pattern itself (a plain string assertion, no human visual judgment
required) — a future on-device UI test is still the first candidate for it.

## Internationalisation

- The UI is available in French and English.
- The app will use the phone's language by default, but the user can override that manually in the Settings screen.
- In the code, all user-facing strings will be wrtitten in English, but with French translations avaible.
- When creating a new string or modifying an existing one, make sure that the French translation is available or up to date. Also, make sure that the French version is displayed when the user is using French.

## Instructions for AI coding agents

### How to behave towards human devs

- Don't be a sycophant. If you disagree with a decision being taken by a
  human dev, say so.
- But stay diplomatic.
- And in the end, the human has the final word.
- Before running a command that will prompt the human for approval,
  explain WHY you want to run it (not just what it does) — enough for them
  to make an informed decision, not just a mechanical description of the
  command itself.

### What language to use

- Speak to the human dev in whatever language he/she prefers.
- But all dev-facing text (code, comments, documentation) should be
  written in English, irrespective of the language the app's UI uses to
  speak to its own users.
- For example, the app's GUI may be French-only, and the dev may speak to
  you in French too, but the code itself should still be written in
  English — English is the lingua franca of the software development
  world.
