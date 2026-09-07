package org.iutools.morph

/**
 * Per-word Recall/Precision/reference-decomp-rank snapshot for one gold
 * standard, checked by one analyzer -- e.g. R2L's expectations differ from
 * FST's for the same words, hence a subclass per analyzer per gold file
 * (MorphAnalCurrentExpectations_Hansard, _FST_Hansard, etc).
 *
 * Sparse by design, like the old OutcomeType-based version this replaces:
 * only words that deviate from the "perfect" default (expectedFor()) need
 * an expect(...) call. `matched`/`produced` are exact integer counts (the
 * shared numerator/denominators of Recall and Precision), not the ratios
 * themselves -- comparing integers needs no float tolerance.
 */
abstract class MorphAnalCurrentExpectationsAbstract {

    data class WordExpectation(val matched: Int, val produced: Int, val referenceRank: Int?)

    protected abstract fun initMorphAnalCurrentExpectations()

    var focusOnWord: String? = null

    val expWord = mutableMapOf<String, WordExpectation>()

    init {
        initMorphAnalCurrentExpectations()
    }

    fun expect(word: String, matched: Int, produced: Int, referenceRank: Int?) {
        expWord[word] = WordExpectation(matched, produced, referenceRank)
    }

    /**
     * The "perfect" default for a word not explicitly listed: the analyzer's
     * produced set exactly equals all_correct_decomps (100% recall AND 100%
     * precision) and the reference decomp is first. `allCorrectCount` comes
     * from the live gold CSV (not stored in the snapshot itself -- it's
     * gold-data-dependent, not analyzer-dependent, so storing it would cause
     * false mismatches whenever the gold CSV is regenerated).
     */
    fun expectedFor(word: String, allCorrectCount: Int): WordExpectation =
        expWord[word] ?: WordExpectation(matched = allCorrectCount, produced = allCorrectCount, referenceRank = 0)

    /**
     * True when `live` is WORSE than `expected` on any of recall, precision,
     * or reference rank. An improvement (better than expected) is not a
     * regression -- see MorphologicalAnalyzer__AccuracyTest's own docs for
     * why this project deliberately doesn't fail on every drift.
     */
    fun isRegression(live: WordExpectation, expected: WordExpectation): Boolean {
        // Recall worse: same denominator (allCorrectCount) on both sides,
        // so comparing the shared numerator directly is equivalent to
        // comparing recall.
        val recallWorsened = live.matched < expected.matched

        // Precision worse: cross-multiply matched/produced fractions
        // (exact integer arithmetic, no float rounding). produced == 0
        // (no output at all) is worst-possible-precision, but only a
        // regression if the snapshot wasn't already produced == 0.
        val precisionWorsened = when {
            live.produced == 0 && expected.produced == 0 -> false
            live.produced == 0 -> true
            expected.produced == 0 -> false
            else -> live.matched.toLong() * expected.produced < expected.matched.toLong() * live.produced
        }

        // Reference rank worse: null (never found) is worst; otherwise a
        // larger 0-based rank is worse.
        val rankWorsened = when {
            live.referenceRank == null && expected.referenceRank == null -> false
            live.referenceRank == null -> true
            expected.referenceRank == null -> false
            else -> live.referenceRank > expected.referenceRank
        }

        return recallWorsened || precisionWorsened || rankWorsened
    }
}
