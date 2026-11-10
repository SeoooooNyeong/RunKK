package com.a207.runkk.app.core.avatar

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 미리 준비([prepareRiveAvatarLook])는 Rive 뷰 없이 서기 합성을 해야 하므로, 네이티브 로더가 받는 내장 이미지(ImageAsset 이름 +
 * FileAssetContents 바이트)를 riv 바이트에서 직접 읽는다. 이름·크기는 5.1 표(설계 검증 28.0.3 C2)와 로더 슬롯 목록과 같다.
 */
class RivEmbeddedImagesTest {
    private val raw = File("src/main/res/raw")

    @Test
    fun `여자 서기는 머리 다섯 자리, standing_body 512x768, 볼 덮개를 담는다`() {
        val images = read("avatar_idle_layered")
        assertEquals(AvatarVariantAssets.FRONT_SLOTS + setOf("standing_body", "fullness_overlay"), images.keys)
        assertEquals(512 to 768, HeadResultPolicy.pngSize(images.getValue("standing_body")))
        (AvatarVariantAssets.FRONT_SLOTS + "fullness_overlay").forEach {
            assertEquals(1024 to 1024, HeadResultPolicy.pngSize(images.getValue(it)), it)
        }
    }

    @Test
    fun `남자 서기는 여자 목록에 내장 기본 옷 몸 male_selected_body 1122x1402를 더 담는다`() {
        val images = read("avatar_male_idle")
        assertEquals(
            AvatarVariantAssets.FRONT_SLOTS + setOf("standing_body", "fullness_overlay", MaleSelectedBody.ASSET),
            images.keys
        )
        assertEquals(1122 to 1402, HeadResultPolicy.pngSize(images.getValue(MaleSelectedBody.ASSET)))
        assertEquals(512 to 768, HeadResultPolicy.pngSize(images.getValue("standing_body")))
    }

    @Test
    fun `남녀 달리기는 달리기 로더 슬롯 일곱과 옆볼 덮개를 담는다`() {
        for (name in listOf("avatar_run_approved", "avatar_male_run")) {
            val images = read(name)
            assertEquals(RunAvatarAssetLoader.SLOTS + "fullness_overlay_side", images.keys, name)
            val sizes = mapOf(
                "side_head" to (1024 to 1024),
                "side_body_coral" to (256 to 384),
                "side_body_cream" to (256 to 384),
                "side_body_blue" to (256 to 384),
                "side_leg_skin" to (256 to 768),
                "side_shoe" to (256 to 160),
                "continuous_arm" to (256 to 1024),
                "fullness_overlay_side" to (1024 to 1024)
            )
            sizes.forEach { (slot, size) ->
                assertEquals(size, HeadResultPolicy.pngSize(images.getValue(slot)), "$name $slot")
            }
        }
    }

    @Test
    fun `읽은 바이트는 PNG 하나 전체이고 원본 배열을 고치지 않는다`() {
        val original = File(raw, "avatar_idle_layered.riv").readBytes()
        val copy = original.copyOf()
        val images = RivEmbeddedImages.read(original)
        assertTrue(original.contentEquals(copy), "원본 그대로")
        images.values.forEach { png ->
            assertEquals(PNG_SIGNATURE.toList(), png.copyOfRange(0, 8).toList())
            assertEquals(IEND.toList(), png.copyOfRange(png.size - 8, png.size - 4).toList(), "IEND로 끝난다")
        }
    }

    @Test
    fun `내장 이미지가 없거나 길이가 맞지 않으면 빈 목록이다`() {
        assertEquals(emptyMap(), RivEmbeddedImages.read("RIVE".toByteArray()))
        // 이름 「face」 뒤 FileAssetContents 길이가 남은 바이트보다 길다.
        val broken = byteArrayOf(0x69, 0xCB.toByte(), 0x01, 4) + "face".toByteArray() +
            byteArrayOf(0, 0x6A, 0xD4.toByte(), 0x01, 0x7F) + PNG_SIGNATURE
        assertEquals(emptyMap(), RivEmbeddedImages.read(broken))
    }

    private fun read(name: String): Map<String, ByteArray> = RivEmbeddedImages.read(File(raw, "$name.riv").readBytes())

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val IEND = "IEND".toByteArray()
    }
}
