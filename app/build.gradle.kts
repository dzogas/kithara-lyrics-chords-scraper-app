@file:Suppress("DEPRECATION")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.kithara"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.kithara"
        minSdk = 30
        targetSdk = 36
        versionCode = 10
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// In your app/build.gradle.kts file

dependencies {
    // --- Core and Utility Dependencies ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material) // For XML View compatibility
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Jsoup for HTML parsing
    implementation(libs.jsoup)

    // Coroutines for async tasks
    implementation(libs.kotlinx.coroutines.android)

    // --- Jetpack Compose Dependencies ---
    // CORRECTED: The Compose BOM must be declared here using platform()
    // This manages the versions for all subsequent Compose libraries.
    implementation(platform(libs.androidx.compose.bom))

    // Now, these dependencies will work because the BOM provides their versions.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3) // This will now resolve correctly


    // --- Local Unit Tests (for src/test/java) ---
    // CORRECTED: Only the standard JUnit is needed for local tests.
    testImplementation(libs.junit)

    // --- Instrumented Tests (for src/androidTest/java) ---
    // This uses the AndroidX versions of JUnit and Espresso
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // The BOM is also needed for instrumented Compose tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // --- Debug-only Dependencies for Compose Tooling ---
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
