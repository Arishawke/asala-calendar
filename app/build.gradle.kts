import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

// Release signing credentials. Local builds read keystore.properties from
// the repo root (gitignored; see keystore.properties.example). CI builds
// read SIGNING_* environment variables. If neither is available, the
// release build type produces an unsigned APK - adb will refuse to
// install it, which is the right safety default.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }
val hasSigningEnv: Boolean =
    listOf("SIGNING_STORE_FILE", "SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD")
        .all { providers.environmentVariable(it).orNull != null }
val hasSigningConfig: Boolean = keystorePropertiesFile.exists() || hasSigningEnv

android {
    namespace = "com.arishawke.asala.calendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arishawke.asala.calendar"
        minSdk = 28
        targetSdk = 36
        versionCode = 25
        versionName = "0.22.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        // Required to expose BuildConfig.DEBUG so we can plant Timber's
        // DebugTree in debug builds and stay silent in release.
        buildConfig = true
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                if (keystorePropertiesFile.exists()) {
                    storeFile = file(keystoreProperties.getProperty("storeFile"))
                    storePassword = keystoreProperties.getProperty("storePassword")
                    keyAlias = keystoreProperties.getProperty("keyAlias")
                    keyPassword = keystoreProperties.getProperty("keyPassword")
                } else {
                    storeFile = file(providers.environmentVariable("SIGNING_STORE_FILE").get())
                    storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
                    keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                    keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
                }
            }
        }
    }

    buildTypes {
        debug {
            // separate package + label so a local debug build installs alongside
            // the Play-signed release instead of colliding with it.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 dead-code-eliminates unused code from Compose / Material3 /
            // androidx and tree-shakes resources. With these off the release
            // APK is ~25 MB; with both on it drops to single digits.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        baseline = file("lint-baseline.xml")
        // These checks fire based on the build environment's installed SDK
        // and on Maven metadata fetched at build time, so they drift between
        // a developer machine and CI runners that auto-install newer SDKs.
        // Dependency hygiene belongs to Dependabot or a manual cadence, not
        // a build gate that breaks on a remote upstream version bump.
        disable += setOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion")
        // ADR-0004: a11y + i18n + rtl gates promoted from warning to error.
        // MissingTranslation is already error by default; do not re-promote.
        error += setOf("HardcodedText", "ContentDescription", "RtlHardcoded")
    }
}

// ADR-0004: Compose Compiler stability reports for recompose visibility.
// Plural ListProperty form per Kotlin 2.0.20+ / Compose Compiler 1.5.12+;
// singular `stabilityConfigurationFile` is deprecated.
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("config/compose/stability_config.conf"),
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.kizitonwose.calendar.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.skydoves.colorpicker.compose)
    implementation(libs.jakewharton.timber)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // ADR-0004: Compose-specific lint checks (Slack rules). No plugin id,
    // no DSL block; loaded via Android lint's lintChecks configuration.
    lintChecks(libs.compose.lint.checks)

    testImplementation(libs.junit)

    // instrumented tests: the recurrence/exception write paths only fail
    // against the real CalendarProvider, which JVM/Robolectric cannot model.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
