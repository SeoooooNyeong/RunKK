package com.a207.runkk.app.ui.avatar

import android.content.Context
import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.AvatarMotion
import com.a207.runkk.app.core.avatar.AvatarPrepareTrace
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.avatar.holdHomeRiveAvatar
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.MyAvatarPreparation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
 * - 기기 사본(9회차): 머리 레이어와 서기 합성은 내 회원 칸(files/avatar-look/m{회원})에 남아 두 번째 실행부터는 다시 합성하지 않는다.
 * - 시간 상한 없음(9회차, 사용자 결정 "다 받을 때까지 기다린다"): 성별을 모르면 프로필을 물어 알게 하고, 준비가 끝날 때까지 기다린다.
 * - 실패: 입력을 읽지 못하면(Failed)·프로필을 받지 못하면 연결 실패, 서기 합성이 예외로 끝나면 연결 실패로 돌려준다(시작 미리 받기가
 *   자동으로 다시 하고 그래도 안 되면 실패 화면·「다시 시도」). 로그아웃 상태면 인증 실패, 프로필에 성별이 없으면 거부(다시 해도 같다).
 * - 표식: AvatarPrepare total <ms>.
 */
@Singleton
internal class HomeAvatarPreparation(
    private val store: MyAvatarStore,
    private val holdRive: suspend (AvatarGender) -> Unit,
    private val scope: CoroutineScope,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    /** 홈 칸이 그릴 서기 모습을 합성한다(공용 캐시·기기 사본에 있으면 바로 끝). 실패하면 던진다. */
    private val prepareLook: suspend (RiveAvatarLook) -> Unit = {},
    /** 준비할 성별을 모를 때 프로필을 물어 알게 한다. 알게 되면 성공. */
    private val knowGender: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
) : MyAvatarPreparation {
    @Inject
    constructor(
        store: MyAvatarStore,
        accounts: AccountRepository,
        avatars: AvatarRepository,
        @ApplicationContext context: Context
    ) : this(
        store,
        { gender -> holdHomeRiveAvatar(context, gender) },
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        prepareLook = { look -> prepareMyLook(context, accounts.selfMemberId(), look, AvatarMotion.STAND) },
        knowGender = { genderOf(accounts, avatars) }
    )

    private var holdJob: Job? = null

    override fun isPrepared(): Boolean = store.state.value is MyAvatarState.Ready && holdJob?.isActive == true

    override suspend fun prepare(onStep: () -> Unit): Outcome<Unit> = withContext(main) {
        val startedAt = AvatarPrepareTrace.now()
        store.warm()
        if (store.state.value is MyAvatarState.Idle) return@withContext Outcome.Failure(AppError.Unauthorized)
        if (store.state.value is MyAvatarState.Failed) store.retryIfFailed()
        if (!store.isGenderKnown) {
            val known = knowGender()
            if (known is Outcome.Failure) return@withContext known
        }
        // 시간 상한 없이 끝날 때까지 기다린다(실패·로그아웃이면 그 결과).
        val done = store.state.first { it !is MyAvatarState.Loading }
        val ready = when (done) {
            is MyAvatarState.Ready -> done
            is MyAvatarState.Idle -> return@withContext Outcome.Failure(AppError.Unauthorized)
            else -> return@withContext Outcome.Failure(AppError.Network)
        }
        // 저장소는 합성 실패를 삼키므로(그려도 뷰가 다시 한다) 여기서 한 번 더 확인한다: 이미 했으면 바로 끝나고, 예외면 실패다.
        try {
            prepareLook(ready.look.rive)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext Outcome.Failure(AppError.Network)
        } catch (e: OutOfMemoryError) {
            return@withContext Outcome.Failure(AppError.Network)
        }
        onStep()
        holdJob?.cancel()
        holdJob = scope.launch { withTimeoutOrNull(RIVE_HOLD_MILLIS) { holdRive(ready.look.rive.gender) } }
        onStep()
        AvatarPrepareTrace.mark("total", startedAt)
        Outcome.Success(Unit)
    }

    internal companion object {
        /**
         * 홈 칸이 붙잡아 둔 Rive 파일을 가져갈 때까지 붙잡는 시간. 로딩이 끝난 뒤 워드마크 뜀·첫 화면 걷기를 지나 홈 칸이 그려지기에
         * 넉넉하다. 놓은 뒤에도 RivImports가 10초 더 기다린다. 로딩을 끊는 상한이 아니라 붙잡은 파일을 놓는 시간이다.
         */
        const val RIVE_HOLD_MILLIS = 30_000L

        /** 프로필 응답에 성별이 없을 때의 거부 사유(다시 해도 같다). */
        const val GENDER_UNKNOWN = "GENDER_UNKNOWN"

        /** 기기에 남긴 성별·지금 성별이 있으면 바로 성공, 없으면 프로필을 물어 성별을 알게 한다. */
        private suspend fun genderOf(accounts: AccountRepository, avatars: AvatarRepository): Outcome<Unit> {
            if (accounts.myGender.value != null || avatars.savedSelfGender() != null) return Outcome.Success(Unit)
            return when (val profile = accounts.profile(AccountRepository.SELF)) {
                is Outcome.Success ->
                    if (profile.value.gender != null) {
                        Outcome.Success(Unit)
                    } else {
                        Outcome.Failure(AppError.Rejected(GENDER_UNKNOWN))
                    }

                is Outcome.Failure -> profile
            }
        }
    }
}
