package com.a207.runkk.app.ui.avatar

import android.content.Context
import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.holdHomeRiveAvatar
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.MyAvatarPreparation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 홈 큰 아바타 미리 준비(S15P31A207-345 2차, 포트 domain.avatar.MyAvatarPreparation). 시작 로딩 화면에서 홈 칸과 같은 [MyAvatarStore]를
 * 데워(아바타·내 러너·상점 입력 모으기, 내 머리 층, 서기 합성 텍스처를 프로세스 공용 캐시에 넣은 뒤 Ready) 홈 칸이 같은 입력·같은 머리
 * 인스턴스로 그리게 하고, 홈 칸이 쓸 서기 Rive 파일을 미리 가져와 붙잡는다([holdHomeRiveAvatar], [RIVE_HOLD_MILLIS] 동안).
 *
 * - 단계: 입력 모으기·서기 합성(Ready) 1, Rive 첫 판 붙잡기 1.
 * - 실패: 입력을 읽지 못하면(Failed) 연결 실패로 돌려준다(시작 미리 받기는 앞당기기라 건너뛴다). 성별을 오래 모르면([PREPARE_TIMEOUT_MILLIS])
 *   기다리지 않고 실패로 돌려준다(홈 칸이 스스로 다시 준비한다). 로그아웃 상태면 인증 실패다.
 */
@Singleton
internal class HomeAvatarPreparation(
    private val store: MyAvatarStore,
    private val holdRive: suspend (AvatarGender) -> Unit,
    private val scope: CoroutineScope,
    private val main: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main.immediate
) : MyAvatarPreparation {
    @Inject
    constructor(store: MyAvatarStore, @ApplicationContext context: Context) : this(
        store,
        { gender -> holdHomeRiveAvatar(context, gender) },
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    )

    private var holdJob: Job? = null

    override fun isPrepared(): Boolean = store.state.value is MyAvatarState.Ready && holdJob?.isActive == true

    override suspend fun prepare(onStep: () -> Unit): Outcome<Unit> = withContext(main) {
        store.warm()
        if (store.state.value is MyAvatarState.Idle) return@withContext Outcome.Failure(AppError.Unauthorized)
        if (store.state.value is MyAvatarState.Failed) store.retryIfFailed()
        val done = withTimeoutOrNull(PREPARE_TIMEOUT_MILLIS) {
            store.state.first { it is MyAvatarState.Ready || it is MyAvatarState.Failed }
        }
        val ready = done as? MyAvatarState.Ready ?: return@withContext Outcome.Failure(AppError.Network)
        onStep()
        holdJob?.cancel()
        holdJob = scope.launch { withTimeoutOrNull(RIVE_HOLD_MILLIS) { holdRive(ready.look.rive.gender) } }
        onStep()
        Outcome.Success(Unit)
    }

    internal companion object {
        /**
         * 홈 칸이 붙잡아 둔 Rive 파일을 가져갈 때까지 붙잡는 시간. 로딩이 끝난 뒤 워드마크 뜀·첫 화면 걷기(최대 4초)를 지나 홈 칸이 그려지기에
         * 넉넉하다. 놓은 뒤에도 RivImports가 10초 더 기다린다.
         */
        const val RIVE_HOLD_MILLIS = 30_000L

        /** 성별·입력을 기다리는 가장 긴 시간(앞당기기라 시작을 오래 막지 않는다). */
        const val PREPARE_TIMEOUT_MILLIS = 15_000L
    }
}
