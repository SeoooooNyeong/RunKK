package com.a207.runkk.app.core.avatar

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
