package com.a207.runkk.app

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E 시험용 카카오 토큰 창구(src/localDebug)가 local 플레이버 + debug 빌드에만 들어가는지 확인한다.
 * 변형마다 따로 돈다: testLocalDebugUnitTest는 "있다", testDevelopDebugUnitTest·testProdDebugUnitTest는 "없다"를 확인한다.
 * release 변형은 단위 시험 작업이 없어 e2e/verify_release.sh가 APK dex를 직접 뒤져 확인한다(e2e/README.md "출시 빌드 확인").
 */
class E2eLoginPathTest {
    private val e2eVariant = BuildConfig.FLAVOR == "local" && BuildConfig.BUILD_TYPE == "debug"

    @Test
    fun `시험용 카카오 창구 클래스는 localDebug에만 있다`() {
        val present =
            listOf(
                "com.a207.runkk.app.E2eKakaoTokenSource",
                "com.a207.runkk.app.E2eLoginModule",
                "com.a207.runkk.app.E2eBuildConfigModule"
            ).map { name -> runCatching { Class.forName(name) }.isSuccess }

        assertEquals(List(present.size) { e2eVariant }, present)
    }

    @Test
    fun `시험용 토큰 빌드 설정 값은 localDebug에만 있다`() {
        val present = BuildConfig::class.java.declaredFields.any { it.name == "E2E_KAKAO_TOKEN" }

        assertEquals(e2eVariant, present)
    }
}
