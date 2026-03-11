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
        // Shizuku için JitPack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Prozessor"
include(":app")
