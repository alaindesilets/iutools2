package org.iutools.llm

import org.iutools.dictionary.DictionarySource

/*
 * The pre-translated sentences that guessMeaningSeedPrompt() drops around
 * the data when it writes the message it sends to the LLM.
 *
 * That message is assembled from facts about one word -- its
 * decomposition(s), a dictionary definition, example sentences. The wording
 * in between is plain prose: the opening question, a one-line "here is what
 * follows and how to use it" before each section, and a stand-in for a
 * morpheme whose meaning is unknown. That prose depends on the user's
 * language and lives in the app's string resources, which :core can't
 * read -- so the caller resolves the strings and hands them over in this
 * one object, letting guessMeaningSeedPrompt() stay a plain function.
 *
 * Some fields are templates: "%1$s" is replaced with the word (in
 * syllabics), and the decomposition-number template uses "%1$d" for the
 * number. Each field below says which it is.
 */
data class GuessMeaningSeedLabels(
    // "%1$s": the word, in syllabic. Placed first (not last, as before), per
    // Alain's request -- the seed now opens with the actual question, then
    // explains what follows it, rather than piling up data and asking the
    // question at the end.
    val questionTemplate: String,
    // Two variants, not one -- when there's just one decomposition it's
    // presented as fact; with several, the prompt also has to say they're
    // competing alternatives, not all necessarily correct (see
    // decompositions.size in guessMeaningSeedPrompt()).
    val decompositionSingleIntro: String,
    val decompositionMultipleIntro: String,
    val decompositionNumberTemplate: String,
    val unknownMorpheme: String,
    // The localized name of each dictionary, as it should read inside the
    // seed message (e.g. "Found in the Spalding dictionary"). Used for the
    // shorter-word definitions the seed lists.
    val dictionarySourceNames: Map<DictionarySource, String>,
    // "%1$s": the word, in syllabic.
    val shorterWordDictionaryIntroTemplate: String,
    // Two variants, not one -- the instruction to verify candidate meanings
    // against the examples only makes sense when they're for the exact word
    // (see guessMeaningSeedPrompt's hansardExamplesAreExactMatch parameter
    // and its own header comment); when they're only for a shorter, related
    // word, the prompt says the opposite instead. "%1$s": the word, in
    // syllabic, in both variants.
    val hansardExamplesExactIntroTemplate: String,
    val hansardExamplesShorterWordIntroTemplate: String,
)
