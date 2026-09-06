# Plan: parallel agents on two independent clones (retire the linked worktree)

## Goal

Move from the current setup — where the devcontainer's `/workspace` is a
**linked `git worktree`** of a repo whose primary checkout lives on Alain's
Mac — to **two independent full clones, each on `main`**, one per agent.

Target properties:

- Two agents can both work on `main` (a linked worktree cannot — a branch
  is checked out in at most one worktree).
- Each agent can `git checkout` / branch / rebase freely, with no
  cross-worktree branch lock.
- **No feature branches by default.** The remote carries only `main`.
  Alain and Benoît already keep `main` clean and green; this preserves
  that. (Short-lived branch only for the AGENTS.md "genuinely
  experimental" exception, deleted the moment it merges or is abandoned.)
- No shared `.git` across the container boundary, so: no `git worktree
  prune` footgun, no bind-mount mtime flakiness during rebase, and the
  read-only `.devcontainer/devcontainer.json` mount stops blocking
  rebase/merge.
- Agents can still hand work to each other **without a GitHub
  round-trip**: each clone adds the other as a local-path remote
  (`sibling`), so `git fetch sibling && git merge sibling/main` (or
  `cherry-pick`) is all-local. One `git fetch` is the only tax versus a
  shared object store.

## Alternative considered and rejected

**Shared `.git` worktrees + permanent color-named local branches**
(`GREEN-agent` / `BLUE-agent`, matching Alain's per-agent VS Code colour
themes), never pushed, folded into `main` locally before each push of
`main`.

Rejected because it keeps the shared-`.git`-across-the-container-boundary
fragility (rebase mtime flakiness, `git worktree prune` footgun from the
Mac, `devcontainer.json` read-only blocking rebase/merge) and needs an
anti-push guard so a stray `git push` can't send an agent branch — all
for a marginal gain (instant cross-agent visibility vs. one `git fetch`).
Two independent clones remove that whole class of problem structurally,
and the local `sibling` remote covers the exchange-without-GitHub need.

## Why the current setup hurts

`/workspace/.git` is a file:
`gitdir: /Users/alaindesilets/Documents/iutools2/.git/worktrees/iutools2-Android-UI`.

- `~/Documents/iutools2` (Mac) holds branch `main` as the **primary
  worktree**; it is not mounted into the container.
- `~/Documents/iutools2-Android-UI` is the **linked worktree**,
  bind-mounted to `/workspace`.

Symptoms hit in practice:

- `git checkout main` in `/workspace` →
  `fatal: 'main' is already used by worktree at '/Users/.../iutools2'`.
- `git worktree list` flags `/workspace`'s worktree `prunable` because
  git-in-container can't see its registered host path (remapped to
  `/workspace`); `git worktree prune` from the Mac would deregister it.
- `git rebase` can abort with a spurious "local changes would be
  overwritten" on a clean tree (bind-mount mtime).
- `.devcontainer/devcontainer.json` is a read-only mount; a rebase/merge
  that rewrites it fails (see the `project_devcontainer_readonly_mount_gotcha`
  memory).

At ~2 agents these are pure friction. Linked worktrees with a shared
`.git` make sense only when every worktree is in the same
filesystem/container and you want local `git merge <sha>` integration —
not when one worktree straddles the container boundary.

## Target layout

| clone | path (Mac) | role |
|---|---|---|
| A | `~/Documents/iutools2` | agent 1 — already a normal clone on `main`; nothing to convert |
| B | `~/Documents/iutools2-agent2` | agent 2 — fresh `git clone` |

Each: own `.git`, `origin` → `github.com/alaindesilets/iutools2`, `main`
checked out and tracking `origin/main`. Each opened in its own
devcontainer (or A in a container and B on the Mac, etc.). Coordination is
**only** through `origin`.

## Steps

### 1. Drain the current linked worktree
- [ ] In the container: `git status` in `/workspace`. Commit + push, or
      stash and record, anything pending. Note the current HEAD sha.
      (As of 2026-09-06 it is clean at `origin/main`.)
- [ ] Stop the devcontainer that mounts `~/Documents/iutools2-Android-UI`.

### 2. Remove the linked worktree (from the Mac)
- [ ] `cd ~/Documents/iutools2`
- [ ] `git worktree remove ~/Documents/iutools2-Android-UI`
      (add `--force` if it complains about the detached HEAD / mount).
- [ ] `git worktree prune`
- [ ] `git worktree list` → shows **only** `~/Documents/iutools2`.
- Done when: no `prunable` entries, the `-Android-UI` directory is gone.

### 3. Create clone B
- [ ] `cd ~/Documents`
- [ ] `git clone git@github.com:alaindesilets/iutools2.git iutools2-agent2`
- [ ] `cd iutools2-agent2 && git status` → on `main`, clean, tracking
      `origin/main`.
- (Optional: rename `~/Documents/iutools2` → `iutools2-agent1` for
  symmetry. Not required.)

### 3b. Wire the `sibling` remote (local exchange without GitHub)
- [ ] In clone A: `git remote add sibling ../iutools2-agent2` (or the
      absolute path).
- [ ] In clone B: `git remote add sibling ../iutools2` (adjust for any
      rename).
- [ ] `git -c fetch.parallel=0 fetch sibling` in each → succeeds.
- Done when: from either clone, `git log sibling/main` shows the other
  clone's `main` after a `git fetch sibling`.
- Note: if a clone runs inside a container, the sibling path must be the
  path **as seen from inside that container** — mount the other clone (or
  at least its `.git`) into the container, or keep the sibling exchange to
  whichever side (Mac) can see both.

### 4. Edit `.devcontainer/devcontainer.json` (do this carefully — RO-mount edit)
All of these are `devcontainer.json` changes → per the
`project_devcontainer_readonly_mount_gotcha` memory, make them in a **temp
clone outside any mounted workspace**, commit, push; both clones pick them
up on next `git pull`.

- [ ] **Remove the worktree `.git` bind mount** — the line
      `source=${localWorkspaceFolder}/../iutools2/.git,target=…/../iutools2/.git`.
      It exists only so a linked worktree's `.git` pointer file resolves;
      an independent clone has a real local `.git` and does not need it.
- [ ] **Drop the worktree comment block** above that mount (the
      "Running multiple agents in parallel via git worktrees …" paragraph)
      and the worktree rationale in the `--name` runArg comment.
- [ ] Keep the read-only `.devcontainer` self-mount
      (`target=/workspace/.devcontainer,…,readonly`) — still wanted.
- [ ] Keep the fixed-slug named-volume mounts (5a).
- [ ] Add the `/shared/ref` mount (5b) and, in `postCreateCommand`,
      `git config core.hooksPath hooks` (5c).
- Done when: opening clone B in its container shows clone B's files at
  `/workspace`, `git rev-parse --show-toplevel` resolves inside clone B,
  and `git -C /workspace status` works with no worktree/`.git`-pointer
  errors.

### 5. Shared mounts

**5a. Build caches (already mostly in place).** `devcontainer.json` already
mounts fixed-slug named volumes (`iutools-mobile-gradle-cache`,
`iutools-mobile-android-sdk`, `iutools-mobile-claude-code-config`, …) so
every container of this project shares them regardless of folder name —
keep that. Nothing to change unless a cache needs to be per-agent.
- Gradle cache is safe for concurrent processes (the daemon is
  per-project-dir, so no clash).
- Done when: clone B's first `./gradlew :cli:test` reuses the cache
  instead of re-downloading.

**5b. Shared read-only reference data.** Add one bind mount to **both**
clones' `devcontainer.json`:
```jsonc
"source=${localEnv:HOME}/iutools-agent-shared/ref,target=/shared/ref,type=bind,readonly"
```
- [ ] `mkdir -p ~/iutools-agent-shared/ref` on the Mac; move the recovered
      Living Dictionary, the gov.nu.ca crawl, and any raw `.bak` / corpus
      archives under it.
- [ ] `target=/shared/ref`, **never under `/workspace`**, so a copy into a
      clone is always a deliberate cross-directory `cp`, never an in-place
      `git add`.
- [ ] Read-only for agents: no write races, and an agent cannot clobber or
      delete the masters. There is deliberately **no** agent-writable
      shared dir — to pass a non-git file between agents, Alain places it
      on `/shared/ref`.
- Done when: both containers can read `/shared/ref/...` and neither can
  write there.

**5c. Enable the commit guard.** In each clone (and via each
`devcontainer.json` `postCreateCommand`):
```sh
git config core.hooksPath hooks
```
- [ ] Confirm `hooks/pre-commit` blocks a staged file that fingerprints as
      Living Dictionary / gov.nu.ca data or exceeds 5 MiB.
- `.gitignore` already lists `/shared/`, `**/living-dictionary-recovery/`,
  `**/gov-nu-ca-crawl/`, `*.bak`. See AGENTS.md "Shared reference data
  (`/shared`)".

### 6. Update AGENTS.md
- [x] "Shared reference data (`/shared`)" section added (covers `/shared/ref`,
      the no-commit rule, the `hooks/pre-commit` guard, and the rights note).
- [ ] Rewrite the "Running a second agent in parallel (git worktrees)"
      subsection under "## Git History": replace the
      `git worktree add --detach` guidance with:
      - two independent clones, both on `main`;
      - integrate via `origin` (`git pull --rebase` / `git push`), or the
        local `sibling` remote for exchange without GitHub;
      - assign the agents **disjoint scopes** (e.g. one on `:composeApp`,
        one on `:core` / FST / `data/grammar`) so commits rarely collide;
      - keep the existing exception clause for genuinely experimental
        work on a named branch, deleted on merge/abandon.
- [ ] Delete the `project_workspace_is_linked_git_worktree` memory once
      this ships (or trim it to "historical: we used to run a linked
      worktree").

### 7. Per-agent workflow (the steady state)
Not a migration step — the routine each agent follows afterwards:

1. Start of a work unit: `git pull --rebase origin main`. To pick up the
   other agent's not-yet-pushed work instead, `git fetch sibling &&
   git rebase sibling/main` (all local).
2. Before every push, run the gate for what changed:
   - `:core` touched → `./gradlew :cli:test` (Hansard histogram must be
     unchanged: 673 / 244 / 2 / 0);
   - `:composeApp` touched → `./gradlew :composeApp:compileDebugKotlin`
     + `:composeApp:testDebugUnitTest`;
   - both if the change spans modules.
3. `git push`. On non-fast-forward rejection: `git pull --rebase origin
   main`, re-run the gate, `git push`. Repeat.
4. Small, frequent commits → tiny divergence windows → few conflicts.
5. `git fetch sibling` is the only way to see the other agent's local
   commits — there is no shared object store, so a forgotten fetch just
   means working against a slightly stale view of their branch, never
   corruption.

## Risks / open points

- **Step 4 is the real unknown.** If `devcontainer.json` hard-codes a
  host path, resolve that before building clone B's container.
- Two `./gradlew` runs sharing `~/.gradle` is fine; two runs of the same
  task in the same project dir is not — a non-issue with separate clones.
- Disk: ~2× the repo plus separate `build/` outputs. Negligible.
- If feeding two agents is already near capacity, keep this setup as
  plain as written here — do not grow it into a framework.
