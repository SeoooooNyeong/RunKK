package com.a207.runkk.app.core.avatar

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 서기 riv 바이트 패치(허리 네모·엉덩이 뼈 묶음·체형 2·3단계 키, 2026-11-12 PM): 승인 riv 파일은 그대로 두고 앱이 읽은 바이트에 패치 파일을
 * 덮는다. 지문(파일 길이, 고칠 자리 바꾸기 전·뒤 SHA-256)이 맞을 때만 덮는다.
 */
class RivBytePatchTest {
    private val raw = File("src/main/res/raw")

    @Test
    fun `지문이 맞으면 고칠 자리만 바꾸고 다시 부르면 이미 바뀐 것으로 본다`() {
        val riv = ByteArray(64) { it.toByte() }
        val patch = patch(riv, listOf(Edit(8, byteArrayOf(1, 2, 3, 4)), Edit(40, byteArrayOf(9))))
        val target = riv.copyOf()

        assertEquals(RivBytePatch.Outcome.APPLIED, RivBytePatch.applyInPlace(target, patch))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), target.copyOfRange(8, 12))
        assertEquals(9, target[40].toInt())
        assertEquals(
            listOf(8, 9, 10, 11, 40),
            riv.indices.filter { riv[it] != target[it] },
            "고칠 자리 말고는 그대로"
        )
        val again = target.copyOf()
        assertEquals(RivBytePatch.Outcome.ALREADY_APPLIED, RivBytePatch.applyInPlace(again, patch))
        assertContentEquals(target, again)
    }

    @Test
    fun `길이나 고칠 자리 값이 다르면(다시 내보낸 riv) 고치지 않는다`() {
        val riv = ByteArray(64) { it.toByte() }
        val patch = patch(riv, listOf(Edit(8, byteArrayOf(1, 2, 3, 4))))

        val reexported = riv.copyOf().also { it[9] = 77 }
        assertEquals(RivBytePatch.Outcome.MISMATCH, RivBytePatch.applyInPlace(reexported, patch))
        assertEquals(77, reexported[9].toInt())

        val longer = riv.copyOf(riv.size + 4)
        assertEquals(RivBytePatch.Outcome.MISMATCH, RivBytePatch.applyInPlace(longer, patch))
        assertContentEquals(riv.copyOf(riv.size + 4), longer)
    }

    @Test
    fun `잘못된 패치 파일은 riv를 건드리지 않는다`() {
        val riv = ByteArray(64) { it.toByte() }
        val target = riv.copyOf()
        assertEquals(RivBytePatch.Outcome.MISMATCH, RivBytePatch.applyInPlace(target, byteArrayOf(1, 2, 3)))
        val outside = patch(riv, listOf(Edit(8, byteArrayOf(1)))).also { it[it.size - 6] = 0x7f } // 자리를 파일 밖으로
        assertEquals(RivBytePatch.Outcome.MISMATCH, RivBytePatch.applyInPlace(target, outside))
        val overflow = patch(riv, listOf(Edit(8, byteArrayOf(1, 2)))).also {
            // 자리 Int.MAX_VALUE − 0: 자리 + 길이가 Int를 넘어 음수로 감기는 값
            listOf(0x7f, 0xff, 0xff, 0xff).forEachIndexed { i, b -> it[it.size - 7 + i] = b.toByte() }
        }
        assertEquals(RivBytePatch.Outcome.MISMATCH, RivBytePatch.applyInPlace(target, overflow))
        val negative = patch(riv, listOf(Edit(8, byteArrayOf(1)))).also { it[it.size - 6] = 0x80.toByte() }
        assertEquals(RivBytePatch.Outcome.MISMATCH, RivBytePatch.applyInPlace(target, negative))
        val trailing = patch(riv, listOf(Edit(8, byteArrayOf(1)))) + byteArrayOf(0)
        assertEquals(RivBytePatch.Outcome.MISMATCH, RivBytePatch.applyInPlace(target, trailing))
        assertContentEquals(riv, target)
    }

    @Test
    fun `달리기에는 패치가 없고 서기는 남녀 res raw 패치를 쓴다`() {
        assertEquals(null, RivBytePatch.resource(AvatarGender.MALE, AvatarMotion.RUN))
        assertEquals(null, RivBytePatch.resource(AvatarGender.FEMALE, AvatarMotion.RUN))
        assertEquals(R.raw.avatar_male_idle_patch, RivBytePatch.resource(AvatarGender.MALE, AvatarMotion.STAND))
        assertEquals(R.raw.avatar_female_idle_patch, RivBytePatch.resource(AvatarGender.FEMALE, AvatarMotion.STAND))
    }

    @Test
    fun `남녀 서기 패치는 앞선 고치기(바탕·그림자·소매 이음매) 뒤 승인 riv에 맞는다`() {
        for (gender in AvatarGender.entries) {
            val name = if (gender == AvatarGender.MALE) "avatar_male_idle" else "avatar_idle_layered"
            val prepared = PreparedRiveAssets.hideBuiltInShapes(File(raw, "$name.riv").readBytes())
            val patch = File(raw, RivBytePatch.resourceName(gender) + ".bin").readBytes()
            val before = prepared.copyOf()

            assertEquals(RivBytePatch.Outcome.APPLIED, RivBytePatch.applyInPlace(prepared, patch), "$gender")
            assertTrue(before.indices.count { before[it] != prepared[it] } > 0, "$gender 바뀐 바이트가 있다")
            assertEquals(before.size, prepared.size, "길이는 그대로")
        }
    }

    private class Edit(val offset: Int, val bytes: ByteArray)

    /** 시험용 패치 파일: RKP1, 길이, 바꾸기 전 SHA-256, 바꾼 뒤 SHA-256, 개수, (자리, 길이, 바이트)… */
    private fun patch(riv: ByteArray, edits: List<Edit>): ByteArray {
        fun sha(parts: List<ByteArray>) =
            MessageDigest.getInstance("SHA-256").apply { parts.forEach(::update) }.digest()
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write("RKP1".toByteArray())
            data.writeInt(riv.size)
            data.write(sha(edits.map { riv.copyOfRange(it.offset, it.offset + it.bytes.size) }))
            data.write(sha(edits.map { it.bytes }))
            data.writeInt(edits.size)
            edits.forEach {
                data.writeInt(it.offset)
                data.writeByte(it.bytes.size)
                data.write(it.bytes)
            }
        }
        return out.toByteArray()
    }
}
