
This document contains instructions for coding agents.

Two companion documents hold the material that is not agent-specific; read
them as well:

  - doc/dev/developer-handbook.md — coding, documenting, git and testing
    practices that both human devs and agents follow.
  - doc/dev/system-architecture.md — the module layout, the target
    platforms, and other structural facts about the project.

What stays in this file is only what an AI agent specifically needs: how to
behave toward the human devs, what language to use, how the testing
workload is split between the agent and the human, and how to hand a build
to a dev.

## Learning about this project

Besides the code, you can find lots of information about the project by consulting the following documents.

README.md at the root provides an overview of the project and what it is for.

The doc/ directory contains a lots of documentation as well.

Of particular interest to you is doc/dev, which contains info for developpers.

In particular, doc/dev/plans contains information about various development tasks that are planned or in progress. Some of them may be relevant to the task that the human is working on with you. 

## Testing — what the agent runs, what the human runs

The regression gate for `:core` and the general "write a test for every new
behavior" rule are in `doc/dev/developer-handbook.md`. What
follows is only the part that is specific to an AI agent working without a
device.

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

### Giving devs a test APK to try on their phone

When a dev asks for a build to install ("compile the APK and send it",
"build me an APK", "publie un APK"):

1. `./build-android-apk.sh debug` — this now works in the devcontainer
   (x86_64 image with a bundled Android SDK); the old "macOS only" guard is
   gone. Produces `composeApp/build/outputs/apk/debug/iutools-debug.apk`
   (~22 MB).
2. Hand that file to Alain with `SendUserFile` (`status: proactive`,
   `display: attach`). Do NOT `cat`/`base64` it — `SendUserFile` uploads the
   file to his client without the bytes entering the agent's context, so it
   costs ~nothing in tokens.

He installs it from the file card in the conversation (from his phone),
allowing "install unknown apps" once. No GitHub release, no Dropbox, no
external service -- the APK goes only into his conversation. The APK is
debug-signed with `composeApp/debug.keystore`, a fixed keystore committed
to the repo (see the `signingConfigs.debug` block and its comment in
`composeApp/build.gradle.kts`), so every debug build carries the same
signature no matter which container or agent produced it -- installs go
straight over the previous one, no uninstall needed. (One exception: the
very first install after this keystore was introduced still needed a single
uninstall, to get off the old per-container `~/.android/debug.keystore`
signature.) The container's `local.properties` no
longer carries an API key (the app takes the user's Claude key from
Settings at runtime -- see `AppSettings.loadApiKey`), so the APK carries no
secret and would be safe to distribute more widely if ever needed.

**Android Studio's test dropdown, mapped to Gradle** (`rootProject.name`
is `iutools-mobile`, so IntelliJ module names are `iutools-mobile.<module>.test`):
there is no single dropdown entry that runs every test in the whole project
at once — run both rows below when work spans both modules (`:core` has no
test source set of its own; its tests live in `:cli`). The modules moved
under `apps/` (their Gradle paths stayed `:cli` / `:composeApp`), so a
`.idea/runConfigurations/` file's `<dir>` must point at
`apps/cli/src/test/kotlin` etc.; re-run the `android-studio-run-all-tests`
skill if a stored config still references the old top-level path.

| Android Studio entry | Equivalent Gradle command | Scope |
|---|---|---|
| "Tests in 'iutools-mobile.cli.test'" or "cli - ALL tests" (same scope, two ways to launch it) | `./gradlew :cli:test` | `:cli` (includes the Hansard gold-standard suite) |
| "Tests in 'org.iutools.app'" | `./gradlew :composeApp:testDebugUnitTest` | `:composeApp` only |

Rule of thumb: run whichever module's tests actually changed
(`:composeApp`-only work → "Tests in 'org.iutools.app'"; any `:core`
change → "cli - ALL tests"); run both if a change spans modules.

### Semi-automated tests, for what neither pure automation nor a manual checklist covers well

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
