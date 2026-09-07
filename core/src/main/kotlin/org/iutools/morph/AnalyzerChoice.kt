package org.iutools.morph

/*
 * Which morphological analyzer decomposes a word.
 *
 * UQAILAUT is Benoit Farley's original right-to-left analyzer
 * (MorphologicalAnalyzer_R2L) -- the default. FST is the newer HFST
 * finite-state analyzer (MorphologicalAnalyzer_FST in the :fst module),
 * still experimental (partial lexicon, no ranking of its candidates yet).
 */
enum class AnalyzerChoice { UQAILAUT, FST }
