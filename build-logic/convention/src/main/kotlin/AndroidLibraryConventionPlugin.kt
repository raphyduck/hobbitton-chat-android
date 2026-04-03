import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("librechat.mobile.detekt")
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    freeCompilerArgs.addAll("-opt-in=kotlin.time.ExperimentalTime")
                }
            }

            extensions.configure<LibraryExtension> {
                compileSdk = 36
                defaultConfig {
                    minSdk = 26
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                    targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
                    isCoreLibraryDesugaringEnabled = true
                }
                lint {
                    // Workaround: NonNullableMutableLiveDataDetector crashes with
                    // IncompatibleClassChangeError on AGP 8.7.x + Kotlin 2.1.x
                    disable += "NullSafeMutableLiveData"
                }
                testOptions {
                    unitTests.isReturnDefaultValues = true
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
