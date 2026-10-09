// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Add
    alias(libs.plugins.plugin.ksp) apply false
    alias(libs.plugins.plugin.kotlin.jvm) apply false
    alias(libs.plugins.plugin.maven.publish) apply false
}