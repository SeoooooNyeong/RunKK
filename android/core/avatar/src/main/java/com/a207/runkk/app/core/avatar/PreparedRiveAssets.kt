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
    /** [motion]의 Rive 파일 바이트([RiveAvatarScene.rawResource]). */
    fun riv(motion: AvatarMotion): ByteArray = scenes.getValue(motion)

    companion object {
        /** 막는 읽기다. IO 스레드에서 부른다. */
        fun read(context: Context, gender: AvatarGender): PreparedRiveAssets {
            val resources = context.resources
            fun raw(id: Int): ByteArray = resources.openRawResource(id).use { it.readBytes() }
            val scenes = EnumMap<AvatarMotion, ByteArray>(AvatarMotion::class.java)
            AvatarMotion.entries.forEach { scenes[it] = raw(RiveAvatarScene.rawResource(gender, it)) }
            return PreparedRiveAssets(
                gender,
                AvatarVariantAssets.loadBuiltIn(context.assets, gender),
                raw(R.raw.avatar_legacy_standing_body),
                scenes
            )
        }
    }
}
