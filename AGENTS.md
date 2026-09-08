
This project is a Kotlin port of some of the apps in the original 
iutools project (which was a web-based application). This port aims 
at making those tools availble as Android or Desktop (OSX, Linux,
Windows) apps.

The tools and apps in this project help speaker, writers and learner of
Inuktut, the language of the Inuit people.

The project currently includes user facing tools like:

- Word dictionary
- Morpheme dictionary

And we plan to eventualy provide additional ones like:
- Spell checker
- Translation Memory
- Reading assistant for second language learner.

The project also includes developer facing tools like:

- Morphological decomposer
- Transliterator
- Tokenizer

Which are available in the form of Kotlin classes, as well as a Command Line Interface.

At the moment, the project only ships as an Android mobile app. The next
target is **Desktop (macOS — Alain's main platform — plus Linux and
Windows)**. **iOS is not planned for the short term**, and there is no web
target.

## Technical constraints

- Target platforms: **Android and Desktop (macOS / Linux / Windows) — both
  JVM** — plus the JVM CLI for dev/test. No iOS short-term. No web browser
  target — this is not a web app.
- Because every target is JVM, `:core` and the other shared modules are
  plain Kotlin/JVM libraries. Use `java.*` / JVM APIs freely. **Kotlin
  Multiplatform is being dropped** from `:core` (it was the only KMP module
  and bought nothing for an all-JVM target set) — see
  `doc/dev/plans/drop-kmp-core.md`; until that lands, `:core` is still
  configured as KMP with a single `commonMain` source set.
- If iOS is ever revisited it is a project of its own (convert `:core`
  back to multiplatform, write a Compose Multiplatform iOS shell) — not a
  constraint to design around now.

## Architecture

Directory grouping: **`core/`** + **`fst/`** (the analyzer and its
carved-out FST reader), **`apps/`** (entry-point modules), **`data/`**
(passive data + generators). See
`doc/dev/plans/module-architecture-migration.md`. The grouping is
directory-level only — Gradle paths stay `:cli` / `:composeApp` (the dirs
moved to `apps/`, the module names did not).

Gradle modules:
- **`:core`** (`core/`) — the morphological analyzer itself, plus reusable
  domain logic including the Guess Meaning enrichment and its one concrete
  LLM client (`org.iutools.llm`, `LlmClient_Anthropic`). A plain Kotlin/JVM
  library (`core/src/main/kotlin/org/iutools/**`). This is the single
  source of truth; the app modules depend on it and add no analyzer logic
  of their own. Linguistic data (CSV files) lives under
  `data/grammar/linguistic-data/` and is loaded by `:core` at runtime via
  the JVM classpath (`LinguisticDataCSV.kt`).
- **`:fst`** (`fst/`) — `MorphologicalAnalyzer_FST` + the vendored pure-Java
  HFST optimized-lookup reader. Its own module (not part of `:core`)
  because `:core`'s toolchain can't compile the Java source; stays at the
  top level, not under `apps/`. See `fst/README.md`.
- **`:cli`** (`apps/cli/`) — JVM-only command-line entry point
  (`--word`/`--interactive`/`--pipeline`, modeled on the original iutools
  CLI's own option names) plus the full ported accuracy/regression test
  suite (`:cli:test` is the regression gate).
- **`:composeApp`** (`apps/composeApp/`) — the graphical app: today a plain
  Android app (`com.android.application` + Jetpack Compose). Named
  `composeApp` (rather than `:app`/`:mobile`) because that's the module
  name JetBrains' KMP wizard generates for the Compose UI module; kept for
  continuity, not because the project is multiplatform. A Compose
  **Desktop** build is the next planned target (a sibling `apps/desktopApp/`
  or a desktop target here).

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

### Documenting

There are several ways to document things in this project.

- Comments
- Agent's own memory
- Planning documents
- README files

Below are details about the proper use of each approach.

#### Comments versus proper naming

- Use comments sparingly.
- If you feel the need to write a comment to explain the purpose of a
  method, function, attribute, variable, see if changing the name might
  not achieve the same clarity.
- If you feel the need to write a comment to explain a section of a
  function/method, see if you can achieve the same clarity by turning that
  section into a function/method, and giving it a clear name.
- Appropriate use of comments:
  - Put a comment at the top of each package, file, class (compulsory).
  - In that top-of-file/class/function comment, lead with the overview:
    the first sentence(s) say what the thing does, globally. Rationale,
    mechanics and edge cases come after — not first. More on what
    "the overview" means, because a terse abstract label is NOT it:
    - Write for a reader who does not already know this codebase or this
      feature. Plain, unhurried expository prose — a few short sentences —
      not one dense sentence stacked with clauses and semicolons, and not
      a clever compressed one-liner.
    - "What it does, globally" is allowed to start from *why the thing
      exists*. The motivating context ("we cache LLM replies to avoid
      re-calling; this builds the cache key") is often the fastest way in,
      and a concrete example (a real value, a real prompt fragment)
      belongs here when it makes the point land.
    - Litmus: could someone who has never seen this feature read the
      comment alone and correctly say what the class is for?
      This FAILS the litmus (assumes you know the feature, reads as an
      aphorism):
        /* Identity of one "guess the meaning" attempt, used to cache the
         * model's reply. Every field here can change the answer. */
      This PASSES:
        /*
         * To avoid calling the LLM every time, we cache some of its
         * replies. This class builds the cache key for one "Guess
         * Meaning" request.
         *
         * The key captures the word, plus the other things that change
         * the LLM's answer (e.g. the exact prompt text used).
         */
  - Don't enumerate the code's callers. One or two examples of callers
    are fine; an exhaustive list is a coupling smell (the callee
    "knowing" its clients) and goes stale as callers come and go.
    Better: describe the *kind* of caller or input abstractly rather
    than naming classes ("some source — a dictionary, a corpus index, a
    web lookup" rather than "used by SpaldingDictionary,
    NunavutHansardLocalIndex").
  - If a section of a function/method does something that is not clear,
    and it is difficult to clarify that section by turning it into a
    properly named function/method, then by all means, write a comment.
  - If there is something non-obvious about the rationale for why a
    particular section is written the way it is, then by all means, write
    a comment — this project relies on this heavily for pruning decisions
    (why some original Java code was dropped rather than ported) and the
    occasional build/portability workaround.

#### Agent Memory

Coding agents may use their personal memory to remember details about what they are currently working on and where they are at.

Use this for the kinds of details that do not need to be shared with other agents or human devs.

#### Planning documents

The doc/dev/plans/ directory contains files that describe plans for tasks, whether they be future ones, or tasks that are undergoing.

The documents in that directory are not meant to be permanent. They are meant to communicate plans and their current status, with other agents and human devs.

#### README.md files

Each directory in this project may contain a README.md that describe the purpose and structure of that directory (and its descendants).

This type of documentation is meant to be more permanent than the docs found in doc/dev/plans/. But if the directory is in a state of flux, the README may explain this and even refer to a planning document, while the directory is being modified.

Keep it high-level: a README states the *intent* of the directory. It is
not an inventory of the files inside it, nor a per-file rationale — that
belongs in each file's own top comment, or in a planning document.

## Preserving data integrity

This isn't a project with production databases or deployed installations,
but the equivalent concern here is the linguistic data (CSV files under
`data/grammar/linguistic-data/`) and the accuracy-test gold standard
(`cli/src/test/kotlin/org/iutools/morph/MorphAnalGoldStandard_*.kt`):

- Never hand-retype Inuktitut/linguistic data or large data files. Either
  copy bytes directly, or if a format conversion is needed, do it with a
  small script and verify the result byte-for-byte / string-for-string
  against the original — don't trust a manual transcription.
- Don't touch the gold-standard test data or its "current expectations"
  files to make a failing test pass. If the analyzer's behavior changed on
  purpose, that's a real finding to report, not something to quietly paper
  over by editing the fixture.

## Shared reference data (`/shared`)

Large, private, or licensed material that several agents need but that must
**never** enter git lives on a host directory mounted into the containers,
**read-only**, at `/shared/ref`: the recovered Nunavut Living Dictionary,
the gov.nu.ca crawl, raw `.bak` / corpus archives. Alain populates it from
the host side; agents only read it.

- **Never `git add` anything copied out of `/shared`.** The
  `hooks/pre-commit` guard (enable with `git config core.hooksPath hooks`;
  the agent devcontainers do this automatically) rejects staged files that
  are over 5 MiB or whose content fingerprints as one of these datasets,
  and `.gitignore` covers the obvious paths. `git commit --no-verify`
  bypasses the hook — don't, unless you have confirmed it is a false
  positive.
- There is **no agent-writable shared space** by design. To hand a non-git
  file to another agent, ask Alain to place it on `/shared`.
- Rights: most of the recovered Living Dictionary is drawn from
  copyrighted third-party dictionaries (only the Schneider subset is
  licensed). Treat it as *look-but-don't-incorporate* — it may inform your
  own judgement, but its content does not go into iutools2 code, data, or
  prompts. See `doc/dev/plans/offline-dictionary-generation.md` → "Rights".

## Git guidelines

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
- **Recommended workflow (a recommendation, not a rule — another dev may
  prefer a different flow; the only hard requirement is that `main` builds
  and stays green, and that branches don't accumulate):**
  - **One task = one commit.** Grow it as the work progresses with
    `git commit --amend --no-edit` (drop `--no-edit` to refine the
    message). Don't let a stack of unpushed commits build up — several
    unpushed commits almost always means one task, which should be one
    amended commit.
  - **Ship each task as it finishes:** `git pull --rebase origin main`,
    re-run the regression gate on the updated base, then `git push`. If
    the push is rejected, repeat. `main` is the trunk; no feature branch,
    no PR step. (An AI agent still confirms before `git push` unless Alain
    has said to push freely for this stretch of work.)
- **Multiple agents in parallel:** each runs in its **own independent clone**,
  all on `main`; `origin` is the only channel between them. Because every
  commit is pushed as above, an agent that needs the other's work just
  `git pull --rebase origin main` — there is nothing unpushed to chase.
  No git worktrees (two worktrees can't both hold `main`, and a worktree
  straddling the devcontainer boundary is fragile — see the
  `project_workspace_is_linked_git_worktree` memory), no cross-clone
  `sibling` remotes. Give the two agents **disjoint scopes** (e.g. one on
  `:composeApp`, one on `:core` / FST / `data/grammar`) so their commits
  rarely touch the same files; if cross-agent conflicts are frequent, fix
  the scoping, not the git flow. Full setup:
  `doc/dev/plans/agent-parallelism-two-clones.md`.
  - **Exception**: genuinely experimental work whose outcome is still
    uncertain (e.g. the FST prototype in its early days) does warrant a
    real named branch — that's what named branches are for. When creating
    one, state explicitly what resolves it (merged once X is proven,
    dropped if Y doesn't pan out), and act on that condition as soon as
    it's met — delete the branch (local and remote) the moment its content
    is merged or abandoned. Several orphaned branches whose purpose nobody
    remembered were found and deleted in September 2026 — an untracked
    branch is a maintenance cost, not a free option.

## Testing

- **The non-negotiable regression gate for any change to `:core`**: run
  `./gradlew :cli:test` and confirm the Hansard accuracy suite doesn't
  report any word as regressed against its committed per-word snapshot
  (`MorphAnalCurrentExpectations_Hansard.kt`; the FST has its own,
  `MorphAnalCurrentExpectations_FST_Hansard.kt`). Reported as 4 metrics
  (as of this writing, R2L: Recall 100.0%, Precision 100.0% -- both exactly
  100% by construction, since `all_correct_decomps` is itself generated by
  running R2L -- reference decomp present anywhere 917/919 (99.8%),
  reference decomp in top-1 673/919 (73.2%), out of 919 "fair" words). Any
  word regressing is a real behavioral change and must be called out
  explicitly, not silently absorbed (an *improvement* is fine and doesn't
  fail the test -- see `MorphologicalAnalyzer__AccuracyTest.kt`'s own
  docs for why).
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
