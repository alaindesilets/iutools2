"""
Replicates the real analyzer's own decomposition ranking -- see
core/src/commonMain/kotlin/org/iutools/morph/r2l/DecompositionState.kt's
own `compareTo()` (lines ~68-80) and MorphologicalAnalyzer_R2L.kt's
`doDecompose()` step C (`decStatesArray.sort()`, line 85):

    // - Les racines connues en premier
    // - Les racines les plus longues
    // - Le nombre minimum de morphParts en premier
    override fun compareTo(other: DecompositionState): Int {
        var returnValue: Int
        val lengthOfRoot = stem.getRoot().morpheme!!.length
        val lengthOfRootOfOtherDec = other.stem.getRoot().morpheme!!.length
        returnValue = lengthOfRootOfOtherDec.compareTo(lengthOfRoot)
        if (returnValue == 0) {
            returnValue = morphParts.size.compareTo(other.morphParts.size)
        }
        return returnValue
    }

i.e. sort key = (-len(root's CANONICAL form), number of non-root
morphemes), ascending -- longest root wins ties broken by fewest
affixes/endings. This project's own FST tag convention already encodes
each morpheme's canonical form directly in the upper-side tag
("aki+1n", not the surface "aki" separately) -- see lexicon.lexc's own
header comment -- so `parse_hfst_analysis()`'s first element's
canonical text IS exactly Benoit's `stem.getRoot().morpheme`, no extra
lookup needed.

Deliberately NOT replicated here (out of scope for this pass, see
Alain's own request -- just the SORT): `removeCombinedSuffixes()`
(collapses separate-morpheme readings that duplicate a composite-
suffix reading -- needs the CSV "combination" cross-reference data,
which the FST doesn't currently thread through per-analysis) and
`removeMultiples()` (exact-duplicate-string dedup -- already
equivalent to the `set()` dedup this project's own tooling already
applies to hfst-lookup's raw output).

This is a Python translation FOR NOW, per Alain's explicit go-ahead
("réimplemente le tri en python... mais éventuellement, faudrait
utiliser le même code pour les deux") -- the two implementations must
be kept in sync by hand until a real shared-code bridge exists (e.g.
exposing DecompositionState.compareTo's key as a small pure JVM
function callable from both the CLI's own tests and a future Kotlin-
based FST wrapper). If DecompositionState.kt's compareTo ever changes,
this function must change with it.
"""
from histogram import parse_hfst_analysis


def benoit_sort_key(analysis: str) -> tuple[int, int]:
    """(analysis string) -> (-root_canonical_length, num_non_root_morphemes),
    ascending -- longest root first, fewest morphemes as the tiebreak."""
    parts = parse_hfst_analysis(analysis)
    root_canonical, _ = parts[0]
    num_non_root = len(parts) - 1
    return (-len(root_canonical), num_non_root)


def canonical_key(analysis: str) -> str:
    """The decomposition rendered as "{canonical/id}{canonical/id}..." --
    identical to Kotlin Decomposition.toString(). Used as the FINAL,
    always-applied sort tie-break so the ranking is a strict total order:
    decompositions tied on every other key are ordered lexicographically
    rather than left in hfst-lookup's arbitrary path-enumeration order (which
    net.sf.hfst enumerates differently). Not linguistically meaningful."""
    return "{" + "}{".join(f"{c}/{i}" for c, i in parse_hfst_analysis(analysis)) + "}"


def weighted_sort_key(pair: tuple[str, float]) -> tuple[float, int, int]:
    """(analysis, weight) -> (weight, -root_len, morph_count), ascending --
    weight FIRST, ahead of Benoit's own 2 keys: phonology.xfscript's own
    LENIENT rule (see its comment) marks its guessed-final-consonant
    candidates with weight 1.0 vs the normal 0.0, and without this as the
    PRIMARY key, a lenient candidate with a longer apparent root or fewer
    morphemes could outrank a correct strict one on Benoit's own 2 keys
    alone -- confirmed as a real, measured accuracy regression (not
    hypothetical) on the --fair gold standard before this key existed.
    A no-op for any all-weight-0 (strict-only) candidate list -- every
    item ties on the new first key, so ordering among them is exactly
    Benoit's original 2-key sort, unchanged.

    No lexicographic final tie-break here (unlike sort_key_with_frequency):
    this mirrors R2L's own 2-key sort, and R2L keeps its search discovery
    order for ties on purpose -- see MorphologicalAnalyzer.sortDecompositions."""
    analysis, weight = pair
    root_len, morph_count = benoit_sort_key(analysis)
    return (weight, root_len, morph_count)


def sort_like_benoit(pairs: list[tuple[str, float]]) -> list[tuple[str, float]]:
    """Stable sort (ties keep hfst-lookup's own relative order, same as
    Kotlin's Array.sort() being a stable sort) -- mirrors doDecompose()'s
    own step C, PLUS the weight-first key above (dedup is assumed already
    done by the caller, same as this project's existing
    hfst_analyses_weighted()-based tooling already does via its own
    `set()` pass). Takes/returns (analysis, weight) pairs, not bare
    strings -- see weighted_sort_key's own comment for why weight needs
    to travel alongside each analysis through the sort."""
    return sorted(pairs, key=weighted_sort_key)


# ============================================================================
# BEYOND Benoit's own algorithm, added by Alain's explicit direction
# ("suivons le chemin déjà tracé par Benoit [pour la transitivité]... [mais]
# je pense que le mieux serait" a genuine 3rd tie-break criterion, "dans un
# sens, c'est une solution plus clean" -- NOT a port, a deliberate
# improvement). Diagnosed first: among the 284 --fair words where Benoit's
# real output ranks the correct decomposition #1 but ours (2-key-sorted)
# doesn't, the correct one is ALWAYS present and in 99/284 (35%) cases is
# the immediate runner-up, tied EXACTLY on both of Benoit's own keys (same
# root length, same morpheme count) -- Benoit's own tie-break in that
# situation is just Kotlin's stable-sort preserving his search's own
# discovery order, an algorithm-specific artifact this project's
# fundamentally different FST traversal has no way to reproduce (confirmed
# by reading DecompositionState.kt's compareTo() -- it genuinely has no
# 3rd key).
#
# Instead: break remaining ties by preferring whichever candidate's
# morphemes are, in aggregate, more frequent -- e.g. juq/1vn (a
# nominalizing suffix, "the one who...") is far more common than the
# homograph juq/tv-ger-3s (a gerundive verb ending), so given a tie,
# prefer the nominal reading.
#
# The frequency table itself was FIRST built from the --fair gold
# standard's own 922 correct decompositions, validated with a leave-
# one-out test (excluding each test word's own contribution before
# scoring it, to rule out simple memorization): resolved 244/284
# (85.9%) of the tied cases, projecting top-1 accuracy from 46.0% to
# ~72.5% (Benoit's own real output is 72.6%).
#
# SWITCHED (2026-08-22) to the Hansard-derived table instead (see
# tools/fst/hansard-cache/, built from the real analyzer's own top-1
# picks over the 9,999 most frequent Hansard word forms) -- per
# Alain's own explicit call: measured on --fair, the Hansard table
# scores very slightly lower than the gold-only one (74.5% vs 74.9%
# top-1, similarly small gaps through N=5 -- see hansard-cache/
# README.md for the full table), but Alain judged that gap "minime"
# and worth trading for a frequency distribution that's presumably
# more GENERAL and less biased toward the specific 922 gold words --
# gold-only has a built-in "in-domain" advantage on this exact
# benchmark that doesn't reflect real generalization. A COMBINED
# (gold+Hansard summed) table was tried and rejected -- Alain judged
# it wasn't a good idea either (and it measured no better: 74.3%,
# actually the worst of the three, likely because Hansard's own raw
# volume, 27,809 pair-occurrences, swamps gold's much smaller counts
# when simply summed, so "combined" ends up close to Hansard-only
# anyway without being genuinely independent of gold).
# ============================================================================
import json
import re
from collections import Counter
from pathlib import Path

HANSARD_CACHE_PATH = Path(__file__).resolve().parent / "hansard-cache" / "top10k_words_benoit_decomps.jsonl"
_BENOIT_DECOMP_RE = re.compile(r"\{([^:}]+):([^/}]+)/([^}]+)\}")


def build_frequency_table() -> Counter:
    """{(canonical, id): count} from the real analyzer's own top-1 pick
    for each of the ~9,999 most frequent Hansard word forms (see
    tools/fst/hansard-cache/README.md for exactly how this cache was
    built, and the module comment above for why this replaced the
    gold-standard-only table this function originally built)."""
    freq = Counter()
    with HANSARD_CACHE_PATH.open(encoding="utf-8") as f:
        for line in f:
            rec = json.loads(line)
            decomps = rec.get("decompositions")
            if not decomps:
                continue
            for _surf, canon, mid in _BENOIT_DECOMP_RE.findall(decomps[0]):
                freq[(canon, mid)] += 1
    return freq


FREQUENCY_TABLE = build_frequency_table()


def frequency_score(analysis: str, freq: Counter = FREQUENCY_TABLE) -> int:
    """Sum of gold-frequency across this analysis's own (canonical, id)
    pairs -- higher means "made of more commonly-correct morphemes"."""
    return sum(freq.get(pair, 0) for pair in parse_hfst_analysis(analysis))


def sort_key_with_frequency(pair: tuple[str, float], freq: Counter = FREQUENCY_TABLE) -> tuple[float, int, int, int]:
    """weight FIRST (see weighted_sort_key's own comment), then Benoit's
    own 2 keys, PLUS gold-morpheme-frequency (descending) as the 4th
    tie-break -- see the module-level comment above for why the frequency
    key exists and isn't part of Benoit's own real algorithm, and
    weighted_sort_key's comment for why weight now comes first."""
    analysis, weight = pair
    root_len, morph_count = benoit_sort_key(analysis)
    return (weight, root_len, morph_count, -frequency_score(analysis, freq),
            canonical_key(analysis))


def sort_with_frequency_tiebreak(
    pairs: list[tuple[str, float]], freq: Counter = FREQUENCY_TABLE
) -> list[tuple[str, float]]:
    """The project's actual production ranking: weight first (strict
    always ranks ahead of LENIENT-marked candidates), then Benoit's 2
    keys, then the gold-frequency tie-break. Takes/returns (analysis,
    weight) pairs, not bare strings."""
    return sorted(pairs, key=lambda p: sort_key_with_frequency(p, freq))
