# `data/grammar/linguistic-data/` -- linguistic-rules CSVs

The CSV tables (roots, suffixes, endings, demonstratives, pronouns, ...)
that `:core`'s `LinguisticData` loads to build its constraint-based
morphological model. Moved here from `core/src/commonMain/resources/` --
`:core`'s Gradle build maps this directory onto its classpath via
`commonMain`'s `resources.srcDir`, so nothing else about how the app loads
them changed.

Never hand-edit these by reformatting or reordering -- if a tool touches
them, verify the result byte-for-byte against the original (see AGENTS.md's
"Preserving data integrity").
