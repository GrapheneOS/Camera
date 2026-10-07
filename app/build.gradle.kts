import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val keystorePropertiesFile = rootProject.file("keystore.properties")
val useKeystoreProperties = keystorePropertiesFile.canRead()
val keystoreProperties = Properties()
if (useKeystoreProperties) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.screenshot)
}

detekt {
    basePath.set(rootDir)
    baseline = file("detekt-baseline.xml")
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    ignoredBuildTypes = listOf("release")
    parallel = true
}

// detekt's classpath convention is the compilation's dependencies and nothing else, so BuildConfig
// and androidxc/ resolve to nothing and every type-aware rule goes quiet instead of reporting. A
// Gradle convention cannot be appended to: `from` would discard it, hence `setFrom` with both.
fun addOwnClassesToDetektClasspath(
    classpath: ConfigurableFileCollection,
    variantName: String,
) {
    classpath.setFrom(
        tasks.named<KotlinJvmCompile>("compile${variantName}Kotlin").map { it.libraries },
        tasks.named("compile${variantName}JavaWithJavac").map { it.outputs.files },
    )
}

// Only the variants `check` gates on below. The plugin's other detekt tasks analyse a source set
// at a time without types and have no compilation to take a classpath from.
listOf("Debug", "DebugUnitTest", "DebugAndroidTest", "DebugScreenshotTest").forEach { variantName ->
    tasks
        .withType<Detekt>()
        .matching { it.name == "detekt$variantName" }
        .configureEach {
            addOwnClassesToDetektClasspath(classpath, variantName)
        }

    tasks
        .withType<DetektCreateBaselineTask>()
        .matching { it.name == "detektBaseline$variantName" }
        .configureEach {
            addOwnClassesToDetektClasspath(classpath, variantName)
        }
}

// The aggregate `detekt` task analyses every source set at once without type resolution, so it
// cannot see what the type-aware rules exist for. The debug variants cover the same sources with
// types, so `check` gates on those and the aggregate stays off.
tasks.named("check") {
    dependsOn(
        tasks.named("detektDebug"),
        tasks.named("detektDebugUnitTest"),
        tasks.named("detektDebugAndroidTest"),
        tasks.named("detektDebugScreenshotTest"),
        tasks.named("validateDebugScreenshotTest"),
    )
}

tasks.named("detekt") {
    enabled = false
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
        applicationId = "app.grapheneos.camera"
        minSdk = 29
        targetSdk = 37
        versionCode = 94
        versionName = versionCode.toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    buildTypes {
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (useKeystoreProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
            resValue("string", "app_name", "Camera")
        }

        getByName("debug") {
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Camera d")
            // isDebuggable = false
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
        compose = true
        resValues = true
    }

    androidResources {
        localeFilters += listOf("en")
    }

    testOptions {
        unitTests {
            // Robolectric builds its application under test from the merged manifest and
            // resources; without this it cannot start one.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // region AndroidX & Views
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    // endregion

    // region Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // endregion

    // region Lifecycle & ViewModel
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // endregion

    // region Kotlin
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    // endregion

    // region Storage
    implementation(libs.androidx.datastore)
    // endregion

    // region Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // endregion

    // region Camera & QR
    implementation(libs.bundles.camerax)
    implementation(libs.zxing.core)
    // endregion

    // region Unit tests
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    // endregion

    // region Instrumented tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.mockk.android)
    // endregion

    // region Screenshot tests
    screenshotTestImplementation(platform(libs.androidx.compose.bom))
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
    // endregion
}
