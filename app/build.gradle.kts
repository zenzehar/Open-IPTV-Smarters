plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.opentv"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.opentv"
        minSdk = 23
        targetSdk = 35
        versionCode = 27
        versionName = "0.12.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("upload") {
            // Populated from environment variables in CI. Absent locally, which is why the
            // release build type falls back to the debug key below.
            val storePath = providers.environmentVariable("KEYSTORE_PATH").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // A release without a configured permanent key is intentionally unsigned.
            // The release workflow rejects absent MYIPTV secrets before packaging.
            if (providers.environmentVariable("KEYSTORE_PATH").orNull != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            // Optional fixed signing identity; loaded through GitHub Actions secrets.
            // Without a stable signing secret Android will reject cross-run upgrades.
            if (providers.environmentVariable("KEYSTORE_PATH").orNull != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
        // A data-safe smoke-test install: never replaces the user's existing .debug app.
        // Temporary QA keys are acceptable because no existing app data is migrated.
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    // Broader container/codec support for IPTV VOD files Media3 cannot parse (LGPL LibVLC).
    implementation("org.videolan.android:libvlc-all:3.6.1")

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    // Storage Access Framework helpers - write recordings to a plugged-in USB / external
    // drive via a user-granted tree URI, with no storage permission.
    implementation(libs.androidx.documentfile)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    // SMB/CIFS client for recording to a NAS (Synology etc.) and playing those recordings
    // back in-app over the network. Pure-Java SMB2/3, no native bits.
    implementation("com.hierynomus:smbj:0.12.2")

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.ext.junit)
}
