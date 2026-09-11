plugins {
    kotlin("jvm")
    application
}

sourceSets {
    test {
        // Test-only fixture for ReferenceRerankerParityTest -- see
        // data/grammar/reference-reranker/README.md. The model itself
        // (reranker_model.json, in the same directory) ships in :core;
        // this fixture doesn't, so it's wired here rather than there.
        resources.srcDir("../../data/grammar/reference-reranker")
        resources.include("reranker_golden_fixture.json")
    }
}

dependencies {
    implementation(project(":core"))
    // runBlocking, to drive :core's suspend APIs from `main` (the --define
    // subcommand calls WordLookup) and from tests. :core depends on
    // coroutines-core only as `implementation` (for LlmClient_Anthropic /
    // WordLookup), so it isn't transitively on this module's classpath --
    // declare it here too.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(kotlin("test"))
    // For MorphologicalAnalyzer_FST__AccuracyTest.
    testImplementation(project(":fst"))
    // Parses reranker_golden_fixture.json directly in
    // ReferenceRerankerParityTest -- :core depends on jackson-module-kotlin
    // only as `implementation` (see that build file's comment), so it isn't
    // transitively on this module's test classpath.
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("org.iutools.morph.cli.MainKt")
}

tasks.test {
    useJUnitPlatform()

    // Forward opt-in flags from the Gradle invocation to the test JVM (they
    // are not passed on by default): AnalyzerSpeedComparisonTest's
    // benchmark flags, and MorphologicalAnalyzer__AccuracyTest's snapshot
    // dump mode.
    listOf("iutools.benchmark", "iutools.benchmark.words", "iutools.accuracy.dumpSnapshot").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}
