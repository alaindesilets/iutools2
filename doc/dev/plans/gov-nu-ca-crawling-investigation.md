# gov.nu.ca as a bilingual sentence source — bot-protection investigation and pause decision

## Context

The concordancer investigation (see `AGENTS.md`'s "out of scope" note, and
step 20/Phase 5 of `doc/spike-llm-local-iutools-mobile.md`) identified
gov.nu.ca as a supplementary source of bilingual Inuktitut-English sentence
pairs for the Guess Meaning feature, alongside the Hansard corpus and
Tusaalanga glossary. The plan: crawl the site's own sitemap (which exposes
structured translation-set URLs per page, no alignment heuristics needed),
extract sentence-level text, and either build a local index or feed page
text to Claude live per lookup.

Before building that crawler, the site's bot protection (Cloudflare) needed
to be understood — this document records what was found, and the decision
to pause pending direct contact with the Government of Nunavut.

## Bot-protection findings

gov.nu.ca is behind Cloudflare, with two distinct protection layers:

**1. The site's own search feature is disallowed and gated regardless of
client.** `robots.txt` explicitly disallows `/search/` for all agents, and
in testing it triggers a Cloudflare challenge even for a real, human-driven
Chrome session from a residential IP. This path is a dead end by design,
not a technical obstacle to work around — confirmed early and not
revisited.

**2. Ordinary content pages are gated by a Cloudflare Managed Challenge**,
which requires executing JavaScript to compute and submit a token before
the real page is served. Investigation findings, in the order they were
established (an earlier "it's IP reputation" hypothesis was tested and
superseded — kept here for the record since it shaped early reasoning):

- `curl` (no JS engine) always receives the challenge page, regardless of
  source IP (tested both from a devcontainer's datacenter IP and Alain's
  own residential IP). This is expected and not a useful signal either way
  — a Managed Challenge cannot be solved by any client without a JS engine.
- A real, human-driven Chrome browser, from a residential IP, loads
  ordinary content pages with no challenge at all.
- A Playwright-driven **headed** Chromium (real window, same residential
  IP) also loads pages cleanly — matching real Chrome.
- The same script with `--headless` lands on the Managed Challenge, which
  does not auto-resolve even after a 20s wait — same machine, same IP,
  only the headless flag differs. This isolates the actual discriminator:
  **headless Chromium's fingerprint specifically** (e.g. `navigator.webdriver`,
  WebGL/Canvas renderer differences), not general JS-vs-no-JS capability,
  and not IP reputation on its own — both headed and headless execute JS
  equally, only one passes.

A small diagnostic tool, `tools/fetch_gov_nu_page.py`, was written to run
these comparisons repeatably from outside any container (Playwright cannot
even be installed inside this project's devcontainer today — its browser
download host isn't in the sandbox's network allowlist, and adding it
requires a devcontainer rebuild, not just a config edit). It supports
`--headless` (currently fails) and `--offscreen` (headed, but positioned
away from the visible screen — a cheap way to run unattended without
fighting the headless-detection issue; macOS's window manager was found to
clamp windows back on-screen regardless of the requested position, so this
isn't fully solved for macOS — the clean fix, if the real crawler ends up
running on Linux, is Xvfb: a genuinely headed Chromium rendering to an
invisible virtual display, never a real screen).

**Open, untested risk**: none of the above addresses request-volume/rate
detection — Cloudflare's bot management also scores the *pattern* of many
requests from one source over time, independent of any single request's
fingerprint. At the current sitemap-size estimate (roughly 19,000–20,000
multi-language URL groups), even a polite multi-second delay between
requests would take many hours to a few days for a full crawl. This was
never tested at scale. If it becomes a blocker, mitigations to try (not yet
implemented): reuse one browser session for the whole crawl so Cloudflare's
`cf_clearance` cookie carries across requests instead of re-triggering
evaluation each time, randomized/jittered delays, and testing at small
scale before committing to a full crawl.

## robots.txt findings

Alain retrieved `gov.nu.ca/robots.txt` directly (not fetchable from this
sandbox). Relevant points:

- No `Crawl-delay` directive — nothing to mechanically follow for pacing.
- `/search/` is disallowed for all agents, confirming the empirical finding
  above.
- A `Content-Signal` declaration (Cloudflare's content-licensing standard)
  for `User-agent: *`:
  ```
  Content-Signal: search=yes, ai-train=no, use=reference
  ```
  - `search=yes` — building a search index and returning short excerpts is
    explicitly permitted. This covers the "local index of bilingual
    sentence pairs, shown as short excerpts" shape of the plan.
  - `ai-train=no` — explicitly forbids using the content to train or
    fine-tune AI models. Not a concern here; nothing in this project does
    that.
  - **`ai-input` is not specified at all.** Per the file's own stated
    semantics, an unset signal means neither granted nor restricted. This
    is exactly what Guess Meaning's design does — feed fetched bilingual
    page text to Claude live, in real time, to help it infer a word's
    meaning (retrieval-augmented, not search-results-to-a-user). It isn't
    "search" in the strict sense the file defines, so `search=yes` doesn't
    cleanly cover it.
  - `ClaudeBot` (Anthropic's own training-data crawler) is separately,
    explicitly disallowed in the same file. Doesn't technically apply to
    this project's own crawler (different user-agent, not fetching for
    training), but signals the site operator's general posture on
    AI-related crawling.

## Decision: paused

The Guess Meaning use of gov.nu.ca content is unambiguously an "ai-input"
use, which the site's own robots.txt leaves genuinely unaddressed rather
than granted. Separately, and more decisively: **the Government of Nunavut
is a potential partner for this project.**

Given that, working around Cloudflare's protection to feed their content to
an AI model, in a use case their own policy signal leaves undecided, is a
relational risk, not just a technical or legal one — discovering this after
the fact could sour a relationship that might otherwise become a real
asset. The reverse is also true: a direct ask ("we're building an Inuktitut
morphological analysis app, we'd like to use excerpts of your bilingual
pages for a word-meaning feature — are you comfortable with that?") could
turn into a better-structured data-access arrangement than scraping ever
would, or the start of an actual partnership conversation.

**Decided (2026-08-19)**: pause the crawler/live-fetch work. Before
resuming, reach out to the Government of Nunavut directly about this use.
Whatever access method they agree to (if any) should replace this
document's rate-limiting/headless-detection workarounds, not sit alongside
them.

## If/when this resumes

1. Revisit based on the outcome of contacting the Government of Nunavut —
   their answer determines the actual access method, which may make some
   or all of the bot-protection workarounds above moot.
2. If proceeding with a scraped/crawled approach is still the outcome: the
   sitemap-driven plan (structured translation-set URLs per page, no
   language-detection heuristics needed) and the headed-Playwright approach
   above are validated and ready to build on.
3. `AGENTS.md`'s note that the concordancer is "explicitly out of scope"
   still accurately reflects the codebase — nothing has been implemented,
   only investigated.
