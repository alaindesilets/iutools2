rootProject.name = "iutools-mobile"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Lets Gradle auto-download a matching JDK toolchain (e.g. 21, needed
    // by composeApp's jvmToolchain) when one isn't already installed
    // locally, instead of just failing with "no matching toolchain found".
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":core", ":cli", ":composeApp", ":fst")

// The entry-point modules live under apps/ (core / apps / data grouping --
// see doc/dev/plans/module-architecture-migration.md). Their Gradle paths
// stay ":cli" / ":composeApp" -- only the directory moved -- so
// `./gradlew :cli:test`, IDE run configs and the android-studio-run-all-tests
// labels are unaffected.
project(":cli").projectDir = file("apps/cli")
project(":composeApp").projectDir = file("apps/composeApp")
