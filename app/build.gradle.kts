plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.kithara"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.kithara"
        minSdk = 29
        targetSdk = 34
        versionCode = 9
        versionName = "0.0.9"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Jsoup για HTML parsing
    implementation("org.jsoup:jsoup:1.17.2")

    // Coroutines για async εργασίες
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Unit testing

}