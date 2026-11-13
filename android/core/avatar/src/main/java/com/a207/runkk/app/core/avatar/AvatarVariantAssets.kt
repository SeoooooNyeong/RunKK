package com.a207.runkk.app.core.avatar

import android.content.res.AssetManager

/**
 * 한 몸체(남녀)에 넣을 머리 원본: 정면 다섯 층(face_layer·hair_layer·eyes_open·eyes_half·eyes_closed), 측면 세 층, 체형 1·2단계
 * 볼 레이어(정면·측면). 내장 기본 머리는 assets/avatars/{female,male}/에서 읽고, 생성 머리는 [GeneratedAvatarLayers]가 만든다.
 * 바이트는 읽기 전용이다(쓰는 쪽은 풀어서만 쓴다).
 */
internal class AvatarVariantAssets(
    val front: Map<String, ByteArray>,
    val sideLayers: SideHeadLayers,
    val frontFullness: Map<Int, ByteArray>,
    val sideFullness: Map<Int, ByteArray>
) {
    companion object {
        /** Rive 정면 이미지 자리 이름(FileAssetLoader가 이 이름으로 바꾼다). */
        val FRONT_SLOTS = setOf("face_layer", "eyes_open", "eyes_half", "eyes_closed", "hair_layer")

        /** 몸체마다 읽는 내장 레이어 12장(확장자 뺀 이름). */
        val BUILT_IN_LAYERS = listOf(
            "face", "hair", "eyes_open", "eyes_half", "eyes_closed",
            "side_face", "side_eyes", "side_hair",
            "front_fullness_1", "front_fullness_2", "side_fullness_1", "side_fullness_2"
        )

        fun assetDirectory(gender: AvatarGender): String = "avatars/" + gender.name.lowercase()

        /** 내장 기본 머리. 12장이 모두 1024×1024 PNG가 아니면 실패해 남녀 레이어가 섞인 채 남지 않는다. */
        fun loadBuiltIn(assets: AssetManager, gender: AvatarGender): AvatarVariantAssets {
            fun read(name: String): ByteArray {
                val path = "${assetDirectory(gender)}/$name.png"
                val bytes = assets.open(path).use { it.readBytes() }
                require(HeadResultPolicy.pngSize(bytes) == HeadResultPolicy.CANVAS to HeadResultPolicy.CANVAS) {
                    "avatar layer is not a 1024 PNG: $path"
                }
                return bytes
            }
            return AvatarVariantAssets(
                front = mapOf(
                    "face_layer" to read("face"),
                    "hair_layer" to read("hair"),
                    "eyes_open" to read("eyes_open"),
                    "eyes_half" to read("eyes_half"),
                    "eyes_closed" to read("eyes_closed")
                ),
                sideLayers = SideHeadLayers(
                    read("side_face"),
                    read("side_eyes"),
                    read("side_hair"),
                    SideNeckRegistration.builtInFor(gender)
                ),
                frontFullness = (1..2).associateWith { read("front_fullness_$it") },
                sideFullness = (1..2).associateWith { read("side_fullness_$it") }
            )
        }
    }
}
