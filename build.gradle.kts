plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
}

// ADR-0004 Phase B1: Kover aggregates coverage from the :app module.
// No threshold enforcement yet (see ADR §"NOT adopting"); track first.
dependencies {
    kover(project(":app"))
}

kover {
    reports {
        total {
            html { onCheck = false }
            xml { onCheck = false }
            filters {
                excludes {
                    classes(
                        "*Activity*",
                        "*Fragment*",
                        "*_Factory*",
                        "*_HiltModules*",
                        "*ComposableSingletons*",
                        "*Preview*",
                    )
                    annotatedBy("androidx.compose.runtime.Composable")
                }
            }
        }
    }
}

// Captured at root configuration time so they're usable inside
// subprojects { } where the typed `libs` accessor isn't visible.
val ktlintVersion = libs.versions.ktlint.get()
val detektVersion = libs.versions.detekt.get()

// Root project Spotless: format and check root build.gradle.kts +
// settings.gradle.kts. Subprojects get their own Spotless block below
// for src/**/*.kt and their own *.gradle.kts.
spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion).editorConfigOverride(
            mapOf(
                "ktlint_standard_function-naming" to "disabled",
                "ktlint_standard_property-naming" to "disabled",
                "ktlint_standard_filename" to "disabled",
            ),
        )
    }
}

// Compose-friendly ktlint overrides. Disabled inline because Spotless's
// bundled ktlint does not always pick up .editorconfig settings reliably.
// See ADR-0004 Phase A1 rationale.
val composeKtlintOverrides =
    mapOf(
        "ktlint_standard_function-naming" to "disabled",
        "ktlint_standard_property-naming" to "disabled",
        "ktlint_standard_filename" to "disabled",
    )

subprojects {
    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**", "**/generated/**")
            ktlint(ktlintVersion).editorConfigOverride(composeKtlintOverrides)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion).editorConfigOverride(composeKtlintOverrides)
        }
    }

    apply(plugin = "io.gitlab.arturbosch.detekt")
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        toolVersion = detektVersion
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = file("$rootDir/config/detekt/baseline.xml")
        parallel = true
    }
}
