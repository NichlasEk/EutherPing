plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val cryptoProbeAbi = providers.gradleProperty("cryptoProbeAbi").orElse("arm64-v8a")

android {
    namespace = "se.apothictech.eutherping.crypto.probe"
    compileSdk = 36

    defaultConfig {
        applicationId = "se.apothictech.eutherping.crypto.probe"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.99.4-probe"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += cryptoProbeAbi.get() }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging.resources.excludes += setOf(
        "**/libsignal_jni*.dylib",
        "**/signal_jni*.dll",
    )
    packaging.jniLibs.excludes += "**/libsignal_jni_testing.so"
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(project(":crypto-libsignal"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
