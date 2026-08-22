"""
Milestone 5+ scoping (doc/fst-analyzer-plan.md): ranks affix/ending
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

Gold-standard source: reads directly from the two Kotlin files (never
hand-copied) via a regex over `AnalyzerCase("word", arrayOf("decomp", ...`
-- entries with no real decomposition (`null`, or a `[decomposition:...]`
placeholder for proper names) simply produce zero regex matches and are
skipped, no special-casing needed.

Usage (from repo root):
    python3 tools/fst/affix_frequency.py
"""
import re
from collections import Counter
from pathlib import Path

REPO_ROOT = Path(__file__).parent.parent.parent
GOLD_FILES = [
    REPO_ROOT / "cli/src/test/kotlin/org/iutools/morph/MorphAnalGoldStandard_Hansard.kt",
    REPO_ROOT / "cli/src/test/kotlin/org/iutools/morph/MorphAnalGoldStandard_WordsThatFailedBefore.kt",
]
OUTPUT_FILE = Path(__file__).parent / "affix-priority.md"

CASE_RE = re.compile(r'AnalyzerCase\(\s*"([^"]+)"\s*,\s*arrayOf\(\s*"([^"]*)"')
MORPHEME_RE = re.compile(r"\{[^:]+:([^/]+)/([^}]+)\}")

# The real :cli accuracy suite (MorphologicalAnalyzer__AccuracyTest.kt's
# evaluateAccuracy()/skipCase()) doesn't evaluate every word in the gold
# standard -- it skips ones flagged with one of these five chained method
# calls on the AnalyzerCase (misspelled/possibly-misspelled/proper-name/
# borrowed/decomp-unknown), on the reasoning that the analyzer can't
# fairly be expected to handle them. AGENTS.md's own "919 evaluated
# words" figure for the Hansard suite is exactly 1092 distinct Hansard
# words minus 173 carrying one of these flags -- confirmed by reproducing
# that arithmetic here before trusting this regex. load_words()'s own
# CASE_RE match ends at the decomposition string and never sees these
# chained calls (they appear later in the same addCase(...) statement),
# so finding them needs a second pass over each statement's own text.
FLAG_RE = re.compile(
    r"\.isMisspelled\(\)|\.possiblyMisspelledWord\(\)|\.isProperName\(\)"
    r"|\.isBorrowedWord\(\)|\.correctDecompUnknown\(\)"
)


def flagged_words():
    """Returns the set of words the real :cli accuracy suite skips (see
    FLAG_RE's own comment) -- last addCase() for a given word wins, same
    overwrite behavior as MorphAnalGoldStandardAbstract's own
    case4word map."""
    flagged = {}
    for path in GOLD_FILES:
        text = path.read_text(encoding="utf-8")
        starts = [m.start() for m in re.finditer(r"addCase\(AnalyzerCase\(", text)]
        starts.append(len(text))
        for i in range(len(starts) - 1):
            segment = text[starts[i]:starts[i + 1]]
            m = re.search(r'addCase\(AnalyzerCase\(\s*"([^"]+)"', segment)
            if not m:
                continue
            flagged[m.group(1)] = bool(FLAG_RE.search(segment))
    return {w for w, is_flagged in flagged.items() if is_flagged}


def affix_key(canonical: str, morph_id: str) -> str:
    """Terminal-ending ids (contain a hyphen) already identify a unique
    morpheme; short derivational-class ids (e.g. "1vn") don't -- pair with
    canonical text so distinct suffixes sharing a class id aren't merged."""
    return morph_id if "-" in morph_id else f"{canonical}/{morph_id}"


def load_words(exclude_flagged: bool = False):
    """Yields (surface_word, [(canonical, id), ...]) for every gold-standard
    entry that has a real {surface:canonical/id} decomposition.

    exclude_flagged: when True, also drops every word flagged_words()
    reports -- the same misspelled/proper-name/borrowed/decomp-unknown
    words the real :cli accuracy suite itself doesn't evaluate. Default
    is False (permissive, includes everything with a decomposition) --
    this project's own FST work has deliberately used the permissive set
    throughout, since a wider net finds more gaps to fix; pass True
    specifically when comparing this prototype's coverage percentage
    against AGENTS.md's own :cli figures, so the two numbers are over
    the same population."""
    skip = flagged_words() if exclude_flagged else set()
    for path in GOLD_FILES:
        text = path.read_text(encoding="utf-8")
        for word, decomp in CASE_RE.findall(text):
            if word in skip:
                continue
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
        "Generated by `tools/fst/affix_frequency.py` -- rerun after adding gold-standard\n"
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
