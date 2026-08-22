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

## Running multiple agents in parallel (git worktrees)

The devcontainer setup (`.devcontainer/`) supports running several Claude
Code agents side by side, each on its own branch, each in its own
container — as long as each one works in its own
[git worktree](https://git-scm.com/docs/git-worktree) rather than all
sharing one checkout. Container names don't collide, and Claude Code's
config/auth, shell history, and the Gradle/Android SDK caches are shared
across worktrees via fixed-name Docker volumes instead of being duplicated
per worktree.

1. Create a worktree as a sibling folder of the main checkout:
   `git worktree add ../inuktitut-morpohological-analyzer-feature-x feature-x`
2. One-time per machine: uncomment the git-worktree bind mount near the
   bottom of `.devcontainer/devcontainer.json`'s `mounts` — needed because
   a worktree's `.git` is just a text file pointing at an absolute host
   path inside the main repo's `.git`, which isn't visible inside the
   container otherwise (every git command would fail with `fatal: not a
   git repository: (null)`). One mount line covers every worktree.
3. Open the worktree folder in a new VS Code window and **Reopen in
   Container**.
4. (Optional) Give that worktree's `.vscode/settings.json` a different
   `workbench.colorCustomizations` titleBar color so the windows are easy
   to tell apart.
5. Run `claude` in the integrated terminal — it works side by side with
   Claude Code instances running in the other worktrees/containers of this
   project.

To remove a worktree once done: close its VS Code window/container, then
from the main repo run `git worktree remove ../inuktitut-morpohological-analyzer-feature-x`.

## Testing

```bash
./gradlew :cli:test
```

Runs the full accuracy/regression suite, including the Hansard-corpus
accuracy benchmark described in [AGENTS.md](AGENTS.md).

## License

[MIT](LICENSE.md).
