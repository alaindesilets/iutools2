plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core"))
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
}
