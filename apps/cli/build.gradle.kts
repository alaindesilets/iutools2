plugins {
    kotlin("jvm")
    application
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
