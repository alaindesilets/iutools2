# Development plans

This directory holds **planning documents** for iutools2 -- design notes and
step-by-step migration/implementation plans that outlive a single work
session.

They are written to be picked up and acted on by whoever comes next: a human
developer, or a coding agent starting cold with no memory of the discussion
that produced the plan. So each plan should be self-contained -- state the
goal, the rationale, the concrete steps, and how to tell each step is done.

Conventions:

- One file per plan, kebab-case name (`module-architecture-migration.md`).
- Keep a plan updated as it is executed: check off completed steps, record
  deviations, note what is left. A plan that no longer matches reality is
  worse than no plan.
- When a plan is fully done, either delete it or move a short "what shipped"
  summary into the relevant code/tooling doc and remove the plan.
- An investigation or decision that carries forward-looking direction ("we
  paused X, here is what to do when it resumes") is a plan and belongs
  here. Purely historical notes with nothing actionable left can go one
  level up in `doc/dev/`.

Current plans:

- [`module-architecture-migration.md`](module-architecture-migration.md) --
  moving from the current `:core` / `:cli` / `:composeApp` layout to a
  `core` / `apps` / `data` functional split with platform concerns kept in
  KMP source sets.
- [`fst-analyzer-plan.md`](fst-analyzer-plan.md) -- the original milestone
  plan for the HFST-based finite-state morphological analyzer (`data/grammar/fst/`).
- [`gov-nu-ca-crawling-investigation.md`](gov-nu-ca-crawling-investigation.md)
  -- bilingual-sentence-source crawling: bot-protection findings and the
  plan for resuming once it runs from a non-datacenter IP.
- [`reranker-objectives-and-analyzers.md`](reranker-objectives-and-analyzers.md)
  -- decomposition re-ranking: the distinct objectives (reference@1,
  correct@1, R-precision, P@min(5,N)) and why they conflict, the best
  models so far per analyzer (R2L vs FST) × objective, and the case for
  multiple re-rankers. Strategic layer above
  `data/grammar/fst/reranker-experiment.md`.
- [`offline-dictionary.md`](offline-dictionary.md) -- an offline LLM pass
  over the top ~10k Hansard word forms: pre-computed Guess Meaning plus a
  per-decomposition annotation of whether it matches a real bilingual
  corpus usage, feeding the Morpheme Dictionary "good example words" gate.
