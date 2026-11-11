package com.a207.runkk.app.core.avatar

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [임시:D-성-107]: 그림자는 앱이 그린다(타원 0.24A × 0.048A). riv 네 파일에 들어 있는 Ground shadow(타원 105x8, SolidColor
 * 0x143D2D25, 알파 20/255)가 겹치지 않게 읽은 바이트 사본에서 그 칠의 알파만 0으로 바꾼다(파일은 승인본 그대로).
 */
class RivGroundShadowTest {
    private val raw = File("src/main/res/raw")

    @Test
    fun `네 Rive 파일 모두 Ground shadow 칠 하나의 알파만 0으로 바꾸고 나머지 바이트는 그대로다`() {
        for (name in RIVS) {
            val original = File(raw, "$name.riv").readBytes()
            val hidden = original.copyOf().also(RivGroundShadow::hideInPlace)
            val changed = original.indices.filter { original[it] != hidden[it] }
            assertEquals(1, changed.size, "$name 에서 바뀐 바이트")
            val at = changed.single()
            assertEquals(SHADOW_COLOR.toList(), original.copyOfRange(at - 3, at + 1).toList(), "$name 바뀐 자리는 그림자 칠의 알파")
            assertEquals(0.toByte(), hidden[at])
        }
    }

    @Test
    fun `준비한 Rive 바이트는 검수 바탕과 Ground shadow를 함께 숨긴 사본이다`() {
        for (name in RIVS) {
            val original = File(raw, "$name.riv").readBytes()
            val prepared = PreparedRiveAssets.hideBuiltInShapes(original.copyOf())
            // 달리기 riv는 엉덩이 잇기 꼭짓점 v(float32 17개, [RivHipConnection])도 바뀐다
            val hip = (RivHipConnection.FEMALE_OFFSETS + RivHipConnection.MALE_OFFSETS).flatMap {
                (it until it + 4).toList()
            }
            // 남자 서기 riv는 긴소매 이음매 몸통 쪽 꼭짓점 값(float32, [RivSleeveSeam])도 바뀐다
            val seam = RivSleeveSeam.TARGETS.flatMap { (it until it + 4).toList() }.toSet()
            val changed = original.indices.filter { original[it] != prepared[it] && it !in hip && it !in seam }
            assertEquals(2, changed.size, "$name: 검수 바탕 알파 하나 + 그림자 알파 하나")
        }
    }

    private companion object {
        val RIVS = listOf("avatar_idle_layered", "avatar_male_idle", "avatar_run_approved", "avatar_male_run")

        /** SolidColor colorValue 0x143D2D25의 리틀엔디언 바이트. */
        val SHADOW_COLOR = byteArrayOf(0x25, 0x2D, 0x3D, 0x14)
    }
}
