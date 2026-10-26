import com.android.build.api.variant.BuildConfigField

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.sophiel"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.sophiel"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A compressed .tflite cannot be memory-mapped and fails to load at runtime.
    androidResources {
        noCompress += "tflite"
        noCompress += "onnx" // spike: NudeNet models, copied to filesDir once
    }

    flavorDimensions += "net"
    productFlavors {
        create("offline") { dimension = "net" }
        create("remote") { dimension = "net" }
    }

    buildTypes {
        release {
            // Local release testing only (D32 gates, no debug overlay); real signing is M7.
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// RELAY_URL exists only in `remote` (D83). Debug reaches a local relay through adb reverse tcp:3000 (D84); release takes the
// Fly URL from the /wizard deploy (ticket 22): relayUrl in gradle.properties or -PrelayUrl=..., read only when remoteRelease builds.
androidComponents {
    onVariants(selector().withFlavor("net" to "remote")) { v ->
        val url = if (v.buildType == "release") providers.gradleProperty("relayUrl") else providers.provider { "http://localhost:3000" }
        v.buildConfigFields?.put("RELAY_URL", url.map { BuildConfigField("String", "\"$it\"", null) })
    }
}

dependencies {
    implementation(project(":safecore"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.onnxruntime.android) // NudeNet: the Precise preset (D35) and the D24 benchmark
    "remoteImplementation"(libs.okhttp)
    "remoteImplementation"(libs.zxing.core)
    "remoteImplementation"(libs.play.services.code.scanner)
    "remoteImplementation"(libs.androidx.fragment)
    testImplementation(libs.junit)
    "testRemoteImplementation"(libs.json) // org.json is a stub in the JVM android.jar; the app uses the platform one
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}