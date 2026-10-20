// domain.*: 업무 규칙·도메인 모델·포트·UseCase(D-56). Android 플러그인 없는 순수 Kotlin JVM 모듈이고
// 주입 애너테이션은 javax.inject(JSR-330)만 쓴다(D-61).
plugins {
    alias(libs.plugins.runkk.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)

    testImplementation(libs.kotlinx.coroutines.test)
}
