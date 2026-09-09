# System Architecture

Below is a description of the main directories in the project. Where a
directory is also a Gradle module, its module path is shown in parentheses.

- **core/** (`:core`) — the morphological analyzer and all other domain
  logic shared across the apps: the CLI, the Android app (`composeApp`),
  and the planned Desktop build.
- **fst/** (`:fst`) — a still-experimental Finite State Transducer
  implementation of Uqailaut, the R2L morphological analyzer originally
  developed by Benoit Farley: `MorphologicalAnalyzer_FST` plus a vendored
  pure-Java HFST optimized-lookup reader. Kept out of both `core/` and
  `apps/` because `core/`'s toolchain can't compile its vendored Java
  source. See `fst/README.md`.
- **apps/** — the entry-point modules, one per way of running the tools:
  - **`apps/cli/`** (`:cli`) — JVM-only command-line entry point
    (`--word` / `--interactive` / `--pipeline`, modeled on the original
    iutools CLI's own option names), plus the full ported
    accuracy/regression test suite (`:cli:test` is the regression gate).
  - **`apps/composeApp/`** (`:composeApp`) — the graphical app: a plain
    Android app today (`com.android.application` + Jetpack Compose), with a
    Compose Desktop build (macOS / Windows / Linux) as the next planned
    target — a sibling `apps/desktopApp/`, or a desktop target added here.
    The `composeApp` name is a leftover from the KMP project wizard, not a
    sign the project is multiplatform.
- **data/** — data artefacts that may have value outside this project
  (dictionaries, corpora) and the generators that produce them.
- **doc/** — documentation about the project, both user-facing and
  dev-facing.
- **gradle/** — the Gradle wrapper (a pinned Gradle version that
  `./gradlew` downloads on first use). Not project-specific.
- **tools/** — standalone developer scripts that are not part of the build.
  Currently the tooling that builds and publishes the Nunavut Hansard
  bilingual-examples index (see `tools/README-hansard.md`).
- **hooks/** — versioned git hooks, opt-in via
  `git config core.hooksPath hooks` (the dev container does this
  automatically). Currently a `pre-commit` guard that blocks committing
  large or private/licensed reference data. See `hooks/README.md`.

The real entry point into the analyzer is
`org.iutools.morph.r2l.MorphologicalAnalyzer_R2L.decomposeWord()`.


## Dev container

`.devcontainer/` defines a VS Code / Claude Code development container. It
exists for two reasons:

- **One-step setup for human devs.** The image (Microsoft's
  `devcontainers/java:21` base, plus the Android SDK command-line tools)
  already has the JDK, the Android SDK, and Claude Code installed, so a
  fresh clone builds without hunting down toolchain versions.
- **A locked-down sandbox for running coding agents autonomously.** Several
  hardening choices only make sense in that light:
  - Outbound network is restricted by `init-firewall.sh` at container
    start (hence the `NET_ADMIN` / `NET_RAW` capabilities).
  - `.devcontainer/` is re-mounted **read-only** inside the container, so
    an agent can't weaken its own sandbox for the next rebuild. The files
    stay freely editable on the host.
  - `/shared/ref` (private / licensed reference data) is mounted
    **read-only** — see `doc/dev/developer-handbook.md` → "Private data on
    `/shared`".
  - `sudo` is removed for the container user.

The container is built as x86_64 even on Apple Silicon, so Google's
x86_64-only Android build tools (`aapt2`) run as native binaries rather
than under emulation.

Each clone of the repo gets its own container (named after the folder), but
they share named volumes for the Gradle cache, the Android SDK, and the
Claude Code config, so opening a second clone doesn't re-download or
re-authenticate anything.

Those shared volumes are a build-cache convenience, not a channel between
agents: agents in separate clones exchange work through `git` (`origin`),
and any non-git file has to go through the host-managed `/shared`.

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

## Internationalisation

The UI is available in English and French. By default the app follows the
phone's language; the user can override that in the Settings screen.

The rule for keeping each string and its French translation in sync as you
change the code is in `doc/dev/developer-handbook.md` →
"Internationalisation".