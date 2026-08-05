import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "se.apothictech.eutherping.crypto.libsignal"
    compileSdk = 36

    defaultConfig { minSdk = 28 }

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
    api(project(":crypto-api"))
    implementation("org.signal:libsignal-android:0.99.4")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
}
