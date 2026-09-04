"""
Generates core/src/commonMain/kotlin/org/iutools/morph/MorphemeFrequencyPrior.kt
-- the embedded {morphemeId -> count} table the shared decomposition ranking
(MorphologicalAnalyzer.sortDecompositions) uses as its final tie-break.

Provenance: the counts are how often each morpheme (canonical/idSuffix, i.e.
the plain morpheme id) appears in the REAL analyzer's own current top-1
decomposition across the ~9,999 most frequent Nunavut Hansard word forms --
exactly the table benoit_sort.build_frequency_table() builds in memory for
the Python-side FST tooling. This script just freezes that same table into
Kotlin source so :core (and, through it, both MorphologicalAnalyzer_R2L and
MorphologicalAnalyzer_FST) can apply it without a Python dependency.

It is a SNAPSHOT: it was built from R2L's top-1 picks under the ranking that
existed BEFORE the frequency tie-break itself was added, and is not
regenerated on every build. Re-run this script (after rebuilding
hansard-cache/top10k_words_benoit_decomps.jsonl) if the ranking or the
lexicon drifts far enough to be worth it.

Usage (from data/grammar/fst/):
    python3 generate_morpheme_frequency_prior.py
"""
from pathlib import Path

from benoit_sort import FREQUENCY_TABLE

OUT = (
    Path(__file__).resolve().parents[3]
    / "core/src/commonMain/kotlin/org/iutools/morph/MorphemeFrequencyPrior.kt"
)

HEADER = '''package org.iutools.morph

/*
 * Frequency prior over morpheme ids, used only as the final tie-break in
 * MorphologicalAnalyzer.sortDecompositions() (see its own doc). count(id) is
 * how many of the ~9,999 most frequent Nunavut Hansard word forms have `id`
 * somewhere in the real analyzer's own top-1 decomposition -- so a higher
 * summed count means a reading built of morphemes that are more often the
 * analyzer's own first pick.
 *
 * GENERATED -- do not hand-edit. Regenerate with
 * data/grammar/fst/generate_morpheme_frequency_prior.py (which reads
 * data/grammar/fst/hansard-cache/top10k_words_benoit_decomps.jsonl). This is a
 * deliberate snapshot, not a build-time artifact: see that script's header
 * and data/grammar/fst/benoit_sort.py's module comment for why the table is frozen
 * rather than recomputed, and why it is a genuine improvement bolted onto
 * Benoit Farley's original two-key sort rather than part of it.
 *
 * Embedded as Kotlin source (not a resource) so it is available on every
 * target including Kotlin/Native (iOS), same reasoning as the linguistic
 * CSVs.
 */
object MorphemeFrequencyPrior {

    /** Summed count() over every morpheme id in a decomposition. */
    fun score(morphemeIds: List<String>): Int = morphemeIds.sumOf { count(it) }

    fun count(morphemeId: String): Int = COUNTS[morphemeId] ?: 0

    private val COUNTS: Map<String, Int> by lazy {
        val out = HashMap<String, Int>()
        for (line in DATA.trim().lineSequence()) {
            val sp = line.lastIndexOf(' ')
            out[line.substring(0, sp)] = line.substring(sp + 1).toInt()
        }
        out
    }

    // "<morphemeId> <count>" per line, highest count first.
    private val DATA = """
'''


def main() -> None:
    items = sorted(FREQUENCY_TABLE.items(), key=lambda kv: (-kv[1], kv[0][0], kv[0][1]))
    body = "".join(f"{canon}/{sig} {n}\n" for (canon, sig), n in items)

    OUT.write_text(HEADER + body + '"""\n}\n', encoding="utf-8")
    print(f"Wrote {OUT} ({len(items)} morpheme ids)")


if __name__ == "__main__":
    main()
