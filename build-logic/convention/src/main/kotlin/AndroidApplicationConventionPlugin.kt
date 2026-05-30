import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.util.Properties

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("librechat.detekt")
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            val appVersion = readAppVersion(target)
            val release = readReleaseSigning(target)

            extensions.configure<KotlinAndroidProjectExtension> {
                jvmToolchain(BuildConstants.JVM_TOOLCHAIN_VERSION)
                compilerOptions {
                    freeCompilerArgs.addAll("-opt-in=kotlin.time.ExperimentalTime")
                }
            }

            extensions.configure<ApplicationExtension> {
                compileSdk = BuildConstants.COMPILE_SDK
                defaultConfig {
                    minSdk = BuildConstants.MIN_SDK
                    targetSdk = BuildConstants.TARGET_SDK
                    versionCode = appVersion.code
                    versionName = appVersion.name
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    isCoreLibraryDesugaringEnabled = true
                }
                lint {
                    disable += "NullSafeMutableLiveData"
                }
                if (release != null) {
                    signingConfigs {
                        create("release") {
                            storeFile = file(release.storeFile)
                            storePassword = release.storePassword
                            keyAlias = release.keyAlias
                            keyPassword = release.keyPassword
                            enableV1Signing = true
                            enableV2Signing = true
                            enableV3Signing = true
                        }
                    }
                }
                buildTypes {
                    release {
                        isMinifyEnabled = true
                        // Strip unused resources alongside the code shrink. Requires minify.
                        isShrinkResources = true
                        // Use the real release key when credentials are present (CI release
                        // builds, or a local keystore.properties); otherwise fall back to the
                        // debug key so local `assembleRelease` and CI checks still work.
                        signingConfig = if (release != null) {
                            signingConfigs.getByName("release")
                        } else {
                            signingConfigs.getByName("debug")
                        }
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }
            }

            dependencies.add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk").get())
            dependencies.add("testImplementation", libs.findBundle("testing").get())
        }
    }
}

private data class AppVersion(val name: String, val code: Int)

/**
 * Reads `versionName` from version.properties and derives a monotonic `versionCode`
 * from it as `MAJOR * 10_000 + MINOR * 100 + PATCH` (i.e. the digits XXYYZZ), so the
 * code is a deterministic function of the name with nothing to track separately:
 * `0.1.0` -> 100, `1.2.3` -> 10203. MINOR and PATCH must each be <= 99 to stay
 * monotonic; the build fails fast if either overflows.
 *
 * Pre-release suffixes (`-rc1`) are stripped, so `0.8.6-rc1` and `0.8.6` map to the
 * same code — fine for tagged stable releases; bump the patch if you ship an RC users
 * actually update from.
 */
private fun readAppVersion(target: Project): AppVersion {
    val props = Properties().apply {
        val file = target.rootProject.file("version.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    val name = props.getProperty("versionName") ?: "0.0.0"
    val (major, minor, patch) = name.substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }
        .plus(listOf(0, 0, 0))
        .take(3)
    // The XXYYZZ packing only stays monotonic while MINOR and PATCH each fit in two
    // digits; e.g. 0.1.100 would collide with 0.2.0 (both -> 200). Fail the build
    // (and therefore CI) rather than silently ship a non-monotonic versionCode.
    check(minor in 0..99 && patch in 0..99) {
        "versionName '$name' exceeds the XXYYZZ versionCode scheme: MINOR and PATCH " +
            "must each be <= 99 (got minor=$minor, patch=$patch). Bump MAJOR/MINOR instead."
    }
    return AppVersion(name = name, code = major * 10_000 + minor * 100 + patch)
}

private data class ReleaseSigning(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

/**
 * Resolves release signing credentials from environment variables (CI) first, then a
 * local `keystore.properties` at the repo root. Returns null when any field is missing,
 * which signals the caller to fall back to debug signing — keeping the keystore out of
 * the repo while letting local `assembleRelease` and CI lint/test checks run unsigned.
 */
private fun readReleaseSigning(target: Project): ReleaseSigning? {
    val propsFile = target.rootProject.file("keystore.properties")
    val props = Properties().apply {
        if (propsFile.exists()) propsFile.inputStream().use { load(it) }
    }
    fun value(env: String, prop: String): String? =
        System.getenv(env) ?: props.getProperty(prop)

    val storeFile = value("SIGNING_STORE_FILE", "storeFile")
    val storePassword = value("SIGNING_STORE_PASSWORD", "storePassword")
    val keyAlias = value("SIGNING_KEY_ALIAS", "keyAlias")
    val keyPassword = value("SIGNING_KEY_PASSWORD", "keyPassword")

    return if (storeFile != null && storePassword != null && keyAlias != null && keyPassword != null) {
        ReleaseSigning(storeFile, storePassword, keyAlias, keyPassword)
    } else {
        null
    }
}

private val Project.libs
    get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
