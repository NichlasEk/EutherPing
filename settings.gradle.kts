pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "SignalBuildArtifacts"
            url = uri("https://build-artifacts.signal.org/libraries/maven/")
            content { includeGroup("org.signal") }
        }
    }
}

rootProject.name = "EutherPing"
include(":app")
include(":baselineprofile")
include(":crypto-api")
include(":crypto-libsignal")
include(":crypto-probe-app")
include(":crypto-storage-android")
include(":crypto-vodozemac")
