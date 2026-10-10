plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    //ksp
    alias(libs.plugins.plugin.ksp)
}

ksp {
    arg("model.classPrefix", "Sparrow")
    arg("model.paramSuffix", "BySparrow")
    arg("model.nameRule", "base64")
    arg("model.xorKey", "bd")
    arg(
        "model.dict",
        layout.projectDirectory.file("wire-names.properties").asFile.absolutePath,
    )
    // Prefer an absolute path under build/ so the mapping is not packaged into the APK.
    // "true" writes to generated KSP resources (can be packaged — avoid for release).
    arg(
        "model.mappingFile",
        layout.buildDirectory.file("outputs/model-wire-mapping.json").get().asFile.absolutePath,
    )
    arg("path.nameRule", "dict")
    arg(
        "path.dict",
        layout.projectDirectory.file("path-names.properties").asFile.absolutePath,
    )
    arg(
        "path.mappingFile",
        layout.buildDirectory.file("outputs/path-mapping.json").get().asFile.absolutePath,
    )
}

tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
    inputs.file(layout.projectDirectory.file("wire-names.properties"))
    inputs.file(layout.projectDirectory.file("path-names.properties"))
}
android {
    namespace = "com.gson.model.generator"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gson.model.generator"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = file("../gsonmodel.jks")
            storePassword = "gsonmodel"
            keyAlias = "gsonmodel"
            keyPassword = "gsonmodel"
        }
    }
    buildTypes {
        debug {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    //Add
    implementation(libs.gson)
    implementation(project(":model-annotation"))
    compileOnly(project(":model-encoder"))
    ksp(project(":model-compiler"))
    ksp(project(":model-encoder"))
//    implementation("io.github.pangli:model-annotation:1.0.1")
//    ksp("io.github.pangli:model-compiler:1.0.1")
}