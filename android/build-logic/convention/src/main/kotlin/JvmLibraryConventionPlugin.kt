import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * 순수 Kotlin JVM 모듈(:core:model, :domain, :wear-contract) 공통 설정.
 * Android 플러그인이 없으므로 android·androidx·Retrofit·Room import가 컴파일 단계에서 막힌다(D-56, 13.2).
 * Android Lint는 com.android.lint 플러그인으로 같이 돈다.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("com.android.lint")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
            dependencies {
                add("testImplementation", libs.lib("junit"))
                add("testImplementation", libs.lib("kotlin-test-junit"))
            }
            configureKtlint(android = false)
            aliasVerificationTasks(unitTestTask = "test", lintTask = "lint")
            enforceModuleRules()
        }
    }
}
