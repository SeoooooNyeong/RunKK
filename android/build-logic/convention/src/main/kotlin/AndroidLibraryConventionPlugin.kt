import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Android 라이브러리 모듈(:core:ui, :data, :feature:*) 공통 설정. 플레이버 없이 debug·release만 둔다. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                compileSdk = COMPILE_SDK
                defaultConfig {
                    minSdk = MIN_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                lint {
                    abortOnError = true
                    htmlReport = true
                    xmlReport = false
                }
            }
            dependencies {
                add("testImplementation", libs.lib("junit"))
                add("testImplementation", libs.lib("kotlin-test-junit"))
            }
            configureKtlint(android = true)
            aliasVerificationTasks(unitTestTask = "testDebugUnitTest", lintTask = "lintDebug")
            enforceModuleRules()
        }
    }
}
