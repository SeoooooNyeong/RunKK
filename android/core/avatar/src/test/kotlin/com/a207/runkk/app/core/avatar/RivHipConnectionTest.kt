package com.a207.runkk.app.core.avatar

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import timber.log.Timber

/**
 * 판정 round1 C: 달리기 엉덩이 잇기 메시가 다리 메시가 쓰지 않는 텍스처 칸(여자 y 0~47, 남자 y 48~95)을 쓰도록 꼭짓점 v만 옮긴다(파일은
 * 승인본 그대로). 승인 파일 지문이 다르면 고치지 않는다(코드 리뷰).
 */
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

    private val cases = listOf(
        Triple("avatar_run_approved", RivHipConnection.FEMALE_OFFSETS, RivHipConnection.FEMALE_V_SHIFT),
        Triple("avatar_male_run", RivHipConnection.MALE_OFFSETS, RivHipConnection.MALE_V_SHIFT)
    )

    @AfterTest
    fun reset() {
        Timber.uprootAll()
        RivHipConnection.resetWarning()
    }

    @Test
    fun `승인 달리기 riv 지문(길이, 꼭짓점 둘레 바이트 SHA-256)이 상수와 같다`() {
        for ((name, offsets, _) in cases) {
            val riv = File(raw, "$name.riv").readBytes()
            val expected = RivHipConnection.FINGERPRINTS.single { it.offsets === offsets }
            assertEquals(expected.size, riv.size, name)
            assertEquals(expected.sha256, RivHipConnection.windowSha256(riv, offsets), name)
        }
    }

    @Test
    fun `다시 내보내 지문이 다르면 v가 승인 범위여도 고치지 않고 경고를 한 번만 남긴다`() {
        val warnings = mutableListOf<String>()
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                warnings += message
            }
        })
        for ((name, offsets, _) in cases) {
            val original = File(raw, "$name.riv").readBytes()
            // 꼭짓점 둘레(v 값 밖) 한 바이트만 다른 사본, 끝에 한 바이트 붙은 사본
            val nearby = original.copyOf().also { it[offsets.first() - 9] = (it[offsets.first() - 9] + 1).toByte() }
            val longer = original.copyOf(original.size + 1)
            for (riv in listOf(nearby, longer)) {
                val before = riv.copyOf()
                RivHipConnection.retargetInPlace(riv)
                assertTrue(before.contentEquals(riv), name)
            }
        }
        assertEquals(1, warnings.size, warnings.toString())
    }

    @Test
    fun `두 번 불러도 한 번 부른 것과 같다`() {
        for ((name, _, _) in cases) {
            val once = File(raw, "$name.riv").readBytes().also(RivHipConnection::retargetInPlace)
            val twice = once.copyOf().also(RivHipConnection::retargetInPlace)
            assertTrue(once.contentEquals(twice), name)
        }
    }

    @Test
    fun `검수 바탕·그림자를 먼저 숨긴 준비 바이트에서도 엉덩이 잇기 v를 옮긴다`() {
        for ((name, offsets, shift) in cases) {
            val original = File(raw, "$name.riv").readBytes()
            val prepared = PreparedRiveAssets.hideBuiltInShapes(original.copyOf())
            val before = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN)
            val after = ByteBuffer.wrap(prepared).order(ByteOrder.LITTLE_ENDIAN)
            offsets.forEach { assertEquals(before.getFloat(it) - shift, after.getFloat(it), 1e-6f, name) }
        }
    }
}
