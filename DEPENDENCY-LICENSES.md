# Third-party dependencies bundled in the distributed package

Unlike the original `iutools-core` (a large multi-feature suite with many
dependencies — spellchecker, Elasticsearch, PDF handling, etc., see its own
`DEPENDENCY-LICENSES.md`), this port only includes the morphological analyzer
core, so the dependency footprint actually bundled into any distributed
package (jpackage/installDist output) is small:

- **Kotlin standard library** (`kotlin-stdlib`) — Apache License 2.0
- **Caffeine** (`com.github.ben-manes.caffeine:caffeine`) — Apache License 2.0
- Transitive: `checker-qual`, `error_prone_annotations` (Google), `annotations`
  (JetBrains) — all Apache License 2.0

No copyleft (GPL/LGPL) dependencies are bundled in this port.
