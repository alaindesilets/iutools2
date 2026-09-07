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
        // Source of truth for the linguistic CSVs is
        // data/grammar/linguistic-data/, not core/'s own resource tree --
        // see that directory's README. They ride the runtime classpath as
        // resources; LinguisticDataCSV.kt loads them from there.
        resources.srcDir("../data/grammar/linguistic-data")
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
}

kotlin {
    jvmToolchain(21)
}
