import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * 설계서 13.2 "계층 규칙"을 Gradle 의존 선언으로 강제한다.
 * - `:feature:*`는 `:domain`·`:core:*`만 의존한다. 예외는 `:feature:record`·`:feature:runimal`·`:feature:crew` → `:feature:share`
 *   (크루 축하 카드 공유, ui.crew → ui.share, D-140). `:feature:avatar`는 공용 Rive 아바타 렌더러 `:core:avatar`도 의존한다
 *   (S15P31A207-300). `:core:avatar`는 아무 모듈도 의존하지 않는다.
 * - `:data`(폰·워치 공용)는 `:domain`·`:core:model`만 의존한다. 폰 전용 `:data:phone`(D-365, W16)은 `:data`·`:domain`·
 *   `:core:model`·`:wear-contract`를 의존한다.
 * - `:wear`는 `:wear-contract`·`:domain`·`:data`·`:core:model`을 의존한다(D-366). `:data:phone`은 넣지 않는다: Hilt가 의존 모듈의
 *   바인딩을 모두 모으므로 워치용 MeasurementHost·WatchGateway 등과 중복 바인딩이 된다(설계 9.1).
 * - `:domain`은 `:core:model`만, 순수 모듈(`:core:model`, `:wear-contract`)은 아무 모듈도 의존하지 않는다.
 * - `:app`은 조립 루트라 Hilt 집계를 위해 모든 모듈을 가진다(5.1.2 의존 표는 import 기준이라 세지 않는다).
 * 같은 Gradle 모듈 안 패키지 사이 방향(5.1.2 의존 표)은 :app의 Konsist 구조 시험이 지킨다.
 */
internal object ModuleRules {
    private val featureBase = setOf(":domain", ":core:model", ":core:ui")

    private val allowed: Map<String, Set<String>> =
        mapOf(
            ":core:model" to emptySet(),
            ":wear-contract" to emptySet(),
            ":core:ui" to setOf(":core:model"),
            // 공용 Rive 아바타 렌더러(S15P31A207-300)는 다른 모듈을 쓰지 않는다. 아바타 화면만 붙인다.
            ":core:avatar" to emptySet(),
            ":domain" to setOf(":core:model"),
            ":data" to setOf(":domain", ":core:model"),
            ":data:phone" to setOf(":data", ":domain", ":core:model", ":wear-contract"),
            ":wear" to setOf(":wear-contract", ":domain", ":data", ":core:model"),
            ":feature:home" to featureBase,
            ":feature:run" to featureBase,
            ":feature:record" to featureBase + ":feature:share",
            ":feature:battle" to featureBase,
            ":feature:crew" to featureBase + ":feature:share",
            ":feature:chat" to featureBase,
            ":feature:runimal" to featureBase + ":feature:share",
            ":feature:avatar" to featureBase + ":core:avatar",
            ":feature:me" to featureBase,
            ":feature:notification" to featureBase,
            ":feature:share" to featureBase,
        )

    /** 규칙 표에 없는 모듈(:app)은 null을 돌려 검사하지 않는다. */
    fun allowedFor(path: String): Set<String>? = allowed[path]
}

internal fun Project.enforceModuleRules() {
    afterEvaluate {
        val allowedTargets = ModuleRules.allowedFor(path) ?: return@afterEvaluate
        configurations.forEach { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
                val target = dependency.path
                if (target != path && target !in allowedTargets) {
                    throw GradleException(
                        "모듈 의존 규칙 위반(설계서 13.2): $path → $target " +
                            "(${configuration.name}). 허용: ${allowedTargets.sorted()}",
                    )
                }
            }
        }
    }
}
