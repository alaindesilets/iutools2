# iutools-morph-kt

A Kotlin Multiplatform port of the morphological analyzer core from
[iutools](https://github.com/iutools/iutools) (Java), which decomposes
Inuktitut words into their constituent morphemes:

```
atuagaq → {atua:atuaq/1v}{gaq:gaq/1vn}
```

Only the analyzer itself was ported — the original project's spellchecker,
concordancer, dictionary/Elasticsearch, and web/servlet layers are out of
scope here. The goal is a real mobile app (Android/iOS), not just a
library: a command-line tool and a Compose UI both exist as ways of
exercising the same shared analyzer core.

For architecture, module layout, coding guidelines, and everything else
relevant to working on this codebase (including for AI coding agents), see
[AGENTS.md](AGENTS.md).

## Building and running

Command-line, from the repo root:

```bash
./gradlew :cli:run --args="--word atuagaq"
```

Android app: see [build-android-apk.sh](build-android-apk.sh) (build the
APK) and [run-android-app.sh](run-android-app.sh) (build, launch an
emulator, install, and run) — both meant to be run from a macOS terminal,
not inside a Linux devcontainer (Android's `aapt2` has no Linux/ARM64
build).

## Testing

```bash
./gradlew :cli:test
```

Runs the full accuracy/regression suite, including the Hansard-corpus
accuracy benchmark described in [AGENTS.md](AGENTS.md).

## License

[MIT](LICENSE.md).
