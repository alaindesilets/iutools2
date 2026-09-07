// The morphological analyzer plus reusable domain logic (org.iutools.llm,
// the Guess Meaning enrichment). A plain Kotlin/JVM library: every target
// -- the CLI, the Android app, and a planned Compose Desktop app -- is JVM,
// so there is no Kotlin Multiplatform here (see
// doc/dev/plans/drop-kmp-core.md). Consumed by :cli, :fst (JVM) and
// :composeApp (Android dexes the plain jar, same as it does :fst).
plugins {
    `java-library`
    kotlin("jvm")
}

sourceSets {
    main {
        resources {
            // Source of truth for the linguistic CSVs and the parsed
            // Spalding dictionary is data/ (see those directories' READMEs).
            // Gradle maps them onto the classpath root so they load as flat
            // resources -- LinguisticDataCSV.kt, SpaldingDictionary.kt.
            srcDir("../data/grammar/linguistic-data")
            srcDir("../data/lexicon")
            // Only the data files ride the classpath -- not the per-directory
            // READMEs (which would also collide at the classpath root), the
            // generator script, or the multi-100k-line word-decomposition
            // dataset that also lives under data/lexicon/. Nothing in :core
            // reads any of those, and they must not bloat the jar (or, via
            // :composeApp, the APK).
            exclude("README.md", "parse_spalding_dictionary.py", "decompositions/**")
        }
    }
}

dependencies {
    // The LLM call (LlmClient_Anthropic) -- Kotlin consumes the Java SDK,
    // there is no separate Kotlin one. `implementation`, not `api`: no
    // consumer of :core touches an com.anthropic.* type, they only see the
    // vendor-neutral LlmClient interface.
    implementation("com.anthropic:anthropic-java:2.34.0")
    // withContext(Dispatchers.IO) around that blocking SDK call. The rest
    // of :core uses only the `suspend` language feature, not the library.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    // Parsing the embedded spalding.json (SpaldingDictionary). The real
    // JVM org.json artifact -- Android ships a non-functional stub of the
    // same package, which is why this used to need Robolectric; :core is
    // plain JVM so the real one just works.
    implementation("org.json:json:20240303")
}

kotlin {
    jvmToolchain(21)
}
