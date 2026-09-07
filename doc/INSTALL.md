# segment_iu — Inuktitut morphological analyzer

Version 1.0 (Mac, Intel/x64) — Kotlin port of the morphological core of
[iutools](https://github.com/iutools/iutools).

## Requirements

**None.** The Java runtime is bundled in the package — there's nothing to
install.

## Installation

1. Unzip the `.zip` file you received (double-click in Finder, or
   `unzip segment_iu-mac-x64.zip`).
2. You'll get a file called `segment_iu.app`. Move it wherever you like
   (e.g. into `Applications/`, or just onto your Desktop).

## First launch — bypassing Gatekeeper

By default, macOS blocks applications that don't come from the App Store or
from a developer registered with Apple. Since this package isn't signed,
you need to authorize it manually, **once**:

1. In Finder, **Ctrl+click** (or right-click) on `segment_iu.app`.
2. Choose **Open** from the context menu.
3. A warning dialog appears — click **Open** to confirm.

If macOS refuses even that option (a message saying it "cannot be opened"
with no Open button), go to **System Settings → Privacy & Security**, and
look near the bottom for a message mentioning `segment_iu` with an
**"Open Anyway"** button.

Once authorized the first time, subsequent launches (including from the
command line, see below) will work without any warning.

## Usage

`segment_iu` is a command-line tool — it's meant to be run from the
Terminal, not double-clicked.

Open the **Terminal** app (Applications → Utilities → Terminal), then run:

```
/path/to/segment_iu.app/Contents/MacOS/segment_iu --word wordToAnalyze
```

For example, if `segment_iu.app` is on your Desktop:

```
~/Desktop/segment_iu.app/Contents/MacOS/segment_iu --word atuagaq
```

Expected output:
```
=== atuagaq ===
  {atua:atuaq/1v}{gaq:gaq/1vn}
  {atu:atuq/2v}{a:a/1vv}{gaq:gaq/1vn}
  {atu:atuq/1v}{a:a/1vv}{gaq:gaq/1vn}
```

Every possible decomposition is shown, with the first one usually being
the most likely.

### Other options

- `--lenient-decomps` — "lenient" analysis (allows a consonant after a
  final vowel); off by default.
- `--timeout-secs N` — max seconds allowed per word before giving up;
  defaults to 10 seconds.
- `--interactive` — prompts for words one at a time (type `q` to quit)
  instead of specifying `--word`:
  ```
  ~/Desktop/segment_iu.app/Contents/MacOS/segment_iu --interactive
  ```
- `--pipeline` — reads one word per line from standard input and prints
  one JSON result per line (useful for scripting):
  ```
  echo "atuagaq" | ~/Desktop/segment_iu.app/Contents/MacOS/segment_iu --pipeline
  ```

`--word`, `--interactive`, and `--pipeline` are mutually exclusive — use
only one at a time.

### Tip: a shorter alias

To avoid retyping the full path every time, add this line to the end of
your `~/.zshrc` (or `~/.bash_profile` if you use bash):

```
alias segment_iu="$HOME/Desktop/segment_iu.app/Contents/MacOS/segment_iu"
```

(adjust the path if you placed `segment_iu.app` somewhere other than the
Desktop), then open a new terminal. After that, you can simply type:

```
segment_iu --word atuagaq
```

## About

This program reproduces the behavior of iutools' morphological analyzer
(`MorphologicalAnalyzer_R2L`) — ported from Java to Kotlin, then validated
against the original accuracy test suite (Hansard corpus, 919 evaluated
words): the results obtained are identical to those of the original Java
analyzer.

License: see `LICENSE.md` included in this package (MIT, Copyright His
Majesty the King in Right of Canada and others).

Questions or bug reports: contact Alain Desilets.

---

## Note for maintainers (rebuilding the package)

This file is also included inside the delivered zip
(`dist/segment_iu-mac-x64-v1.0.zip`), alongside `segment_iu.app` and
`LICENSE.md`. If the code changes and the package needs to be rebuilt:

1. `./gradlew installDist` (regenerates `apps/cli/build/install/cli/lib/*.jar`
   with up-to-date bytecode — **don't skip this step**, otherwise `jpackage`
   will use a stale jar).
2. Use `jpackage` (from an **x86_64** JDK — see below, not the project's
   default arm64 JDK) to produce `segment_iu.app`:
   ```
   jpackage --type app-image --name segment_iu \
     --input apps/cli/build/install/cli/lib \
     --main-jar cli.jar \
     --main-class org.iutools.morph.cli.MainKt \
     --app-version 1.0 --vendor "iutools" \
     --dest <output-folder>
   ```
3. An x86_64 JDK for macOS (e.g. Eclipse Temurin 21) is needed to produce
   an x64 binary from an Apple Silicon machine — Rosetta 2 runs it
   transparently. This JDK doesn't need to be installed, just downloaded
   and extracted somewhere.
4. Gather `segment_iu.app`, `LICENSE.md`, and this file into one folder,
   then zip it with `ditto -c -k --sequesterRsrc --keepParent <folder> <zip>`
   (correctly preserves macOS `.app` bundles, unlike `zip -r`).
