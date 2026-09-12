import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// --- Release keystore resolution -------------------------------------------------
// Priority: Gradle project properties (-P... or gradle.properties)  >  environment
// variables (handy for GitHub Actions secrets)  >  nothing found -> fall back to
// the debug keystore so `assembleRelease` never hard-fails when a signer isn't
// configured (e.g. on a contributor's machine or a CI dry run).
fun prop(name: String): String? =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseStoreFilePath = prop("RELEASE_STORE_FILE")
val releaseStorePassword = prop("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = prop("RELEASE_KEY_ALIAS")
val releaseKeyPassword = prop("RELEASE_KEY_PASSWORD")

val hasReleaseSigning = releaseStoreFilePath != null &&
    file(releaseStoreFilePath).exists() &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "dev.clipkeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.clipkeyboard"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Fallback: if no real release key was supplied, sign with the debug
            // key so CI/local `assembleRelease` still produces an installable APK.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("ClipKeyboard: no RELEASE_STORE_FILE/PASSWORD/ALIAS found -> signing release build with the DEBUG key. Set gradle properties or env vars for a real release.")
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
}
