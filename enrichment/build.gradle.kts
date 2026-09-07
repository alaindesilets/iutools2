// Concrete implementations of the word-enrichment interfaces declared in
// :core -- the parts that actually reach the outside world for the "Guess
// Meaning" and dictionary-lookup features: the LLM call, the live web
// dictionary fetchers, the parsed local dictionaries. :core holds the pure
// logic and the interfaces; this module holds the I/O.
//
// A plain JVM library (not KMP, no Android) so it can be shared by
// :composeApp and by any headless JVM tool that wants the same word info a
// user gets on screen (e.g. a future CLI). Android-specific mechanics
// (asset loading, key storage) are deliberately kept out -- callers inject
// what they need.
plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":core"))

    // Anthropic's official Java SDK -- Kotlin consumes the Java SDK, there
    // is no separate Kotlin one. Same coordinate :composeApp used while
    // this code lived there.
    implementation("com.anthropic:anthropic-java:2.34.0")
    // withContext(Dispatchers.IO) around the blocking SDK call. :core only
    // uses the `suspend` language feature, not the library; the concrete
    // client here needs the real thing.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
