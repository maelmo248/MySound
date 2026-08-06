// Ajoute ce bloc dans ton settings.gradle.kts existant (dependencyResolutionManagement > repositories)
// si jitpack.io n'y est pas déjà :
//
// dependencyResolutionManagement {
//     repositories {
//         google()
//         mavenCentral()
//         maven { url = uri("https://jitpack.io") }
//     }
// }

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
        maven { url = uri("https://jitpack.io") } // <-- LIGNE À AJOUTER ICI
    }
}

rootProject.name = "MySound"
include(":app")
