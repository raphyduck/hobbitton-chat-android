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
        commonMain {
            dependencies {
                implementation(libs.coroutines.core)
                implementation(libs.okio)
                api(libs.kotlinx.datetime)
                // Kermit only — :core:logging depends on this module, so `Diag` is unreachable here.
                // Its PersistentLogWriter is a Kermit LogWriter, so plain Kermit still reaches the
                // diagnostic export.
                implementation(libs.kermit)
            }
        }
        commonTest.dependencies {
            implementation(libs.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.coroutines.android)
            implementation(libs.koin.android)
            // ContextCompat.registerReceiver, for the exported/not-exported flag the power-save
            // receiver needs. Declared rather than relied on transitively through koin-android.
            implementation(libs.androidx.core.ktx)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}
