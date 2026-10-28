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

/**
 * 비밀 빌드 속성(제공자 앱 키 등)을 읽는다: local.properties → Gradle 속성(-P, ~/.gradle/gradle.properties) → 환경 변수 순.
 * 저장소에는 넣지 않으며(AGENTS.md), 없으면 빈 문자열이다. 키가 비면 그 기능은 "준비 중"으로 동작하고 빌드·시험은 통과한다.
 */
fun Project.secretProperty(name: String): String {
    val localFile = rootProject.file("local.properties")
    val local = if (localFile.exists()) {
        java.util.Properties().apply { localFile.inputStream().use { load(it) } }.getProperty(name)
    } else {
        null
    }
    return (local ?: providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull ?: "").trim()
}

/** BuildConfig 문자열 값으로 쓸 수 있게 따옴표·역슬래시를 막는다. */
fun buildConfigString(value: String): String = "\"" + value.replace("\\", "").replace("\"", "") + "\""

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
    // 플레이버(env)를 직접 가진 라이브러리(:data, 플레이버별 서버 주소 BuildConfig)는 같은 이름의 실제 작업이 있으므로
    // 별칭을 만들지 않는다. AGP는 플러그인 적용 때 등록한 afterEvaluate에서 변형 작업을 만들므로 이 검사가 그 뒤에 돈다.
    afterEvaluate {
        if ("testLocalDebugUnitTest" !in tasks.names) {
            tasks.register("testLocalDebugUnitTest") {
                group = "verification"
                description = "AGENTS.md 검증 명령용 별칭: $unitTestTask"
                dependsOn(unitTestTask)
            }
        }
        if ("lintLocalDebug" !in tasks.names) {
            tasks.register("lintLocalDebug") {
                group = "verification"
                description = "AGENTS.md 검증 명령용 별칭: $lintTask"
                dependsOn(lintTask)
            }
        }
    }
}
