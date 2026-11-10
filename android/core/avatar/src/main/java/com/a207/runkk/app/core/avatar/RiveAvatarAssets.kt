package com.a207.runkk.app.core.avatar

import android.content.Context
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** 남녀별 [PreparedRiveAssets]를 [io]에서 한 번 읽어 프로세스에 둔다(아바타 화면을 다시 열어도 다시 읽지 않는다). */
internal class RiveAvatarAssets(private val io: CoroutineDispatcher) {
    private val prepared = ConcurrentHashMap<AvatarGender, PreparedRiveAssets>()

    /** 이미 읽어 둔 [gender] 자산. 없으면 null. */
    fun cached(gender: AvatarGender): PreparedRiveAssets? = prepared[gender]

    /** [gender] 자산. 처음이면 [io]에서 읽는다. 읽지 못하면 그 예외(IOException, 형식이 틀린 내장 PNG면 IllegalArgumentException)다. */
    suspend fun load(context: Context, gender: AvatarGender): PreparedRiveAssets {
        prepared[gender]?.let { return it }
        val read = withContext(io) { PreparedRiveAssets.read(context.applicationContext, gender) }
        return prepared.putIfAbsent(gender, read) ?: read
    }

    companion object {
        /** 앱 프로세스 공용. */
        val shared = RiveAvatarAssets(Dispatchers.IO)
    }
}
