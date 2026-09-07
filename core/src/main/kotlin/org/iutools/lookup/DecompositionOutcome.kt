package org.iutools.lookup

import org.iutools.llm.MorphemeRow
import org.iutools.script.Script

/*
 * The result of asking a morphological analyzer to decompose one word,
 * as one of a small set of cases the caller renders differently:
 * decompositions were found, or the attempt failed in a specific way.
 *
 * This is the "decomposition" slice of a full WordLookupResult (see
 * WordLookup) -- broken out so the analyzer step can be talked about on
 * its own.
 *
 * Idle / Loading are transient view states, not outcomes of a finished
 * lookup; they still live here for now because WordLookupScreen drives the
 * analyzer directly and needs them, and will move back out once WordLookup
 * owns the orchestration (see doc/dev/plans/word-lookup-to-core.md).
 */
sealed interface DecompositionOutcome {
    data object Idle : DecompositionOutcome
    data object Loading : DecompositionOutcome
    data class Success(
        val word: String,
        val lenient: Boolean,
        val decompositions: List<List<MorphemeRow>>,
        val hasMore: Boolean,
        val enteredScript: Script,
    ) : DecompositionOutcome
    data class Failure(val reason: FailureReason) : DecompositionOutcome
}

sealed interface FailureReason {
    data object Timeout : FailureReason
    data class AnalysisError(val detail: String?) : FailureReason
    // The user picked the FST analyzer, but its transducer couldn't be
    // loaded (on Android: the bundled asset failed to materialize -- see
    // WordLookupScreen's fstTransducerFile).
    data object FstNotAvailable : FailureReason
}
