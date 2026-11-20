package com.a207.runkk.app.core.avatar

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * 남녀별 [PreparedRiveAssets]를 [io]에서 한 번 읽어 프로세스에 둔다(아바타 화면을 다시 열어도 다시 읽지 않는다).
 *
 * 앞 읽기가 끝나기 전에 같은 성별을 또 물으면 새로 읽지 않고 그 읽기를 함께 기다린다(S15P31A207-345 10회차: 시작 로딩이 뒤에서
 * 읽어 두는 동안 합성·홈 칸이 물어도 파일을 두 번 읽지 않는다). 읽기는 묻는 쪽이 취소돼도 끝까지 해 둔다.
 */
internal class RiveAvatarAssets(
    private val io: CoroutineDispatcher,
    private val read: (Context, AvatarGender) -> PreparedRiveAssets = PreparedRiveAssets::read
) {
    private val prepared = ConcurrentHashMap<AvatarGender, PreparedRiveAssets>()
    private val reading = ConcurrentHashMap<AvatarGender, Deferred<PreparedRiveAssets>>()
    private val scope = CoroutineScope(SupervisorJob() + io)

    /** 이미 읽어 둔 [gender] 자산. 없으면 null. */
    fun cached(gender: AvatarGender): PreparedRiveAssets? = prepared[gender]

    /** [gender] 자산. 처음이면 [io]에서 읽는다. 읽지 못하면 그 예외(IOException, 형식이 틀린 내장 PNG면 IllegalArgumentException)다. */
    suspend fun load(context: Context, gender: AvatarGender): PreparedRiveAssets {
        prepared[gender]?.let { return it }
        val app = context.applicationContext
        val job = reading.computeIfAbsent(gender) {
            scope.async(start = CoroutineStart.LAZY) {
                val made = read(app, gender)
                prepared.putIfAbsent(gender, made) ?: made
            }
        }
        job.start()
        try {
            return job.await()
        } finally {
            // 끝난 읽기는 놓는다(성공이면 prepared에 있고, 실패면 다음 요청이 다시 읽는다).
            if (job.isCompleted) reading.remove(gender, job)
        }
    }

    companion object {
        /** 앱 프로세스 공용. */
        val shared = RiveAvatarAssets(Dispatchers.IO)
    }
}
