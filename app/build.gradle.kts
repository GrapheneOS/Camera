plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
}

android {
    compileSdk = 31

    defaultConfig {
        applicationId = "app.grapheneos.camera"
        minSdk = 26
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")

    implementation("androidx.camera:camera-core:1.1.0-alpha09")
    implementation("androidx.camera:camera-camera2:1.1.0-alpha09")
    implementation("androidx.camera:camera-lifecycle:1.1.0-alpha09")
    implementation("androidx.camera:camera-view:1.0.0-alpha28")
    implementation("androidx.camera:camera-extensions:1.0.0-alpha28")

    implementation("com.google.zxing:core:3.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0")
}