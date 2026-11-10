package com.a207.runkk.app.core.avatar

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * 실기기 결함 DV-성-3: 승인 Rive 파일 네 개의 아트보드에는 PoC 검수용 베이지 바탕 도형(「Background」·「Review background」,
 * SolidColor FFF6EFE8)이 들어 있어 카드 배경을 가린다. 파일(SHA 시험)은 그대로 두고 읽은 바이트에서 그 칠의 알파만 0으로 바꾼다.
 */
class RivReviewBackgroundTest {
    private val raw = File("src/main/res/raw")

    @Test
    fun `네 Rive 파일 모두 검수 바탕 칠 하나의 알파만 0으로 바꾸고 나머지 바이트는 그대로다`() {
        for (name in listOf("avatar_idle_layered", "avatar_male_idle", "avatar_run_approved", "avatar_male_run")) {
            val original = File(raw, "$name.riv").readBytes()
            val hidden = RivReviewBackground.hidden(original)
            assertNotSame(original, hidden, "원본 배열을 고치지 않는다")
            val changed = original.indices.filter { original[it] != hidden[it] }
            assertEquals(1, changed.size, "$name 에서 바뀐 바이트")
            val at = changed.single()
            assertEquals(OPAQUE_BEIGE.toList(), original.copyOfRange(at - 3, at + 1).toList(), "$name 바뀐 자리는 베이지 칠의 알파")
            assertEquals(0.toByte(), hidden[at])
            assertEquals(-1, indexOf(hidden, byteArrayOf(COLOR_KEY) + OPAQUE_BEIGE), "$name 에 불투명 검수 바탕이 남지 않는다")
        }
    }

    @Test
    fun `검수 바탕이 없는 바이트는 같은 내용의 사본을 돌려준다`() {
        val bytes = "RIVE".toByteArray() + byteArrayOf(0, 0x12, 0x25, 1, 2, 3, 4, 0)
        val result = RivReviewBackground.hidden(bytes)
        assertContentEquals(bytes, result)
        assertNotSame(bytes, result)
    }

    private fun indexOf(bytes: ByteArray, pattern: ByteArray): Int =
        (0..bytes.size - pattern.size).firstOrNull { i -> pattern.indices.all { bytes[i + it] == pattern[it] } } ?: -1

    private companion object {
        /** SolidColor colorValue(키 37) FFF6EFE8의 리틀엔디언 바이트. */
        const val COLOR_KEY: Byte = 0x25
        val OPAQUE_BEIGE = byteArrayOf(0xE8.toByte(), 0xEF.toByte(), 0xF6.toByte(), 0xFF.toByte())
    }
}
