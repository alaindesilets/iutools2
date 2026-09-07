package org.iutools.llm

/*
 * The three pre-translated messages GuessMeaningEngine may need to put into
 * the conversation when a call doesn't produce a normal reply.
 *
 * Passed in for the same reason as GuessMeaningSeedLabels: the real wording
 * is language-dependent and lives in the app's string resources, which
 * :core can't read. The caller resolves them and hands them over.
 *
 * [genericTemplate] has one "%1$s" placeholder, filled with the underlying
 * error's own text (see LlmResponse.Failed) so the user sees a translated
 * frame around an untranslated detail rather than the raw detail alone.
 */
data class GuessMeaningErrorLabels(
    val unauthorized: String,
    val localModelDisabled: String,
    val genericTemplate: String,
)
