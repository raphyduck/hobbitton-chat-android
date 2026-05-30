plugins {
    id("librechat.kmp.library")
    id("librechat.kmp.koin")
}

// Short commit the build was cut from, baked into BuildConfig so the running app can show it
// (Settings → About). Falls back to "unknown" when there's no git checkout (e.g. a source
// tarball). Only this module's BuildConfig references it, so a new commit recompiles core:common
// alone — it doesn't cascade through the module graph.
fun gitSha(): String = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "--short=8", "HEAD")
    }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"

android {
    namespace = "com.garfiec.librechat.core.common"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "GIT_SHA", "\"${gitSha()}\"")
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            api(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.coroutines.android)
            implementation(libs.koin.android)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}
