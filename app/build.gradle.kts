plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.marketdata.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.marketdata.app"
        minSdk = 24
        targetSdk = 36
        // Play rejects an upload whose versionCode was already used, so CI
        // stamps each build with the GitHub run number (always increasing).
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    // Upload-key signing for Play. CI supplies these from GitHub secrets
    // (see docs/PLAY_RELEASE.md). Locally / without secrets the release build
    // falls back to the debug key so sideload APKs keep working - but a
    // debug-signed build can NOT be uploaded to Play.
    val uploadKeystore: String? = System.getenv("UPLOAD_KEYSTORE_PATH")
    signingConfigs {
        if (uploadKeystore != null && file(uploadKeystore).exists()) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = System.getenv("UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
                ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.security.crypto)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.prefs)
    implementation(libs.documentfile)

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
