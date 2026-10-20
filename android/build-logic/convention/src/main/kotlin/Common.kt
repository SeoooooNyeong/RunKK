import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jlleitschuh.gradle.ktlint.KtlintExtension

internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 26
internal const val TARGET_SDK = 36

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

/** 코드 컨벤션 4장: 포맷은 ktlint(규칙은 루트 .editorconfig)가 정한다. */
internal fun Project.configureKtlint(android: Boolean) {
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    extensions.configure<KtlintExtension> {
        version.set("1.8.0")
        this.android.set(android)
    }
}

/**
 * CI·AGENTS.md 명령(`lintLocalDebug`, `testLocalDebugUnitTest`)이 플레이버 없는 모듈에서도 돌도록
 * 같은 이름의 작업을 그 모듈의 실제 작업에 잇는다.
 */
internal fun Project.aliasVerificationTasks(
    unitTestTask: String,
    lintTask: String,
) {
    tasks.register("testLocalDebugUnitTest") {
        group = "verification"
        description = "AGENTS.md 검증 명령용 별칭: $unitTestTask"
        dependsOn(unitTestTask)
    }
    tasks.register("lintLocalDebug") {
        group = "verification"
        description = "AGENTS.md 검증 명령용 별칭: $lintTask"
        dependsOn(lintTask)
    }
}
