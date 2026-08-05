import org.gradle.api.tasks.Exec

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val probeAbi = providers.gradleProperty("cryptoVodoAbi").orElse("arm64-v8a")
val rustOutput = layout.buildDirectory.dir("generated/rustJni")
val nativeManifest = layout.projectDirectory.file("native/Cargo.toml")

android {
    namespace = "se.apothictech.eutherping.crypto.vodozemac"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += probeAbi.get() }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    sourceSets.getByName("main").jniLibs.srcDir(rustOutput)
}

val buildRustNative by tasks.registering(Exec::class) {
    val outputDirectory = rustOutput.get().asFile
    inputs.file(nativeManifest)
    inputs.file(layout.projectDirectory.file("native/Cargo.lock"))
    inputs.dir(layout.projectDirectory.dir("native/src"))
    inputs.property("abi", probeAbi)
    outputs.dir(outputDirectory)

    doFirst {
        delete(outputDirectory)
        outputDirectory.mkdirs()
    }
    workingDir(layout.projectDirectory.dir("native"))
    commandLine(
        "cargo",
        "ndk",
        "-t",
        probeAbi.get(),
        "-o",
        outputDirectory.absolutePath,
        "build",
        "--release",
        "--locked",
    )
    environment(
        "CARGO_TARGET_DIR",
        layout.buildDirectory.dir("rustTarget").get().asFile.absolutePath,
    )
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
        dependsOn(buildRustNative)
    }
}

dependencies {
    api(project(":crypto-api"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
