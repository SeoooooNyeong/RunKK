package com.a207.runkk.app.core.avatar

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 판정 round3: 남자 서기 긴소매 메시의 팔·몸통 이음매 몸통 쪽 꼭짓점 값만 팔 쪽 값으로 바꾼다(파일은 승인본 그대로). */
class RivSleeveSeamTest {
    private val raw = File("src/main/res/raw")

    @Test
    fun `남자 서기 riv는 이음매 몸통 쪽 값만 팔 쪽 값으로 바뀌고 나머지 바이트는 그대로다`() {
        val original = File(raw, "avatar_male_idle.riv").readBytes()
        val patched = original.copyOf().also(RivSleeveSeam::closeInPlace)
        val before = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN)
        val after = ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(RivSleeveSeam.SOURCES.size, RivSleeveSeam.TARGETS.size)
        RivSleeveSeam.SOURCES.indices.forEach {
            val source = before.getFloat(RivSleeveSeam.SOURCES[it])
            assertTrue(before.getFloat(RivSleeveSeam.TARGETS[it]) != source, "이미 같은 값은 표에 없다")
            assertEquals(source, after.getFloat(RivSleeveSeam.TARGETS[it]))
            assertEquals(source, after.getFloat(RivSleeveSeam.SOURCES[it]))
        }
        RivSleeveSeam.OVERLAP_TARGETS.indices.forEach {
            assertEquals(RivSleeveSeam.OVERLAP_VALUES[it], after.getFloat(RivSleeveSeam.OVERLAP_TARGETS[it]))
        }
        val allowed = (RivSleeveSeam.TARGETS + RivSleeveSeam.OVERLAP_TARGETS).flatMap {
            (it until it + 4).toList()
        }.toSet()
        assertEquals(emptyList(), original.indices.filter { original[it] != patched[it] && it !in allowed })
    }

    @Test
    fun `다른 riv는 바꾸지 않는다`() {
        for (name in listOf("avatar_idle_layered", "avatar_run_approved", "avatar_male_run")) {
            val original = File(raw, "$name.riv").readBytes()
            val patched = original.copyOf().also(RivSleeveSeam::closeInPlace)
            assertTrue(original.contentEquals(patched), name)
        }
    }
}
