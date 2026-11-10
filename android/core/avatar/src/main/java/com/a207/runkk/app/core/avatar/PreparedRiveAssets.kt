package com.a207.runkk.app.core.avatar

import android.content.Context
import java.util.EnumMap

/**
 * 한 몸체(남녀)를 그리는 데 드는 내장 바이트: 동작별 Rive 파일(.riv, 최대 5MB), 내장 기본 머리 12장, 앞 단계 서기 몸 PNG.
 * [RiveAvatarView.render]는 준비된 이 바이트만 쓰고 주 스레드에서 파일을 읽지 않는다. 읽기 전용이다.
 */
internal class PreparedRiveAssets(
    val gender: AvatarGender,
    val builtIn: AvatarVariantAssets,
    val legacyStandingBody: ByteArray,
    private val scenes: Map<AvatarMotion, ByteArray>
) {
    private val embedded = AvatarMotion.entries.associateWith { motion ->
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RivEmbeddedImages.read(riv(motion)) }
    }

    /** [motion]의 Rive 파일 바이트([RiveAvatarScene.rawResource], 검수 바탕·내장 그림자를 투명하게 한 사본 [hideBuiltInShapes]). */
    fun riv(motion: AvatarMotion): ByteArray = scenes.getValue(motion)

    /**
     * [motion] riv에 내장된 이미지(이름 → PNG, [RivEmbeddedImages]). 로더가 받는 원본과 같다. 처음 물을 때 한 번 읽어 둔다(막는 읽기,
     * 미리 준비가 합성 스레드에서 부른다).
     */
    fun embeddedImages(motion: AvatarMotion): Map<String, ByteArray> = embedded.getValue(motion).value

    companion object {
        /**
         * 방금 읽은 riv 바이트에서 검수용 바탕(DV-성-3, [RivReviewBackground])과 내장 바닥 그림자([RivGroundShadow],
         * [임시:D-성-107])를 투명하게 한다. [riv] 배열을 바로 고치고 그것을 돌려준다.
         */
        fun hideBuiltInShapes(riv: ByteArray): ByteArray = riv.also {
            RivReviewBackground.hideInPlace(it)
            RivGroundShadow.hideInPlace(it)
        }

        /** 막는 읽기다. IO 스레드에서 부른다. */
        fun read(context: Context, gender: AvatarGender): PreparedRiveAssets {
            val resources = context.resources
            // 의상 묶음 목록·PNG(assets/outfits)를 합성 스레드가 읽게 한 번 연다(D-성-110).
            OutfitLibrary.install(context.applicationContext.assets)
            fun raw(id: Int): ByteArray = resources.openRawResource(id).use { it.readBytes() }
            val scenes = EnumMap<AvatarMotion, ByteArray>(AvatarMotion::class.java)
            // 검수용 베이지 바탕 도형은 투명하게 해 카드 배경이 보이게 하고, 바닥 그림자는 앱이 그린다(DV-성-3, D-성-107,
            // 파일은 승인본 그대로).
            AvatarMotion.entries.forEach {
                scenes[it] = hideBuiltInShapes(raw(RiveAvatarScene.rawResource(gender, it)))
            }
            return PreparedRiveAssets(
                gender,
                AvatarVariantAssets.loadBuiltIn(context.assets, gender),
                raw(R.raw.avatar_legacy_standing_body),
                scenes
            )
        }
    }
}
