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

        // First real use of the androidTest source set (see
        // AppSettingsEncryptionInstrumentedTest.kt) -- needed for anything
        // that depends on real Android framework behavior Robolectric can't
        // simulate, like the Keystore-backed encryption in AppSettings.kt.
        // Runs on a real device/emulator only (see AGENTS.md's "Division of
        // labor" -- this AI sandbox has no emulator to run it itself).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Encrypts the user's own Claude.ai API key at rest (see AppSettings.kt)
    // -- an AES256-GCM value wrapped by a key held in the Android Keystore,
    // rather than the plain-text SharedPreferences used for non-secret
    // settings (language, display script). Still tagged 1.1.0-alpha by
    // Google despite years of production use -- the MasterKey-based API it
    // exposes (replacing the older, now-deprecated MasterKeys helper) is
    // what every current guide recommends; the 1.0.0 "stable" release only
    // has the deprecated API.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // local-llm-spike branch: on-device inference for Guess Meaning, as an
    // alternative to the Claude API call above. LiteRT-LM, not MediaPipe's
    // tasks-genai -- the latter is Google's own recommendation as of this
    // writing, since MediaPipe LLM Inference is now maintenance-only. Native
    // Kotlin API (Engine/Conversation, Flow-based streaming), Gemma models
    // distributed as .litertlm files (see LocalLlmEngine.kt).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
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

    // Compose UI testing (createComposeRule()) -- also runs under Robolectric,
    // no emulator needed, for the few tests that need to check what's actually
    // rendered (not just that a string resource resolves correctly).
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // On-device instrumented tests (androidTest) -- for real Android
    // framework behavior Robolectric can't simulate, e.g. the Keystore-
    // backed encryption in AppSettings.kt (see
    // AppSettingsEncryptionInstrumentedTest.kt). junit:junit is the same
    // plain JUnit4 API used by the unit test suite above; androidx.test's
    // runner/ext.junit are what let AndroidJUnitRunner (see
    // testInstrumentationRunner above) actually execute it on-device.
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
