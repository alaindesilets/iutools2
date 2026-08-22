# iutools-mobile

A port of some of the tools and libraries of the [iutools](https://github.com/iutools/iutools) project. These are tools to help learners, speakers and writers of Inuktitut, the language of the Inuit people of Canada.

As of this writing, we have ported:

- The morphologcal analyzer, which decomposes an inuktitut word into its constituents (morphemes)
- Lookup in several dictionaries (Spalding and Tusaalanga)

We are also working on porting look up on:

- The Nunavut Hansard (parallel Inuktitut-English corpus)
- Bilingual web pages on the Government of Nunavut sites

We are also working on developing a brand new AI powered feature, which can venture guesses as to the meaning of a word, when no dictionary entries are found.

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
