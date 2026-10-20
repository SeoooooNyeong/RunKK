import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * 설계서 13.2 "계층 규칙"을 Gradle 의존 선언으로 강제한다.
 * - `:feature:*`는 `:domain`·`:core:*`만 의존한다. 예외는 `:feature:record`·`:feature:runimal` → `:feature:share`.
 * - `:data`는 `:domain`·`:core:model`·`:wear-contract`만 의존한다.
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
            ":domain" to setOf(":core:model"),
            ":data" to setOf(":domain", ":core:model", ":wear-contract"),
            ":wear" to setOf(":wear-contract"),
            ":feature:home" to featureBase,
            ":feature:run" to featureBase,
            ":feature:record" to featureBase + ":feature:share",
            ":feature:battle" to featureBase,
            ":feature:crew" to featureBase,
            ":feature:runimal" to featureBase + ":feature:share",
            ":feature:avatar" to featureBase,
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
