// Root build file: declares plugin versions once via the version catalog.

// AGP 9 built-in Kotlin ships KGP 2.2.10; upgrade to Kotlin 2.4.21 and a
// matching KSP via the documented buildscript classpath override.
// https://developer.android.com/build/releases/agp-9-0-0-release-notes
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.detekt) apply false
}

// Modules whose TDD milestone has not landed yet have empty test source sets; AGP 9 fails
// unit-test tasks that discover no tests. Remove per module as suites are added (plan §5).
subprojects {
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests = false
    }
}
