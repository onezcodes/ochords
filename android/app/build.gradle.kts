import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Per-machine settings live in local.properties (gitignored), like iOS's project.yml configs:
//   API_BASE_URL   the O Chords API (Debug default: the Laravel dev server reached from the emulator as 10.0.2.2);
//                  empty = the app runs on demo data.
//   ADMOB_APP_ID / ADMOB_BANNER / ADMOB_INTERSTITIAL / ADMOB_REWARDED   AdMob IDs; Debug falls back to Google's
//                  public test units. Empty app ID = the app's own placeholder cards, SDK not started.
//   GOOGLE_WEB_CLIENT_ID   the *web* OAuth client ID for Google sign-in (the ID token's audience the API checks).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun prop(name: String, default: String = ""): String =
    (findProperty(name) as String?) ?: localProperties.getProperty(name, default)

android {
    namespace = "com.onezcodes.ochords"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.onezcodes.ochords"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${prop("GOOGLE_WEB_CLIENT_ID")}\"")
        resValue("string", "admob_app_id", prop("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713"))
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${prop("API_BASE_URL", "http://10.0.2.2:8000/api/v1")}\"")
            buildConfigField("String", "ADMOB_APP_ID", "\"${prop("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")}\"")
            buildConfigField("String", "ADMOB_BANNER", "\"${prop("ADMOB_BANNER", "ca-app-pub-3940256099942544/9214589741")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL", "\"${prop("ADMOB_INTERSTITIAL", "ca-app-pub-3940256099942544/1033173712")}\"")
            buildConfigField("String", "ADMOB_REWARDED", "\"${prop("ADMOB_REWARDED", "ca-app-pub-3940256099942544/5224354917")}\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"${prop("API_BASE_URL")}\"")
            buildConfigField("String", "ADMOB_APP_ID", "\"${prop("ADMOB_APP_ID")}\"")
            buildConfigField("String", "ADMOB_BANNER", "\"${prop("ADMOB_BANNER")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL", "\"${prop("ADMOB_INTERSTITIAL")}\"")
            buildConfigField("String", "ADMOB_REWARDED", "\"${prop("ADMOB_REWARDED")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES")
    }
}

// A release must never ship demo data or plain-HTTP API traffic.
tasks.matching { it.name.startsWith("assembleRelease") || it.name.startsWith("bundleRelease") }.configureEach {
    doFirst {
        check(prop("API_BASE_URL").startsWith("https://")) { "Set an https API_BASE_URL in local.properties for a release build." }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.exifinterface)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    // Frosted glass (the design's "ultraThinMaterial").
    implementation(libs.haze)
    implementation(libs.haze.materials)
    // Free plan: AdMob banner, interstitial, rewarded.
    implementation(libs.play.services.ads)
    // O Chords Pro subscriptions.
    implementation(libs.billing.ktx)
    // Sign in with Google (Credential Manager).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    // Scan a sheet: camera + on-device OCR (Latin); head-nod page turns: face detection.
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.text)
    implementation(libs.mlkit.face)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
