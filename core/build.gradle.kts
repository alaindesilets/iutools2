import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    android {
        namespace = "org.iutools.core"
        compileSdk = 34
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain {
            // Source of truth for the linguistic CSVs is data/grammar/linguistic-data/,
            // not core/'s own resource tree -- see that directory's README.
            resources.srcDir("../data/grammar/linguistic-data")
        }
    }

}
