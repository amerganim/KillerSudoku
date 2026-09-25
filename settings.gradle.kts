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
    }
}

rootProject.name = "KillerSudoku"

// :engine is pure Kotlin/JVM and shaped like the future :core:puzzle
// (docs/puzzle-engine-core.md) so it can be lifted out without rewriting.
include(":engine")
include(":app")
