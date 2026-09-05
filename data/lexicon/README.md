# `data/lexicon/` -- lexicographic data

- **Spalding dictionary** (`spalding.json`, 8373 headwords) -- parsed once
  from the source page (inuktitutcomputing.ca, a single static HTML page)
  by `parse_spalding_dictionary.py`, also here. The source will never
  change again (Spalding is deceased), so this script won't normally be
  re-run -- it's kept as provenance and in case a parsing bug is ever found
  and the JSON needs regenerating, not as an active build step.
  `composeApp/build.gradle.kts` merges this whole directory into the app's
  assets at build time -- no build-time copy, no duplicate committed to the
  repo, and unlike the FST's compiled transducer (a build artifact with no
  standalone use), this JSON has value on its own -- someone may just want
  the parsed dictionary -- so it stays here as the single, discoverable
  copy rather than living only inside `composeApp`. Loaded via
  `SpaldingDictionary.kt`. (This README and the generator script ride along
  into the APK's assets too, as a few harmless KB -- traded deliberately for
  a flat, easy-to-find path instead of nesting the JSON under its own
  `assets/` subfolder.)
- **The morpheme dictionary**: no separate data file -- it's a search over
  the linguistic CSVs already in `data/grammar/linguistic-data/`, not a
  distinct dataset.

See [`../README.md`](../README.md).
