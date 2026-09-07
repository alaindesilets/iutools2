package org.iutools.corpus

/*
 * One aligned sentence pair pulled from a bilingual corpus: the same
 * sentence written once in Inuktitut and once in English, plus the date of
 * the document it was taken from.
 *
 * It is a plain carrier of those three strings -- it does no lookup or
 * formatting of its own. Some source produces these (a local Hansard
 * index, a translation-memory query, a web search) and some consumer reads
 * them (a dictionary screen showing usage examples, the "Guess Meaning"
 * prompt handing the LLM real sentences to reason from). The Inuktitut side
 * is expected to be in syllabics.
 */
data class BilingualExample(val inuktitut: String, val english: String, val date: String)
