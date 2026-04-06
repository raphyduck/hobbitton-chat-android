import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("librechat.mobile.detekt")
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KotlinAndroidProjectExtension> {
                jvmToolchain(17)
                compilerOptions {
                    freeCompilerArgs.addAll("-opt-in=kotlin.time.ExperimentalTime")
                }
            }

            extensions.configure<ApplicationExtension> {
                compileSdk = 36
                defaultConfig {
                    minSdk = 26
                    targetSdk = 35
                    versionCode = 1
                    versionName = "0.1.0"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    isCoreLibraryDesugaringEnabled = true
                }
                lint {
                    disable += "NullSafeMutableLiveData"
                }
                buildTypes {
                    release {
                        isMinifyEnabled = true
                        signingConfig = signingConfigs.getByName("debug")
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

private val Project.libs
    get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
