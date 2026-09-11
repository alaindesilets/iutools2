# The iutools2 dictionary

This folder holds a dataset that provides information about the 100,000 most common words in
the Nunavut Hansard.

It is made up of two files:
- words.jsonl: The words, with their morphological decompositions, meanings and definitions.
- stems.jsonl: A list of word stems from which all words in words.jsonl are derived.

## Use cases (read this before touching the schema)

The whole reason these files exist: **given a word, determine its
meaning.** Every other field (decomps, attested, stems, ...) exists to
support that — it is plumbing and evidence, not the goal. There are three
ways a word's meaning gets determined, and the schema should be justified
field by field against them:

1. **The word itself has a definition in a (human) dictionary.**
2. **A related word has a dictionary definition**, and this word's meaning
   can be derived from that related word's definition — automatically, or
   by the user doing it in their head.
3. **A related word has a Guess-Meaning (LLM) reading**, and this word's
   meaning can be derived from that related word's reading — automatically,
   or by the user doing it in their head.

Next session: revisit the draft schema below against these three cases —
in particular, `prominent_related_words` and `definitions_found_in` are
the mechanism for cases 2 and 3, not just a "see also" nicety, so their
design should follow from what those two derivations actually need.

## File schemas

Below are the schemas for the two datat files.

### words.jsonl schema

Current draft 

{
  "word_roman": "tusaqtutit",
  "word_syll": "ᑐᓴᖅᑐᑎᑦ",

  // this word's frequency rank in the 100k (from S1)
  "rank": 8123,
  // its token count in the corpus (from S1)
  "count": 9,
  // → stems.jsonl (stem of top_decomp; null if the word has no decomp)
  "stem_key": "tusaq/1v",

  // the COMPLETE set of decompositions for this word, ordered best-first.
  // How that order is arrived at is not this file's concern.
  "decomps": [
    { "decomp": ["tusaq/1v", "jutit/tv-decl-2s"], "lenient": false },
    { "decomp": ["tusaq/1v", "juti/1vn", "t/tn-nom-p"], "lenient": true }
    // …
  ],
  // convenience: == decomps[0].decomp
  "top_decomp": ["tusaq/1v", "jutit/tv-decl-2s"],
  // convenience: == decomps[0].lenient
  "top_decomp_is_lenient": false,
  // convenience: any decomps[*].lenient
  "some_lenient_decomps_included": true,

  // true ⇔ this attested word IS its stem's headword (citation form).
  // absent-as-true when the headword is synthetic (no row for it)
  "is_stem_headword": false,
  // true ⇔ this word IS its stem's most_frequent_form (the Guess Meaning
  // input; its llm_meanings entries have source "copy", not "rule")
  "is_stem_most_frequent_form": false,

  // the endings / enclitics THIS form adds to the STEM (not a delta vs
  // the headword or most_frequent_form) — the input to the meaning rule
  "inflection_from_stem": {
    "endings": ["tv-decl-2s"],
    "enclitics": [],
    "features": { "class": "verbal", "mood": "declarative", "person": "2s" }
  },

  // no relation_to_headword / relation_to_stem: the linguistic content is
  // inflection_from_stem.features, and whether the rule could render each
  // sense is recorded per sense in llm_meanings[].source ("rule" vs "llm").

  // -- OPEN: does words.jsonl carry a per-form reading at all? (does the
  //    app show "you hear" for tusaqtutit, or just the stem's meaning
  //    "to hear" for every form of the paradigm?) --
  // one entry per stem sense, position-aligned to stems.jsonl.llm_meanings
  "llm_meanings": [
    {
      // this sense's stem meaning ⊕ this form's features (2s, decl)
      "en": "you hear", "fr": "tu entends",
      // "rule" | "copy" (this word IS most_frequent_form) | "llm" (rule can't render it)
      "source": "rule"
    }
  ]
}

Proposed new draft

```jsonc
{
  // The word in roman and syllabic scripts
  "word_roman": "tusaqtutit",
  "word_syll": "ᑐᓴᖅᑐᑎᑦ",

  // Word's rank among the 100k words, when sorted by frequency
  "rank": 8123,

  // Word's frequency in the NRC Nunavut Hansard corpus
  "count": 9,

  // Applicable word stems (llm attested), found in the stems.jsonl file
  "stems": ["tusaq/1v"],

  // Decompositions for this word, best-first (org.iutools.morph.rerank.
  // ReferenceReranker's order), CAPPED to however many were actually sent
  // to the LLM (currently 10) -- not the complete candidate set. See
  // total_decomps below for how many there really were; a word can have
  // several hundred (one seen: 420) once every ending/enclitic
  // combination is counted, so keeping them all here would swamp the
  // file for no benefit -- only the capped, re-ranked, actually-evaluated
  // ones are informative.
  //
  // llm_attested=true means a Large Language Model (AI) has
  // attested that the decomp corresponds to one of the word's
  // meanings, as observed in the Hansard. llm_attested=false
  // means that although the decomposition is grammatically
  // correct, the LLM could not attest that it corresponds to an
  // actual meaning for that word, as seen in the Hansard corpus.
  // llm_attested is absent/null on a Phase-1-only record (the
  // LLM pass hasn't run on this word yet) -- distinct from false.
  //
  // lenient=true means the word may have omitted the written
  // symbol for a dropped final consonant in one of its morphemes.
  "decomps": [
    {
      "decomp": ["tusaq/1v", "jutit/tv-decl-2s"],
      "lenient": false
    },
    {
      "decomp": ["tusaq/1v", "juti/1vn", "t/tn-nom-p"],
      "lenient": true,
      "llm_attested": true
    }
    // … up to 10
  ],

  // How many DISTINCT candidate decompositions this word actually had
  // before the cap above was applied -- e.g. decomps.length == 10 but
  // total_decomps == 47 means 37 lower-ranked candidates were never even
  // shown to the LLM. total_decomps == decomps.length means nothing was
  // truncated. (org.iutools.morph.cli guess_meaning's own reply already
  // carries this as "totalDecomps" -- this field is that same count,
  // persisted.)
  "total_decomps": 10,

  // How many of decomps.length were actually sent to the LLM for a
  // verdict (org.iutools.llm guessMeaningStaged grows the window in
  // stages -- top-2, then top-5, then top-10 -- stopping as soon as
  // something is attested or no Hansard example is left unexplained, so
  // most words never reach the full decomps.length). Decomps beyond this
  // count have llm_attested == null, same as ones beyond total_decomps.
  "decomps_evaluated": 2,

  // The top decomposition in the list above.
  // Provided here for convenience.
  "top_decomp": ["tusaq/1v", "jutit/tv-decl-2s"],

  // true if the top decomposition is lenient.
  // Provided here for convenience.
  "top_decomp_is_lenient": false,

  // true if some of the decomps above are lenient.
  // Provided here for convenience.
  "some_lenient_decomps_included": true,

  // true if more than one of the decomps above has
  // llm_attested=true. Expected to be false for almost every
  // word (usually a single decomp is attested); a convenience
  // flag to spot and re-evaluate the exceptions, rather than a
  // sign the schema needs to support multiple senses per word.
  "several_decomps_are_llm_attested": false,

  // Possible meanings, generated by an AI Large Language Model
  // (LLM). The meanings were produced from the list of
  // morphological decompositions and bilingual sentences from
  // the Nunavut Hansard. Meanings are generated in English first
  // (the bilingual sentences are en-iu), then translated to
  // French by the LLM.
  "llm_meanings": [
    { "en": "you hear", "fr": "tu entends" }
    // …
  ],

  // Confidence in the llm_meanings above, taken as a whole (not
  // one value per individual sense) — self-assigned by the LLM,
  // based on how much Hansard evidence backed its answer.
  "llm_confidence": 0.8,

  // Number of Hansard sentence pairs used as evidence for
  // llm_meanings. Kept separate from llm_confidence so it can be
  // audited on its own.
  "llm_evidence_count": 2,

  // Hansard sentence pairs (of the llm_evidence_count above) whose usage
  // of the word the LLM could NOT explain with any of the decomps/senses
  // above -- the other direction of llm_attested: not "this decomp has no
  // evidence" but "this evidence has no decomp". A non-empty list is a
  // coverage-gap signal (a real reading is missing from decomps above,
  // possibly beyond the total_decomps cap, or genuinely absent from the
  // candidate set R2L produced) -- worth a human look, not silently
  // dropped. Empty when every example was explained, or none were found
  // (llm_evidence_count == 0). Computed by a SEPARATE, final LLM call
  // (org.iutools.llm.GuessMeaningStructuredEngine.checkUnmatchedExamples),
  // given the word's complete final sense list -- not by whichever
  // guess_meaning stage happened to run last, which would only have seen
  // a partial candidate list. A 2026-09-11 investigation caught exactly
  // that inconsistency (a decomp attested with sense "we will be having a
  // meeting" in the same reply that flagged an example about a meeting as
  // unmatched) before this field was split out into its own check.
  "hansard_examples_not_matching_any_decomp": [
    { "inuktitut": "...", "english": "...", "date": "20100514" }
  ],

  // Which version of the guess_meaning approach produced the fields
  // above (see run_phase2_llm_pass.py's PHASE2_ALGO_VERSION and its
  // CHECKPOINTING doc section) -- lets a re-run tell "already processed,
  // skip" apart from "processed under an approach that's since changed,
  // worth redoing". Absent on a Phase-1-only record.
  "phase2_algo_version": "staged-v2-separate-unmatched-check",

  // The various dictionaries where a definition of the word can
  // be found.
  //
  // - source: name of the dictionary
  // - headword: the word under which the definition appears. If
  //   different from this record's word, this word was found
  //   inside the definition of that headword.
  //
  // For copyright reasons, we don't provide the actual
  // definition — just the reference.
  // If null, it means we haven't yet searched for definitions
  "definitions_found_in": [
    { "source": "Spalding", "headword": "tusaqtutit" },
    { "source": "Dorais", "headword": "tusaq" }
  ],

  // Prominent related words. These are words that share a
  // common stem (llm attested), and for which a definition is
  // available in a dictionary. For each related word:
  //
  // - word: the related word
  // - word_decomp: the related word's (llm-attested) decomp,
  //   from which the kinship to this word was established
  // - common_stem: the (llm-attested) common stem this word
  //   shares with the related word
  // - removed_morphemes: morphemes to remove from word_decomp
  //   to get common_stem
  // - added_morphemes: morphemes to add to common_stem to get
  //   this record's word
  //
  "prominent_related_words": [
    {
      "word": "tusaqtuq",
      "word_decomp": ["tusaq/1v", "juq/tv-ger-3s"],
      "common_stem": ["tusaq/1v"],
      "removed_morphemes": ["juq/tv-ger-3s"],
      "added_morphemes": ["jutit/tv-decl-2s"]
    }
  ]
}
```

### stems.jsonl schema