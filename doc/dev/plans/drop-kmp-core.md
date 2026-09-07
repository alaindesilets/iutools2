# Plan: drop Kotlin Multiplatform from `:core` (Android + Desktop only)

Status (2026-09-07): **Steps 1-3 DONE**, in one commit "Drop Kotlin
Multiplatform from the shared core" (unpushed; amend it for any follow-up).
Step 4 (the remaining Android-coupled adapters) not started. Near-term
target set: **Android + Desktop (macOS, Linux, Windows)** — all JVM.
**iOS is not planned short-term.** No web.

## Why

`:core` was the **only** KMP module in the repo. `:composeApp` is a plain
`com.android.application` (not Compose Multiplatform — its plugins are
`com.android.application` + `org.jetbrains.kotlin.plugin.compose`).
`:cli` and `:fst` are plain `kotlin("jvm")` (as was `:enrichment`, now
folded in).

`:core` carries the full KMP apparatus (multiplatform plugin,
`com.android.kotlin.multiplatform.library`, `commonMain` source set, the
`android { }` target) and gets **nothing** for it today:

- Its code is 100% in `commonMain`, uses no Android API, loads its CSVs via
  JVM classpath resources (`java.io`). It is already a plain Kotlin/JVM
  library wearing KMP config.
- Android + Desktop are both JVM. `:fst` (plain `kotlin("jvm")`) is
  consumed by `:composeApp` (Android) with no trouble — AGP dexes a plain
  `.jar`. So `:core`'s `android` target isn't needed either.
- The one concrete cost we hit: a plain Java `.jar` (the Anthropic SDK)
  can't be a `commonMain` dependency, which is the whole reason
  `:enrichment` exists as a separate module. Drop KMP and that reason
  disappears.

What we give up: iOS becomes a real future migration (convert `:core` back
to KMP, split `commonMain`/`jvmMain`, add an `iosApp`) rather than "add a
target". Given iOS is explicitly deferred and `:composeApp` would need a
full Compose-Multiplatform rewrite for iOS anyway (independent of `:core`),
this is an acceptable trade. The analyzer core is pure Kotlin + kotlinx, so
a future re-KMP-ification is mechanical.

## Target state

| module | before | after |
|---|---|---|
| `:core` | `kotlin("multiplatform")` + `com.android.kotlin.multiplatform.library`, `commonMain` only, `jvm` + `android` targets | `java-library` + `kotlin("jvm")` (like `:fst`), `src/main/`, JVM only |
| `:enrichment` | separate plain-JVM module holding `LlmClient_Anthropic` + the Anthropic dep | **deleted** — folded into `:core` |
| `:cli`, `:fst`, `:composeApp` | depend on `:core` | unchanged (still depend on `:core`) |

`:core` gains the deps that were on `:enrichment`
(`com.anthropic:anthropic-java:2.34.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0`)
plus whatever the future de-Androidified adapters need. That's fine — `:core`
being dependency-free was a *consequence* of the KMP `commonMain`
constraint, not a goal in itself.

## Steps (each: edit -> gate -> commit on `main`)

Gate = `./gradlew :cli:test :composeApp:testDebugUnitTest :composeApp:compileDebugKotlin :composeApp:compileDebugAndroidTestKotlin`
(+ `./build-android-apk.sh debug` and a phone check before pushing the
final commit). The R2L `AssertRuntime` timing check flakes on this host —
re-run `:cli:test` to clear; accuracy metrics must not move (R/P 100%,
917/919, 673/919).

### Step 1 — docs / direction  **[DONE]**

Updated the iOS / KMP references (inventory below): AGENTS.md's "Technical
constraints" + "Architecture" sections, the two other plan docs,
`enrichment/README.md`, `SimpleLruCache.kt`, `VENDORED.md`. State: Android
+ Desktop (macOS, Linux, Windows); iOS not planned short-term; `:core`
plain Kotlin/JVM.

### Step 2 — convert `:core` to `kotlin("jvm")`  **[DONE]**

- `core/build.gradle.kts`: replace the `kotlin { jvm{}; android{}; sourceSets{} }`
  block with the `:fst` shape — `plugins { \`java-library\`; kotlin("jvm") }`,
  `kotlin { jvmToolchain(21) }`, `tasks.test { useJUnitPlatform() }` (though
  `:core` has no tests — they live in `:cli`).
- `git mv core/src/commonMain/kotlin core/src/main/kotlin`. (No
  `commonTest` — nothing to move.)
- The linguistic-data resources: `commonMain { resources.srcDir("../data/grammar/linguistic-data") }`
  becomes `sourceSets["main"].resources.srcDir("../data/grammar/linguistic-data")`.
  Same classpath-resource mechanism `LinguisticDataCSV.kt` already uses;
  the accuracy test proves byte-for-byte load.
- Drop the `android { namespace/compileSdk/minSdk }` block — `:core` has no
  Android manifest, resources, or APIs, so nothing depends on the AAR
  variant's Android metadata.
- Root `build.gradle.kts`: `kotlin("multiplatform")` and
  `com.android.kotlin.multiplatform.library` plugin declarations become
  unused once `:core` converts — remove them. (`org.jetbrains.compose` is
  *already* unused — `:composeApp` uses `kotlin.plugin.compose`, not the
  JetBrains Compose plugin — clean it up in the same pass.)
- Gate. Expect: `:composeApp` consumes `:core` as a plain jar exactly as it
  consumes `:fst` today.

### Step 3 — fold `:enrichment` into `:core`  **[DONE]**

- `git mv enrichment/src/main/kotlin/org/iutools/llm/LlmClient_Anthropic.kt core/src/main/kotlin/org/iutools/llm/`.
- `core/build.gradle.kts` `dependencies`: add
  `implementation("com.anthropic:anthropic-java:2.34.0")` and
  `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")`.
- `:composeApp` `build.gradle.kts`: `implementation(project(":enrichment"))`
  -> nothing (it already has `implementation(project(":core"))`).
- `:cli` `build.gradle.kts`: it still needs the test-only
  `kotlinx-coroutines-core` for `runBlocking` — keep that line (or drop it
  if `:core` now exposes coroutines transitively via an `api` dep; decide
  then).
- `settings.gradle.kts`: remove `":enrichment"`.
- `rm -r enrichment/` (incl. `enrichment/README.md`).
- Update `enrichment/README.md`'s content — the "why a separate module"
  rationale — into a short note in `core/.../llm/`'s README instead (the
  I/O implementations now just live in `:core` alongside the interfaces).
- Gate + APK + phone check, then push.

### Step 4 — (later, not blocking) the remaining Android-coupled adapters

`GuessMeaningCostLog` (SharedPreferences + Calendar), `SpaldingDictionary`
(`context.assets` + `org.json`), `TusaalangaFetcher` (`HttpURLConnection` +
`android.text.Html`) still sit in `:composeApp`. With `:core` now plain
JVM, these can move into `:core` **once de-Androidified** (asset loading ->
classpath resources; `SharedPreferences` -> an injected store; `org.json`
-> `kotlinx.serialization` or a tiny parser; `android.text.Html` -> a small
entity decoder). Same headless-CLI motivation as before. Independent of
Steps 1-3.

## iOS / KMP reference inventory (Step 1)

| file:line | now | change to |
|---|---|---|
| `AGENTS.md` ~26-29 | future platforms list incl. `iOS` and `Web services` | Desktop (macOS/Windows/Linux) as the next target; iOS + web explicitly *not* planned |
| `AGENTS.md` ~33 | "Target platforms: JVM (CLI, dev/test), Android, iOS." | "Android, and Desktop (macOS/Linux/Windows) — all JVM. No iOS short-term, no web." |
| `AGENTS.md` ~35-37 | "Kotlin Multiplatform + Compose Multiplatform is the chosen architecture" | plain Kotlin/JVM shared `:core`; Android app today, a Compose Desktop app planned; link this doc |
| `AGENTS.md` ~38-44 | the "iOS is a real eventual target ... don't pre-emptively avoid JVM-only APIs in `commonMain`" paragraph | drop it — everything is JVM, use `java.*` freely; if iOS is ever revisited it's a project of its own, not a live constraint |
| `AGENTS.md` ~49 | "`:core` ... as a Kotlin Multiplatform library (`core/src/commonMain/...`)" | "plain Kotlin/JVM library (`core/src/main/...`)" |
| `AGENTS.md` ~58-66 | `composeApp` naming rationale + the `iosApp` module bullet | keep the historical `composeApp` name note; drop the `iosApp` bullet (or mark "not planned") |
| `AGENTS.md` ~153 | "JVM/Kotlin-Native declaration clash" example in the comments guidance | genericize to a plain portability example, or drop |
| `core/.../lib/SimpleLruCache.kt` ~11-12 | "NOT available in Kotlin/Native, so this will need to move behind expect/actual ... if/when an iOS port" | "targets are JVM-only (Android + Desktop); revisit only if iOS is ever added" |
| `fst/src/main/java/net/sf/hfst/VENDORED.md` ~7 | "on Android/iOS, not just a dev machine" | "on Android and desktop" |
| `doc/dev/plans/module-architecture-migration.md` | "Axis 2 — platform: KMP source sets", the `iosMain`/`iosApp` table rows, "Phase 4 — platform expansion" iOS bullet | add a header note that for the Android+Desktop-only reality this is superseded by `drop-kmp-core.md`; leave the historical text |
| `doc/dev/plans/fst-analyzer-plan.md` ~264-276 | speculative future spellchecker musings mentioning iOS / Kotlin/Native FFI | leave as-is — clearly hypothetical, not load-bearing; optionally one line noting iOS is deferred |
| `doc/dev/plans/guess-meaning-engine-to-core.md` | several `commonMain` mentions | add a "superseded once KMP is dropped: this all just lives in `:core`" note at the top |
| `enrichment/README.md` | whole file is "why not in `:core`'s `commonMain`" | deleted in Step 3; rationale folded into `core/.../llm/README.md` |

## Risks

- **AGP consuming a plain-jar `:core`.** Mitigated: `:fst` already proves
  it. Watch for anything expecting `:core` as an `.aar` (nothing should).
- **Resource loading path.** `LinguisticDataCSV.kt` reads classpath
  resources; the srcDir move must keep them on the runtime classpath for
  `:cli`, `:composeApp`, and a future desktop app. The accuracy suite is
  the check.
- **IDE / run configs.** The `android-studio-run-all-tests` skill's
  `iutools2.*` labels and the module structure — `:core` staying named
  `:core` means most of this is unaffected; re-import the Gradle project
  after Step 2.
- **Another dev / agent mid-flight.** This restructures `:core`'s build.
  Land it in as few commits as possible and tell whoever's working on
  `:core` / FST.
- **Desktop app doesn't exist yet.** Step 2/3 don't create it; they just
  stop blocking it. A `:desktopApp` (Compose Desktop) is separate future
  work.
