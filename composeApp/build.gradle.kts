import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Guess Meaning spike (llm-guess-meaning-spike branch only): the Anthropic
// API key lives in local.properties (already gitignored, already used for
// sdk.dir) rather than in source -- read here and exposed to the app via a
// generated BuildConfig field, never as a string literal in Kotlin source.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "org.iutools.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.iutools.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "ANTHROPIC_API_KEY",
            "\"${localProperties.getProperty("anthropicApiKey", "")}\"",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // The Apache HttpComponents jars pulled in transitively by the
    // Anthropic SDK each bundle their own identical META-INF/DEPENDENCIES
    // (a plain-text license/dependency listing, unused at runtime) --
    // AGP refuses to package duplicates by default, so drop it explicitly
    // rather than picking one arbitrarily.
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

kotlin {
    // Robolectric (used by the composeApp test suite) can't yet parse
    // class files from very new JDKs (confirmed: fails with "Unsupported
    // class file major version 70" under JDK 26) -- pin the toolchain so
    // tests run on a JDK Robolectric actually supports, regardless of
    // whatever JDK is the machine's default. Matches :cli's toolchain.
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))

    // Guess Meaning spike: Anthropic's official Java SDK (Kotlin uses the
    // Java SDK -- there is no separate Kotlin SDK).
    implementation("com.anthropic:anthropic-java:2.34.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Robolectric: simulates the Android framework on the JVM (no
    // emulator/device needed) so unit tests can resolve real Android
    // resources (values/ vs values-fr/) under a chosen Locale.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("junit:junit:4.13.2")
}
