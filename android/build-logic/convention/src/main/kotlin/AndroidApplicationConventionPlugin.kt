import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * 폰 앱(:app)과 워치 앱(:wear) 공통 설정.
 * 플레이버 env(local·develop·prod)를 두 앱이 같이 가져 Wearable Data Layer가 같은 applicationId로 짝지어진다.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                // Latest AndroidX (Navigation 3 1.2, Hilt Navigation 1.4, Compose BOM 2026.09) requires compileSdk 37.
                // targetSdk stays 36 (Google Play requirement as of 2026-08-31).
                compileSdk = COMPILE_SDK
                defaultConfig {
                    minSdk = MIN_SDK
                    targetSdk = TARGET_SDK
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
                flavorDimensions += "env"
                productFlavors {
                    create("local") {
                        dimension = "env"
                        applicationIdSuffix = ".local"
                        versionNameSuffix = "-local"
                    }
                    create("develop") {
                        dimension = "env"
                        applicationIdSuffix = ".dev"
                        versionNameSuffix = "-dev"
                    }
                    create("prod") {
                        dimension = "env"
                    }
                }
            }
            dependencies {
                add("testImplementation", libs.lib("junit"))
                add("testImplementation", libs.lib("kotlin-test-junit"))
            }
            configureKtlint(android = true)
            enforceModuleRules()
        }
    }
}
