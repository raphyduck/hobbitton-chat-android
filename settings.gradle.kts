pluginManagement {
    includeBuild("build-logic")
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
        maven { url = uri("https://jitpack.io") }
        // sherpa-onnx's Android library (11/11/2026, the dictation heard on the phone): published
        // only as a release asset of the project's GitHub repository, pinned by version, and the
        // only module this repository serves.
        ivy {
            name = "sherpa-onnx releases"
            url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download")
            patternLayout { artifact("v[revision]/[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.k2fsa.sherpa.onnx", "sherpa-onnx") }
        }
    }
}

rootProject.name = "Eva"

include(":app")
include(":core:common")
include(":core:logging")
include(":core:model")
include(":core:network")
include(":core:data")
include(":core:ui")
include(":feature:auth")
include(":feature:tasks")
include(":shared")
