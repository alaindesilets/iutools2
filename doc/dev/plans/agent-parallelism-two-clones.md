# Plan: parallel agents on two independent clones (retire the linked worktree)

## Goal

Move from the current setup — where the devcontainer's `/workspace` is a
**linked `git worktree`** of a repo whose primary checkout lives on Alain's
Mac — to **two independent full clones, each on `main`**, one per agent
(`iutools2-GREEN-agent`, `iutools2-BLUE-agent`).

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
- **`origin` is the only exchange channel.** Every commit is pushed as
  soon as it is made (after a fetch+rebase and a re-run of the gate), so
  there is never unpushed local work another agent needs. No `sibling`
  remotes, no cross-clone cherry-picking — an agent that wants the other's
  work just pulls. `/shared/ref` (below) is for large non-git *data*, not
  for exchanging code.

## Alternative considered and rejected

**Shared `.git` worktrees + permanent color-named local branches**
(`GREEN-agent` / `BLUE-agent`, matching Alain's per-agent VS Code colour
themes), never pushed, folded into `main` locally before each push of
`main`.

Rejected because it keeps the shared-`.git`-across-the-container-boundary
fragility (rebase mtime flakiness, `git worktree prune` footgun from the
Mac, `devcontainer.json` read-only blocking rebase/merge) and needs an
anti-push guard so a stray `git push` can't send an agent branch — all to
avoid a GitHub round-trip that, with the push-every-commit workflow below,
is already the cheap and normal path. Two independent clones remove that
whole class of problem structurally.

A local-path `sibling` remote (each clone fetching directly from the
other's `.git`) was also considered, to hand over *not-yet-pushed* work.
Dropped: with every commit pushed immediately there is nothing unpushed to
hand over, and the rare "I need your half-finished work right now" case is
better solved by the other agent just finishing and pushing it (or, if it
truly can't be pushed, that is a signal the two agents' scopes overlap too
much).

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

Two **fresh** clones, both new; the current primary worktree
(`~/Documents/iutools2`) and linked worktree (`~/Documents/iutools2-Android-UI`)
are deleted once the new pair is verified.

| clone | path (Mac) | VS Code theme |
|---|---|---|
| `iutools2-GREEN-agent` | `~/Documents/iutools2-GREEN-agent` | green |
| `iutools2-BLUE-agent`  | `~/Documents/iutools2-BLUE-agent`  | blue |

Each: own `.git`, `origin` → `github.com/alaindesilets/iutools2`, `main`
checked out and tracking `origin/main`. Each opened in its own
devcontainer. Coordination is **only** through `origin`.

## Steps

The new pair is built and verified **before** anything old is removed, so
the fallback is always "just keep using the current worktree".

### 1. Confirm the current worktree has nothing to lose
- [ ] In `/workspace`: `git status` clean and `git log origin/main..HEAD`
      empty (nothing unpushed). As of 2026-09-06 it is clean at
      `origin/main`.

### 2. Prepare `/shared/ref` on the Mac
- [ ] `mkdir -p ~/iutools-agent-shared/ref`
- [ ] Move the recovered Living Dictionary, the gov.nu.ca crawl, and any
      raw `.bak` / corpus archives under it. This is the read-only,
      Alain-managed area for large data that must never enter git.

### 3. Edit `.devcontainer/devcontainer.json`
It is bind-mounted read-only into the current container, so per the
`project_devcontainer_readonly_mount_gotcha` memory make these edits in a
**temp clone outside any mounted workspace**, commit, push. Do it before
step 4 so the new containers pick up the clean config on their first
build (safe to do after too — the clones just need a rebuild).

- [ ] **Remove the worktree `.git` bind mount** — the line
      `source=${localWorkspaceFolder}/../iutools2/.git,target=…/../iutools2/.git`.
      It exists only so a linked worktree's `.git` pointer file resolves;
      an independent clone has a real local `.git`.
- [ ] Drop the worktree comment block above that mount and the worktree
      rationale in the `--name` runArg comment.
- [ ] **Keep** the read-only `.devcontainer` self-mount
      (`target=/workspace/.devcontainer,…,readonly`) and the fixed-slug
      named-volume mounts (`iutools-mobile-gradle-cache`,
      `-android-sdk`, `-claude-code-config`, …) — the latter give both
      new containers a warm shared cache with no change.
- [ ] **Add** the shared reference-data mount:
      ```jsonc
      "source=${localEnv:HOME}/iutools-agent-shared/ref,target=/shared/ref,type=bind,readonly"
      ```
      `target=/shared/ref`, never under `/workspace`, so a copy into a
      clone is always a deliberate cross-directory `cp`, never an in-place
      `git add`.
- [ ] In `postCreateCommand`, append `&& git config core.hooksPath hooks`
      so `hooks/pre-commit` is active in every container.

### 4. Create the two fresh clones
- [ ] `cd ~/Documents`
- [ ] `git clone git@github.com:alaindesilets/iutools2.git iutools2-GREEN-agent`
- [ ] `git clone git@github.com:alaindesilets/iutools2.git iutools2-BLUE-agent`
- [ ] In each: `git status` → on `main`, clean, tracking `origin/main`;
      `git config core.hooksPath hooks` (redundant with the
      `postCreateCommand`, harmless).
- [ ] Open each in its own devcontainer; set the VS Code colour theme
      (green / blue) so it is obvious which agent is which.

### 5. Verify (the "it works" checklist)
- [ ] Both containers open; `git status` clean on `main`; no
      worktree / `.git`-pointer errors.
- [ ] `git checkout -b tmp && git checkout main` works in each (no branch
      lock).
- [ ] The second clone's first `./gradlew :cli:test` reuses the shared
      cache (no full re-download).
- [ ] `/shared/ref/...` readable in both; a write attempt fails.
- [ ] `git config --get core.hooksPath` → `hooks`; `hooks/pre-commit`
      blocks a fake Living-Dictionary file.
- [ ] Trivial commit in GREEN → `git push` → `git pull --rebase origin
      main` in BLUE picks it up.

### 6. Tear down the old setup (only after step 5 passes)
- [ ] `cd ~/Documents/iutools2 && git worktree remove
      ~/Documents/iutools2-Android-UI` (add `--force` if it complains),
      then `git worktree prune`.
- [ ] Delete `~/Documents/iutools2` and `~/Documents/iutools2-Android-UI`.
- [ ] If Docker recreates an empty `~/Documents/iutools2/.git`, `rm -rf` it.

### 7. Update AGENTS.md
- [x] "Shared reference data (`/shared`)" section added (covers
      `/shared/ref`, the no-commit rule, the `hooks/pre-commit` guard, the
      rights note).
- [x] "## Git History" rewritten: recommended workflow (one amended commit
      per task; fetch+rebase, gate, push) + two-clones parallel model,
      framed as a recommendation; worktree guidance removed; experimental-
      branch exception kept.
- [ ] Once the migration actually happens: delete (or trim to a one-line
      "historical") the `project_workspace_is_linked_git_worktree` memory.

## Per-agent workflow (the steady state)

`origin` is the only exchange channel. **One task = one commit.**

1. Do the work. As it progresses, fold each increment into the *same*
   commit:
   - first change of the task: `git commit`
   - every change after: `git commit --amend --no-edit` (or without
     `--no-edit` to refine the message).
   Never let unpushed commits pile up — several unpushed commits almost
   always means one task, so they should be one amended commit.
2. When the task is done, integrate and ship it:
   ```sh
   git pull --rebase origin main     # replay the task commit on the current tip
   <gate>                            # re-run on the up-to-date base
   git push                          # rejected? -> repeat from pull --rebase
   ```
   Gate = `./gradlew :cli:test` if `:core` changed (Hansard histogram
   unchanged: 673 / 244 / 2 / 0); `:composeApp:compileDebugKotlin` +
   `:composeApp:testDebugUnitTest` if `:composeApp` changed; both if the
   task spans modules.
3. The other agent gets this work with a plain `git pull --rebase origin
   main` — there is nothing unpushed to chase, and no `sibling` remote.
4. Keep the two agents on **disjoint scopes** (e.g. one on `:composeApp`,
   one on `:core` / FST / `data/grammar`) so their task commits rarely
   touch the same files. If cross-agent conflicts are frequent, fix the
   scoping, not the git flow.

## Risks / open points

- **Step 3 is the real unknown.** Confirm exactly which
  `devcontainer.json` paths are worktree-specific before building the new
  containers; a stale worktree `.git` mount is inert while
  `~/Documents/iutools2` still exists but can block a container once it is
  deleted.
- Two `./gradlew` runs sharing `~/.gradle` is fine; two runs of the same
  task in the same project dir is not — a non-issue with separate clones.
- Disk: ~2× the repo plus separate `build/` outputs. Negligible.
- `--amend` only works on the unpushed task commit. Once pushed, it is
  history — start a fresh commit for the next task.
- If feeding two agents is already near capacity, keep this setup as
  plain as written here — do not grow it into a framework.
