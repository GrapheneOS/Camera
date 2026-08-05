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
listOf("Debug", "DebugUnitTest", "DebugAndroidTest").forEach { variantName ->
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
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)

    implementation(libs.bundles.camerax)

    implementation(libs.zxing.core)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)

    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
}
