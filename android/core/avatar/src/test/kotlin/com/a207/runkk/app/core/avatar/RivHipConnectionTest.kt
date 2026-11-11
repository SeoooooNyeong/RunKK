package com.a207.runkk.app.core.avatar

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 판정 round1 C: 달리기 엉덩이 잇기 메시가 다리 메시가 쓰지 않는 텍스처 칸(y 24~72)을 쓰도록 꼭짓점 v만 옮긴다(파일은 승인본 그대로). */
class RivHipConnectionTest {
    private val raw = File("src/main/res/raw")

    @Test
    fun `달리기 riv 두 개는 엉덩이 잇기 꼭짓점 v 17개만 성별 칸만큼 줄고 나머지 바이트는 그대로다`() {
        for ((name, offsets, shift) in listOf(
            Triple("avatar_run_approved", RivHipConnection.FEMALE_OFFSETS, RivHipConnection.FEMALE_V_SHIFT),
            Triple("avatar_male_run", RivHipConnection.MALE_OFFSETS, RivHipConnection.MALE_V_SHIFT)
        )) {
            val original = File(raw, "$name.riv").readBytes()
            val patched = original.copyOf().also(RivHipConnection::retargetInPlace)
            val before = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN)
            val after = ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(17, offsets.size)
            offsets.forEach {
                val v = before.getFloat(it)
                assertTrue(v in 0.2f..0.28f, "$name 원래 v $v")
                assertEquals(v - shift, after.getFloat(it), 1e-6f)
            }
            val allowed = offsets.flatMap { (it until it + 4).toList() }.toSet()
            assertEquals(emptyList(), original.indices.filter { original[it] != patched[it] && it !in allowed })
        }
    }

    @Test
    fun `서기 riv는 바꾸지 않는다`() {
        for (name in listOf("avatar_idle_layered", "avatar_male_idle")) {
            val original = File(raw, "$name.riv").readBytes()
            val patched = original.copyOf().also(RivHipConnection::retargetInPlace)
            assertTrue(original.contentEquals(patched), name)
        }
    }
}
