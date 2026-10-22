import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * 표현 모듈(:feature:*, 패키지 ui.*) 공통 설정: Compose와 :domain·:core:* 의존.
 * 화면을 담는 모듈만 Hilt(FeatureEntry @IntoSet)를 각자 build.gradle.kts에서 켠다.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("runkk.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }
            dependencies {
                add("implementation", project(":domain"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:ui"))
                add("implementation", platform(libs.lib("androidx-compose-bom")))
                add("implementation", libs.lib("androidx-compose-ui"))
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-navigation3-runtime"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            }
        }
    }
}
