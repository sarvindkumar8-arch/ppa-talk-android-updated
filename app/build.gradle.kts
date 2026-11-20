plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val customKeystoreFile = file("ppa-talk-debug-new.jks")
val customStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val customKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val customKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val hasCustomSigning = customKeystoreFile.exists() &&
    !customStorePassword.isNullOrBlank() &&
    !customKeyAlias.isNullOrBlank() &&
    !customKeyPassword.isNullOrBlank()

android {
    namespace = "com.ppatalk.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ppatalk.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        if (hasCustomSigning) {
            create("custom") {
                storeFile = customKeystoreFile
                storePassword = customStorePassword
                keyAlias = customKeyAlias
                keyPassword = customKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasCustomSigning) {
                signingConfig = signingConfigs.getByName("custom")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Keep Kotlin stdlib and its JDK variants on the same version.
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.21"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    // Restore the previously used WebRTC media engine for voice and video calls.
    implementation("io.github.webrtc-sdk:android:150.7871.04")
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
}
