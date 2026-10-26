// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9+ has built-in Kotlin support: no separate org.jetbrains.kotlin.android plugin
// needed or wanted (applying it fails the build — see docs/adr/0001-toolchain-and-di.md).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply false
}
