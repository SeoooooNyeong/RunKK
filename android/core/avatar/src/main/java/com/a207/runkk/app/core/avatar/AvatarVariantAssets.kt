package com.a207.runkk.app.core.avatar

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 한 몸체(남녀)에 넣을 머리 원본: 정면 다섯 층(face_layer·hair_layer·eyes_open·eyes_half·eyes_closed), 측면 세 층, 체형 1·2단계
 * 볼 레이어(정면·측면). 내장 기본 머리는 assets/avatars/{female,male}/에서 읽고, 생성 머리는 [GeneratedAvatarLayers]가 만든다.
 * 두 머리 모두 정면 얼굴 아래에 몸 쪽 목 층을 굽고, 측면은 합성할 때 깐다([NeckUnderlay]).
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
            val face = read("face")
            val skin = builtInSkin(face, gender)
            return AvatarVariantAssets(
                front = mapOf(
                    "face_layer" to withNeck(face, NeckUnderlay(BodyNeck.front(gender), skin)),
                    "hair_layer" to read("hair"),
                    "eyes_open" to read("eyes_open"),
                    "eyes_half" to read("eyes_half"),
                    "eyes_closed" to read("eyes_closed")
                ),
                sideLayers = SideHeadLayers(
                    read("side_face"),
                    read("side_eyes"),
                    read("side_hair"),
                    SideNeckRegistration.builtInFor(gender),
                    NeckUnderlay(BodyNeck.side(gender), skin)
                ),
                frontFullness = (1..2).associateWith { read("front_fullness_$it") },
                sideFullness = (1..2).associateWith { read("side_fullness_$it") }
            )
        }

        /** 기본 머리의 피부색: 정면 얼굴의 몸체 턱 위 피부 중앙값([GeneratedHeadGeometry.skinColor], 셀카 메타가 없을 때와 같은 규칙). */
        private fun builtInSkin(face: ByteArray, gender: AvatarGender): Int {
            val bitmap = decode(face)
            try {
                val size = HeadResultPolicy.CANVAS
                val pixels = IntArray(size * size).also { bitmap.getPixels(it, 0, size, 0, 0, size, size) }
                val target = HeadTarget.front(gender)
                return GeneratedHeadGeometry.skinColor(pixels, size, size, target.centerX, target.chinY)
            } finally {
                bitmap.recycle()
            }
        }

        /** 정면 얼굴 아래에 몸 쪽 목 층을 구운 PNG. */
        private fun withNeck(face: ByteArray, underlay: NeckUnderlay): ByteArray {
            val bitmap = decode(face)
            try {
                val necked = underlay.under(bitmap)
                try {
                    return AvatarImages.encode(necked)
                } finally {
                    necked.recycle()
                }
            } finally {
                bitmap.recycle()
            }
        }

        private fun decode(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("unreadable avatar layer")
    }
}
