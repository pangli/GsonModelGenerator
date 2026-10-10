import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.plugin.kotlin.jvm)
    alias(libs.plugins.plugin.maven.publish)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":wire-annotation"))
    compileOnly(kotlin("stdlib"))
    compileOnly(libs.symbol.processing.api)
    testImplementation(kotlin("stdlib"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}
