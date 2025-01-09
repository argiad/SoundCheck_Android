plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.steegler.soundcheck"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.steegler.soundcheck"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true // Enable Compose
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // Specify compatible Compose compiler
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)

    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.5.3")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.ui:ui-tooling:1.5.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.3")
    implementation("androidx.activity:activity-compose:1.8.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2") // For lifecycle scope
    implementation("io.ktor:ktor-client-android:3.0.0")
    implementation("io.ktor:ktor-client-core:3.0.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling:1.5.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.5.3")
}