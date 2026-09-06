# Git hooks

Versioned hooks for this repo. They are **not** active until you point git at
this directory:

```sh
git config core.hooksPath hooks
```

(The agent devcontainers do this automatically; a human clone does it once.)

## `pre-commit`

Blocks a commit when a staged file is larger than 5 MiB or its content looks
like private / licensed reference data — the recovered Living Dictionary, the
gov.nu.ca crawl, raw `.bak` / corpus archives. That material lives read-only
under `/shared/ref` in the agent containers and must never enter git history.
See `AGENTS.md` → "Shared reference data (`/shared`)".

Override for a genuine false positive: `git commit --no-verify` (and consider
narrowing the pattern list in the hook, or adding a real build artifact to
`.gitignore`).
