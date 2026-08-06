plugins {
    kotlin("jvm") version "2.4.10"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Real dependency for the decompose-results LRU cache (used as-is by the
    // original Java code) — a tiny, non-core optimization not worth hand-rolling.
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    testImplementation(kotlin("test"))
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
