plugins {
    alias(libs.plugins.android.application) apply false
    // No kotlin-android plugin: AGP 9.0+ provides built-in Kotlin support.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
