package com.a207.runkk.app.core.avatar

import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 승인된 Rive 자산 회귀 방지(아바타 PoC 온보딩 4절): 과거 publisher·prepare 결과나 review atlas로 덮이면 최신 여자 전완·목 등록·
 * 남자 체형 보정이 사라진다. 바꿀 때는 새 승인 자산의 SHA256으로 이 표를 함께 고친다.
 */
class ApprovedAvatarAssetsTest {
    private val main = File("src/main")

    @Test
    fun `4개 화면 매핑 Rive 파일과 서기 몸 기본 텍스처는 승인본과 같다`() {
        APPROVED.forEach { (name, sha) ->
            val file = File(main, "res/raw/$name")
            assertTrue(file.isFile, "$name 이 없다")
            assertEquals(sha, sha256(file), name)
        }
        val extra = File(main, "res/raw").list().orEmpty().toSet() - APPROVED.keys
        assertEquals(emptySet(), extra, "4개 매핑 밖 Rive·이미지(avatar.riv, avatar_idle_approved.riv 등)를 넣지 않는다")
    }

    @Test
    fun `남녀 내장 머리 레이어는 12장씩 모두 1024×1024 PNG다`() {
        for (gender in AvatarGender.entries) {
            val dir = File(main, "assets/${AvatarVariantAssets.assetDirectory(gender)}")
            assertEquals(
                AvatarVariantAssets.BUILT_IN_LAYERS.map { "$it.png" }.toSet(),
                dir.list().orEmpty().toSet(),
                "${gender.name} 내장 레이어 목록"
            )
            AvatarVariantAssets.BUILT_IN_LAYERS.forEach { name ->
                val bytes = File(dir, "$name.png").readBytes()
                assertEquals(1024 to 1024, HeadResultPolicy.pngSize(bytes), "$gender/$name")
            }
        }
    }

    private fun sha256(file: File): String = DataInputStream(file.inputStream()).use { input ->
        MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val APPROVED = mapOf(
            "avatar_idle_layered.riv" to "cb14dbc454ed414287b4eb57eaa472e8390bcb71309462833e69223d970a7504",
            "avatar_male_idle.riv" to "8f203bef9bb23815d20fcae05c76faf431d42c2ef9c949dd36b7f742e7ea62f5",
            "avatar_run_approved.riv" to "701c425b398658b2151854ec6b97bf282e699482e5b08bf5fbddfab9feedb298",
            "avatar_male_run.riv" to "51ab4626bdfced4053e825b0531652861bd4bd8383e7b53679c40d4088b83835",
            "avatar_legacy_standing_body.png" to "25c669b6a14f984453bb2eead8656363e05ff1e1e8c79292fb4e58eccbfd67b7"
        )
    }
}
