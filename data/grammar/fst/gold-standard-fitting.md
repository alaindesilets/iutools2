# Where the FST prototype is fitted to the gold standard

`lexicon.lexc` and `phonology.xfscript` were grown **one gold-standard word
at a time** (`doc/dev/plans/fst-analyzer-plan.md`'s stated method). A side effect is
that a number of hand-authored lexc lines exist *because a specific gold
word needed them*, not because the CSV linguistic data
(`core/.../dataCSV/`) says so. This file inventories those, so the
prototype's gold-standard numbers can be read with the right caveat.

Benoit's analyzer does **not** work this way: it has no per-word patches —
every decomposition comes from the CSV data plus general morphographemic
rules. Anywhere this prototype substitutes a hand-picked list for one of
Benoit's general rules, its gold coverage reads higher than a faithful
CSV-plus-rules port would.

> **Provenance.** These entries accumulated across the FST prototype's
> development history (see `git log data/grammar/fst/`). Most carry a comment that
> states plainly how they were added ("confirmed via the real Java
> analyzer's own output before wiring", "hand-added rather than read from
> the CSV", "needed for _word_"). This file collects them; it does not
> accuse the comments of hiding anything.
>
> A precise per-entry "CSV-backed vs. gold-observed" classification of all
> ~212 remaining hand entries is still **pending** (tracked as audit item 1
> alongside porting the general cluster rule and deciding the dedup's
> fate). The categories and counts below are the current best estimate.

## Category A — spelling variants not in the CSV `variant` column

The largest category. Inuktitut Roman orthography has systematic
consonant-cluster alternations (`gl↔ll`, `ngn↔nn`, `kp↔pp`, `ks↔ts/ss`,
`gv↔vv`, `kk↔k` …). **Benoit applies these as general rules to every root
from the CSV.** This prototype has no such rule in `phonology.xfscript`;
instead ~40–70 individual variant spellings were wired by hand after
observing which gold surface forms failed, each "confirmed via the real
Java analyzer's own output".

Verified **not** present in the CSV `variant` column (spot checks): `iglu`
(→`illu`), `tugli` (→`tulli`), `iglaq` (→`illaq`), `kanangnaq`
(→`kanannaq`; its actual CSV variant is the unrelated `kanangniq`),
`uangnaq` (→`uannaq`), `nangminiq` (→`namminiq`), `nattilik` (→`natsilik`),
`pangnirtuuq` (→`panniqtuuq`), `ublaaq` (→`ullaaq`), `unnuksaq`
(→`unnusaq`), `qaplunaaq` (not in the roots CSV at all; →`qallunaa`).

Same pattern, not individually CSV-checked yet: `arraagu`, `nunatsiaq`,
`unipkaaq`, `iqaluktuutsiaq`, `miliat`, `tausat`, `ubluq`, `miksa`
(→`missa`/`mitsa`), verbs `allak` (→`aglat`), `iksiva` (→`issiva`/`itsiva`),
`niqtuq` (→`niqsu`/`nirtu`/`niqtu`), `naamak` (→`naammap`), `avik` (→`avit`),
`makpiq` (→`mappiq`), the demonstrative-pronoun cluster (`taamna`
→`taanna`/`tanna`, `taikkua`→`taikua`, `taingna`→`tainna`, `taiksu`
→`taissu`/`taitsu`, `taakkua`→`takkua`, `taqqapku`→`taqqakku`,
`taapsu`→`taassu`/`taatsu`, `tamaksu`→`tamassu`, `manna`, `igvit`→`ivvit`),
adverbs (`asuilaak`→`asuillaak`, `aksualuk`→`atsualuk`,
`immaqaa`→`immagaa`/`immaqa`, `ikpaksaq`→`ippaksaq`/`ippassaq`/`ippatsaq`,
`qanuq`→`qanu`, `suurlu`→`surlu`, `uattiaruk`→`uattiaru`, `tagva`→`tavva`,
`ubva`→`uvva`, `tama`→`tam`), conjunctions (`uvvalu`→`uvalu`,
`uvvaluunniit`→`uvvaluunni`).

**Impact:** removing all hand root lexicons (the experiment behind the
current dedup) drops `--fair` coverage from **920/922 to 858/922** — 62
gold words rely on this layer.

**At least one is not even matching Benoit:** the FST accepts `illaq`
(via `iglaq+1v:illaq`); Benoit returns "No decompositions found" for that
bare surface. So the hand layer is sometimes *more* permissive than the
analyzer it is meant to replicate.

## Category B — coverage scoped to exactly what a gold word exercises

Very common in the affix/ending blocks. Where the CSV row has several
context candidates (V / t / k / q), typically only the one context an
actual gold word needs is wired; the rest are "not attempted: unattested
in this corpus" or "deferred". Examples: `liaq/2nv`, `liuq/1nv`, `u/1nv`,
`naq/1vv`, `tuq/1vv`, `jaq/1vn`, `allak/1vv`, `qu/2vv`, `raq/1vv`,
`nga/1vv`, most `NounEndings` entries.

This is not fabrication — each wired candidate is a real CSV candidate —
but the breadth of the analyzer is bounded by the 985-word gold sample,
not by the CSV. A held-out corpus exposes the gap: see
`hansard-cache/README.md`, where over the 10k most frequent Hansard words
the FST returns zero decomps for 2,081 words vs. Benoit's 1,547.

## Category C — explicit guesses

Comments that say "guess" / "guessing" outright: the `ptingni/tn-loc-s-1d`
family's 1p forms (`ptingni/ptingnik/ptingnut`, extrapolated by analogy
from the 1d forms), `innaq/1nn`, `ralaaq/1nn`, `tit/1vv`, part of
`ga/tn-nom-s-1s`, `mat/tv-caus-4s`, `li/1q`.

## Category D — a phonology rule justified by a single gold word — RESOLVED, not fitting

`phonology.xfscript`'s `k -> m` rule was flagged because its comment read
as an `aluk/1nn`-specific patch ("verified against this one gold example",
`uqsualummut`).

Checked (2026-09-01): it is a member of the general
total-regressive-assimilation family the FST already implements
(`t->n` / `k->t` / `p->t` / `t->m` / `k->m` / `m->n` / `k->n`), which is
this project's modelling of Benoit's `Action.Assimilation` (`Action.kt`:
the stem's final consonant becomes the following affix's initial). A
consonant assimilating to a following `m` is as systematic as to a
following `n` or `t`. `uqsualummut` is just the gold word where a k-final
stem first met an m-initial affix. **No code change — comments in
`phonology.xfscript` and `generate_affixes.py`'s `aluk` note reworded to
stop implying it's ad-hoc.**

## Category E — continuations added for one gold word — reclassified, not score-inflating

Scattered: "gained a NnSuffixes continuation for _word_", "`ngit` /
`nginnit` / `nginnut` / `kkut` each needed a NEW continuation into
QParticles", etc.

Checked (2026-09-01): these are the **opposite** of overfitting. Each is a
*minimal-scope* continuation — the grammar is wired NARROWER than the real
language ("discourse particles like -lu/-li attaching after a
fully-inflected noun is a general phenomenon, but only these four specific
endings are wired here … wiring what a real gold word needs, not the fully
general" — `lexicon.lexc`). They can only ever hold coverage flat or raise
it, never inflate the first-decomposition ranking.

The narrow one-offs the audit first pointed at (`maanna`, `qujannamiik`,
`taimanna` needing an NvSuffixes path) were already generalised by the
dedup: they are now emitted by `generate_roots.py` with the full
`NounContinuations` hub, not a single-target continuation.

Making the remainder fully general (e.g. every terminal ending →
`QParticles`) is a generator change with an over-generation
(`correct-not-present`) risk — it belongs with Category B (coverage scoped
to the gold sample), not here. **No change made now.**

## Not in scope here

Wiring a CSV context column (`juq` → `tuq` after t/k/q, from Suffixes.csv's
own `t-form`/`k-form`/`q-form` columns) as a **literal lexc entry** rather
than as an `xfst` rule is a deliberate modelling choice
(`doc/dev/plans/fst-analyzer-plan.md`, Milestone 2), not gold-fitting — the surface
form comes from the CSV. Many of the 164 "surface ≠ canonical" hand entries
are this, not Category A.

## The honest number

Until Category A's variants come from a general rule (audit item 2), report
the prototype's gold-standard coverage **with** "≈62 of the 920 `--fair`
correct words depend on hand-added, non-CSV spelling variants", and ideally
alongside a CSV-only coverage figure once audit item 1 produces one.
