"""
Milestone 5+ scoping (doc/dev/plans/fst-analyzer-plan.md): ranks affix/ending
signature ids by how many gold-standard words use them, to decide which
order to add phonological rules in -- frequency-first, since a handful of
common affixes should cover a large fraction of the 919-word gold standard
quickly, and (as a side benefit) frequent affixes tend to be well-attested
across all four V/t/k/q contexts, so implementing them early also means
implementing them *thoroughly* early.

Root ids (e.g. "1n", "1v") are excluded from the ranking: a root id alone
doesn't identify a specific root (many unrelated roots share e.g. "1n" --
it's just "noun, homograph #1 of its own entry"), and roots are the cheap
part anyway (usually zero phonology) -- see AGENTS.md/the plan's own
discussion of where the real risk lives. The root is always the *first*
morpheme of a decomposition (Benoit's own model establishes word class from
the root, then chains affixes after it) -- classifying by digit-vs-letter
id prefix was tried first and is wrong: derivational suffix ids (e.g.
"1vn"/"juq", "1nv"/"liri", "1nn"/"kkut", "1vv"/"nngit", "1q"/"qai") are
*also* digit-prefixed, same convention as root ids, so that check silently
misclassified every derivational suffix as a root. Position in the
decomposition is the only reliable signal.

Also reports affix *bigrams* (adjacent affix-id pairs within a word) as a
fallback: if the single most-frequent affix turns out too large a batch to
implement and verify safely in one go, its word-set can be subdivided by
which other affix most often co-occurs with it, rather than an arbitrary
word-count cutoff.

And a coverage curve: adding affixes in frequency order, how many
gold-standard words become *fully* decomposable (every affix id they use is
in the supported set) after each addition -- the real "how fast does
coverage grow" answer, not just per-affix frequency in isolation.

One more wrinkle, found by inspecting the first run's output: a short
derivational-suffix-class id (e.g. "1vn") is *not* a unique morpheme any
more than a root id is -- "juq" and "ji" are both tagged "1vn" (confirmed
directly in the gold standard: {tu:juq/1vn}{...}{ji:ji/1vn} in the same
word), the same per-morpheme homograph-index convention as roots, just for
affixes. Terminal-ending ids (e.g. "tn-nom-p") don't have this problem --
they encode a full grammatical cell (type-mode/case-number[-person]) and
are unique in practice. Detected by shape: an id containing a hyphen is
treated as already-unique; a short hyphen-less id is disambiguated by
pairing it with its own canonical text ("juq/1vn" vs "ji/1vn") so the two
don't get silently merged into one inflated count.

Gold-standard source: reads data/grammar/gold-standard/gold-standard.csv via
gold_standard_csv_reader.py -- entries with no real decomposition (`null`,
or a `[decomposition:...]` placeholder for proper names) simply produce zero
regex matches and are skipped, no special-casing needed.

Usage (from repo root):
    python3 data/grammar/fst/affix_frequency.py
"""
import re
from collections import Counter
from pathlib import Path

from gold_standard_csv_reader import AnalyzerCase, load_gold_standard

OUTPUT_FILE = Path(__file__).parent / "affix-priority.md"

MORPHEME_RE = re.compile(r"\{[^:]+:([^/]+)/([^}]+)\}")

# The real :cli accuracy suite (MorphologicalAnalyzer__AccuracyTest.kt's
# evaluateAccuracy()/skipCase()) doesn't evaluate every word in the gold
# standard -- it skips ones flagged misspelled/possibly-misspelled/
# proper-name/borrowed/decomp-unknown, on the reasoning that the analyzer
# can't fairly be expected to handle them. AGENTS.md's own "919 evaluated
# words" figure for the Hansard suite is exactly 1092 distinct Hansard
# words minus 173 carrying one of these flags.


def _is_flagged(case: AnalyzerCase) -> bool:
    return (
        case.is_misspelled
        or case.is_possibly_misspelled
        or case.is_borrowed
        or case.is_proper_name
        or case.decomp_unknown
    )


def affix_key(canonical: str, morph_id: str) -> str:
    """Terminal-ending ids (contain a hyphen) already identify a unique
    morpheme; short derivational-class ids (e.g. "1vn") don't -- pair with
    canonical text so distinct suffixes sharing a class id aren't merged."""
    return morph_id if "-" in morph_id else f"{canonical}/{morph_id}"


def load_words(exclude_flagged: bool = False):
    """Yields (surface_word, [(canonical, id), ...]) for every gold-standard
    entry that has a real {surface:canonical/id} decomposition.

    exclude_flagged: when True, restricts to exactly the population the
    real :cli Hansard accuracy suite evaluates -- the "hansard" source only
    (NOT the separate "words_that_failed_before" gold, which :cli runs as
    its own test method), minus the misspelled/proper-name/borrowed/
    decomp-unknown words (see _is_flagged()). Use it whenever comparing
    this prototype's numbers against the :cli FST/R2L figures, so the two
    are over an identical population. Default is False (permissive: both
    gold sources, nothing skipped) -- this project's FST work has used the
    wider net for gap-finding.

    Yields one entry PER accepted parse: a multi-parse gold-standard entry
    produces several (word, morphemes) pairs, which group_by_word() then
    collects so a caller can accept a result matching any of them."""
    sources = ["hansard"] if exclude_flagged else ["hansard", "words_that_failed_before"]
    for source in sources:
        for word, case in load_gold_standard(source).items():
            if exclude_flagged and _is_flagged(case):
                continue
            if not case.correct_decomps:
                continue
            for decomp in case.correct_decomps:
                morphemes = MORPHEME_RE.findall(decomp)
                if morphemes:
                    yield word, morphemes


def main():
    words = list(load_words())

    affix_freq = Counter()
    bigram_freq = Counter()
    words_affix_ids = []  # parallel to `words`: each word's affix keys (root excluded)
    for _, morphemes in words:
        affix_ids = [affix_key(canon, mid) for canon, mid in morphemes[1:]]  # morphemes[0] is always the root
        words_affix_ids.append(affix_ids)
        affix_freq.update(affix_ids)
        for id_a, id_b in zip(affix_ids, affix_ids[1:]):
            bigram_freq[(id_a, id_b)] += 1

    ranked_affixes = affix_freq.most_common()

    # Coverage curve: add affixes in frequency order, count words whose
    # every affix id is already supported after each addition. A word with
    # zero affixes (bare root, e.g. "maanna") is trivially always covered.
    supported: set[str] = set()
    coverage_rows = []
    covered_words = sum(1 for ids in words_affix_ids if not ids)
    for morph_id, freq in ranked_affixes:
        supported.add(morph_id)
        newly_covered = sum(
            1 for ids in words_affix_ids if ids and set(ids) <= supported
        )
        newly_covered += sum(1 for ids in words_affix_ids if not ids)
        delta = newly_covered - covered_words
        covered_words = newly_covered
        coverage_rows.append((morph_id, freq, covered_words, delta))

    lines = []
    lines.append(f"# Affix priority order ({len(words)} gold-standard entries scanned)\n")
    lines.append(
        "Generated by `data/grammar/fst/affix_frequency.py` -- rerun after adding gold-standard\n"
        "words to refresh. Ranked by raw frequency (how many words use this affix id at\n"
        "all); \"fully covered\" counts words where *every* affix id they use is among\n"
        "the affixes ranked so far (roots don't count against coverage -- assumed cheap).\n"
    )
    lines.append("| rank | affix id | word count | cumulative fully-covered words | +new |")
    lines.append("|---|---|---|---|---|")
    for i, (morph_id, freq, cum_covered, delta) in enumerate(coverage_rows, start=1):
        lines.append(f"| {i} | `{morph_id}` | {freq} | {cum_covered} | +{delta} |")

    lines.append("\n## Top affix bigrams (fallback: subdividing an over-large affix)\n")
    lines.append("| affix id pair | word count |")
    lines.append("|---|---|")
    for (id_a, id_b), freq in bigram_freq.most_common(30):
        lines.append(f"| `{id_a}` -> `{id_b}` | {freq} |")

    report = "\n".join(lines) + "\n"
    OUTPUT_FILE.write_text(report, encoding="utf-8")
    print(report)
    print(f"(written to {OUTPUT_FILE})")


if __name__ == "__main__":
    main()
