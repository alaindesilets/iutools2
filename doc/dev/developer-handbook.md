
# Developer Handbook

Practices that everyone working in this repo follows — human devs and
coding agents alike. `AGENTS.md` covers what is specific to agents; this
file is the shared part: how we write code, document it, use git, test,
and handle the project's data.

This handbook has three parts: how we write code, how we ship it, and how
we handle data.

## Separate data, business logic, and presentation

As much as possible, keep business logic independent of the visual
appearance of the page or dialog. And keep as much of the business logic in :core.

In this project specifically: `:core`
must never depend on Compose or any UI type — `:composeApp` calls into
`:core`, never the reverse.

## Porting from the old iutools Java project

iutools2 started as a port of the morphological analyzer (aka Uqailaut or R2L) of the old, java-based iutools project:

  https://github.com/iutools/iutools-java

But it has evolved into a port of many more of the original functionality.

When adding a new functionality to iutools2, first check in the old java project to see if has been implemented there. If so, you might consider porting the code from Java to Kotlin, or taking inspiration from it.

Before porting or keeping any code from the original Java project, confirm
it's actually reachable from `decomposeWord()` — grep first, don't assume.
This project's history has repeatedly found large chunks of faithfully-
portable-but-dead code (unused analyzer variants, display/debug-only
formatting methods, etc.) this way; when in doubt, prune rather than port,
and say so in a comment at the point of pruning.

Note that some of the code in the original java-based iutools project depends on the NRC java-utils project:

  https://github.com/nrc-cnrc/java-utils

So you may need to port parts of that project too. But only the parts reachable from whatever piece of the original iutools project you are currently porting.

## Documenting the code

There are several ways to document things in this project.

- Comments
- Agent's own memory
- Planning documents
- README files

Below are details about the proper use of each approach.

### Writing style

This is the general rule for every piece of dev-facing prose in the
project — comments, README files, planning documents. "Comments versus
proper naming" below is the comment-specific version of it.

Whether you are writing a comment or a .md file, start with a high level point of view that focuses on the WHAT and WHY, not on the HOW.

For example, if you are writing a comment that explains what a class is about, focus on WHY that class exists (and provide an idea of the context in which it will be used), and WHAT it does. In that overview, try to avoid technical jargon. The overview should be understandable by anyone who understands the business domain, but may not be intimately familiar with the gory details of that particular class. 

Of course, if there are some important technical aspects about HOW the class achieves that, then by all means, add those in. But put those details later — after the overview, and possibly down next to the methods they actually bear on.

But even there, before writing long winded jargon heavy prose, ask yourself if you can make those details self-evident by renaming a file, class, method, variable, etc., so that the name conveys those details.


### Comments versus proper naming

- Use comments sparingly.
- If you feel the need to write a comment to explain the purpose of a
  method, function, attribute, variable, see if changing its name might
  not achieve the same clarity.
- If you feel the need to write a comment to explain a section of a
  function/method, see if you can achieve the same clarity by turning that
  section into a function/method, and giving it a clear name.
- Appropriate use of comments:
  - Put a comment at the top of each package, file, class (compulsory).
  - In that top-of-file/class/function comment, apply the **Writing style**
    rule above: WHAT and WHY first, in plain language; HOW, rationale and
    edge cases after — not first.
  - A litmus for a class comment: could someone who has never seen this
    feature read the comment alone and correctly say what the class is
    for? This FAILS (assumes you know the feature, reads as an aphorism):
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

### README.md files in directories

Each directory in this project may contain a README.md that describe the purpose and structure of that directory (and its descendants).

This type of documentation is meant to be more permanent than the docs found in doc/dev/plans/. But if the directory is in a state of flux, the README may explain this and even refer to a planning document, while the directory is being modified.

Keep it high-level: a README states the *intent* of the directory. It is
not an inventory of the files inside it, nor a per-file rationale — that
belongs in each file's own top comment, or in a planning document. 

### Agent Memory

Coding agents may use their personal memory to remember details about what they are currently working on and where they are at.

Use this for the kinds of details that do not need to be shared with other agents or human devs.

### Planning documents

The doc/dev/plans/ directory contains files that describe plans for tasks, whether they be future ones, or tasks that are undergoing.

The documents in that directory are not meant to be permanent. They are meant to communicate plans and their current status, with other agents and human devs.

   

## Internationalisation

The UI ships in English and French (see `doc/dev/system-architecture.md`
for how the language is chosen at runtime). In the code, every user-facing
string is written in English, with a French translation alongside it.

When you add or change a user-facing string, add or update its French
translation in the same change, and check that the French version actually
shows when the app is running in French.

If you're not comfortable writing the French yourself, a machine
translation (any AI tool) is a fine starting point — note it in the change
so a French speaker can check it over later.

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
  - Commit your changes on main. Avoid using branches, except for work that is highly experimental and likely to be abandoned. Routine use of branches tends to result in orphan branches, where nobody remembers what they were about.
  - Before you push your changes, make sure to fetch, rebase and test (FRT), to make sure pulled changes haven't borken anything in your own design.
- **Multiple agents in parallel.** Again, this is a recommaned workflow. Each dev may prefer a different one. We recommend that each agent runs in its **own independent clone**,
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

- **Every test stays green — always, not just the suite you happened to
  touch.** Run the tests for what you changed before committing
  (`./gradlew test`, or the affected module's task), and don't commit on a
  red suite. A test that was already failing before your change is a
  finding to report, not licence to leave it red.
- **Pay particular attention to the morphological analyzer.** Any change
  that reaches `:core` must also pass the Hansard accuracy gate: run
  `./gradlew :cli:test` and confirm no word has regressed against its
  committed per-word snapshot (`MorphAnalCurrentExpectations_Hansard.kt`;
  the FST has its own, `MorphAnalCurrentExpectations_FST_Hansard.kt`).
  Reported as 4 metrics (as of this writing, R2L: Recall 100.0%, Precision
  100.0% -- both exactly 100% by construction, since `all_correct_decomps`
  is itself generated by running R2L -- reference decomp present anywhere
  917/919 (99.8%), reference decomp in top-1 673/919 (73.2%), out of 919
  "fair" words). Any word regressing is a real behavioral change and must
  be called out explicitly, not silently absorbed (an *improvement* is
  fine and doesn't fail the test -- see
  `MorphologicalAnalyzer__AccuracyTest.kt`'s own docs for why).
- Write automated or semi-automated tests for every new behavior you code.
  When you fix a bug, start by writing a test that fails (because of that
  bug), then fix the bug. That way we're sure the bug won't reappear.
- The human's role shifts from sole author of the code and tests to
  curator of what the AI produces. Whenever you create or modify tests,
  ask the human to scrutinize them carefully.

The split between what an AI agent runs and what a human must run on a real
device is agent-specific and lives in `AGENTS.md`.

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


## Private data on `/shared`

Some material the project needs is large, private, or licensed and must
**never** enter git: the recovered Nunavut Living Dictionary, the
gov.nu.ca crawl, raw `.bak` / corpus archives. It lives on the host,
outside every clone, and is mounted **read-only** into the dev container
at `/shared/ref`. Alain maintains it from the host side; anything running
inside a container only reads it.

- **Never `git add` anything copied out of `/shared`.** The
  `hooks/pre-commit` guard (enable with `git config core.hooksPath hooks`;
  the dev container does this automatically) rejects staged files that are
  over 5 MiB or whose content fingerprints as one of these datasets, and
  `.gitignore` covers the obvious paths. `git commit --no-verify` bypasses
  the hook — don't, unless you have confirmed it is a false positive.
- `/shared` is read-only from inside a container — there is **no shared
  writable space** by design. To get a non-git file to another clone, ask
  Alain to place it on `/shared`, or exchange it through `git`.
- Rights: most of the recovered Living Dictionary is drawn from
  copyrighted third-party dictionaries (only the Schneider subset is
  licensed). Treat it as *look-but-don't-incorporate* — it may inform your
  own judgement, but its content does not go into iutools2 code, data, or
  prompts. See `doc/dev/plans/offline-dictionary-generation.md` → "Rights".
