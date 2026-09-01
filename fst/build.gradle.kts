// The finite-state (HFST) morphological analyzer, as a plain JVM library so
// it can be consumed by both :cli (JVM, for the accuracy test) and
// :composeApp (Android runtime). It bundles the vendored pure-Java
// optimized-lookup reader (src/main/java/net/sf/hfst/ -- see its VENDORED.md)
// which the KMP :core module's Android compilation can't build (no Java
// source support there), and MorphologicalAnalyzer_FST on top of it.
plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":core"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
