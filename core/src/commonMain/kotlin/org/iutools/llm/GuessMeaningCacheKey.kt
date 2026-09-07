package org.iutools.llm

/*
 * To avoid calling the LLM evey time, we cache the some of its responses. 
 * 
 * This class is used to generate a cache key for a Guess Meaning request for a 
 * particular word.
 * 
 * The key not only captures the word, but also other parameters used to prompt 
 * the LLM (ex: the actual prompt used)
 */

/** Language the model is asked to answer in. */
enum class MeaningLanguage { ENGLISH, FRENCH }

/** The word looked up, and whether lenient analysis was on (it changes the
 *  decompositions, and so the answer). */
data class GuessMeaningCacheKey(val word: String, val lenient: Boolean)

/**
 * A full attempt's identity: [word] plus everything else that can make the
 * model answer differently -- the language, which backend, and the exact
 * system and seed prompt text (which the debug inspector can edit).
 */
data class GuessMeaningConversationKey(
    val word: GuessMeaningCacheKey,
    val isLocalModel: Boolean,
    val meaningLanguage: MeaningLanguage,
    val systemPrompt: String,
    val seedMessage: String,
)
