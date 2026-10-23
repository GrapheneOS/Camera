import java.security.MessageDigest
import java.io.FileInputStream
import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val useKeystoreProperties = keystorePropertiesFile.canRead()
val keystoreProperties = Properties()
if (useKeystoreProperties) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

android {
    if (useKeystoreProperties) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                enableV4Signing = true
            }

            create("play") {
                storeFile = rootProject.file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["uploadKeyAlias"] as String
                keyPassword = keystoreProperties["uploadKeyPassword"] as String
            }
        }
    }

    compileSdk = 37
    buildToolsVersion = "37.0.0"
    ndkVersion = "29.0.14206865"

    namespace = "app.grapheneos.camera"

    defaultConfig {
        applicationId = "app.foldcamera"
        minSdk = 29
        targetSdk = 37
        versionCode = 150
        versionName = "143.3.3"

        // Bundled native dependencies target ARM64.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (useKeystoreProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
            resValue("string", "app_name", "Fold Camera")
        }

        getByName("debug") {
            resValue("string", "app_name", "Fold Camera")
        }

        create("play") {
            initWith(getByName("release"))
            applicationIdSuffix = ".play"
            if (useKeystoreProperties) {
                signingConfig = signingConfigs.getByName("play")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }

    androidResources {
        localeFilters += listOf("en")
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)

    implementation(libs.bundles.camerax)
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.window:window-java:1.5.1")

    implementation(libs.zxing.core)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    // Official OpenCV Android AAR: native SIFT, matching and image processing.
    implementation("org.opencv:opencv:4.13.0")

}

// Require the bundled, verified model weights for every application build.
// Portrait inference runs entirely on the device.
val portraitModels = mapOf(
    "modnet_photographic_portrait_matting.onnx" to "07c308cf0fc7e6e8b2065a12ed7fc07e1de8febb7dc7839d7b7f15dd66584df9",
    "depth_anything_v2_small_quantized.onnx" to "fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d",
)
val verifyPortraitModels = tasks.register("verifyPortraitModels") {
    doLast {
        for ((fileName, expectedSha) in portraitModels) {
            val model = file("src/main/assets/portrait/$fileName")
            check(model.isFile) { "Missing bundled portrait model: $fileName" }
            val digest = MessageDigest.getInstance("SHA-256")
            model.inputStream().buffered().use { stream ->
                val bytes = ByteArray(1024 * 1024)
                while (true) {
                    val count = stream.read(bytes)
                    if (count < 0) break
                    digest.update(bytes, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual == expectedSha) { "Model checksum mismatch: $fileName" }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(verifyPortraitModels)
}
