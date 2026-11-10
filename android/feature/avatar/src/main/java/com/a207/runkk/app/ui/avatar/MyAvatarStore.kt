package com.a207.runkk.app.ui.avatar

import android.content.Context
import android.util.Log
import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.avatar.preloadRiveAvatar
import com.a207.runkk.app.core.avatar.prepareRiveAvatarLook
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.PendingSelfiePhoto
import com.a207.runkk.app.domain.avatar.SelfieJobFollower
import com.a207.runkk.app.domain.avatar.SelfieProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 내 아바타 입력을 프로세스에 한 번 모아 두는 저장소(design-oo 05-30 [임시:D-성-103], [임시:D-성-104]). 홈·러닝 중 칸이 같은 입력을
 * 나눠 쓴다.
 *
 * - [warm]: 내 성별(`AccountRepository.myGender`)을 구독한다. 모르면(null) Loading에 머물고 그림을 고르지 않는다(FR-162 CR-86).
 *   알면 그 성별의 riv를 미리 읽고([preloadRiveAvatar]) 아바타·내 러너·상점 목록과 생성 머리를 모은 뒤 서기 모습을 미리 합성해
 *   ([prepareRiveAvatarLook]) Ready로 둔다. 성별이 바뀌면 앞 그림을 버리고 다시 준비한다.
 * - 아바타나 내 러너를 못 읽으면 Failed다. 미리 읽기·미리 합성 실패는 준비 실패로 보지 않는다(뷰가 다시 읽고 합성하며, 그래도 안 되면
 *   칸이 성별 기본 그림으로 바꾼다).
 * - 한 번에 준비 작업 하나만 돈다. 새 [refresh]·성별 바뀜은 앞 작업을 취소하고 세대 번호를 올리며, 결과는 세대가 같을 때만 쓴다
 *   (겹치면 마지막 요청이 이긴다). [clear](로그아웃)도 세대를 올리므로 늦게 끝난 앞 회원의 결과가 다음 회원 화면에 보이지 않는다.
 * - 모든 공개 함수는 주 스레드에서 부른다(작업도 주 스레드에서 이어지고 무거운 일은 각 함수가 IO·Default로 넘긴다).
 */
@Singleton
class MyAvatarStore internal constructor(
    private val avatars: AvatarRepository,
    private val myGender: Flow<Gender?>,
    private val heads: SelfieHeads,
    private val preload: suspend (AvatarGender) -> Unit,
    private val prepare: suspend (RiveAvatarLook) -> Unit,
    private val scope: CoroutineScope,
    /** 로그인 상태(SessionRepository.loggedIn). 참 → 거짓이면 비운다([임시:D-성-161], 리뷰 R2-7). */
    private val loggedIn: StateFlow<Boolean> = MutableStateFlow(true),
    /** 로그아웃 뒷정리(보낸 사진 지움, 리뷰 R2-11). */
    private val onLoggedOut: suspend () -> Unit = {},
    /** 기기에 남은 셀카 작업 따라가기 잇기(리뷰 R2-3, [임시:D-성-163]). */
    private val resumeSelfie: suspend () -> Unit = {}
) {
    @Inject
    constructor(
        avatars: AvatarRepository,
        accounts: AccountRepository,
        heads: SelfieHeads,
        sessions: SessionRepository,
        photo: PendingSelfiePhoto,
        follower: SelfieJobFollower,
        @ApplicationContext context: Context
    ) : this(
        avatars = avatars,
        myGender = accounts.myGender,
        heads = heads,
        preload = { gender -> preloadRiveAvatar(context, gender) },
        prepare = { look ->
            prepareRiveAvatarLook(
                context,
                look.gender,
                look.outfit,
                look.bodyShape,
                look.runningAmount,
                look.headLayers
            )
        },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + LOG_FAILURE),
        loggedIn = sessions.loggedIn,
        onLoggedOut = { photo.clear() },
        resumeSelfie = { avatars.pendingSelfieJobId()?.let(follower::resume) }
    )

    /** 셀카 작업 진행(홈 칸의 진행 배지가 듣는다, 피그마 AV-7 4). */
    val selfieProgress: Flow<SelfieProgress> get() = avatars.selfieProgress()

    private val _state = MutableStateFlow<MyAvatarState>(MyAvatarState.Idle)
    val state: StateFlow<MyAvatarState> = _state.asStateFlow()

    private var generation = 0
    private var genderJob: Job? = null
    private var selfieJob: Job? = null
    private var work: Job? = null
    private var gender: AvatarGender? = null

    /** 마지막으로 본 로그인 상태(로그인 → 로그아웃 바뀜을 가린다). */
    private var wasLoggedIn = loggedIn.value

    init {
        scope.launch {
            loggedIn
                .catch { /* 세션 흐름이 끊겨도 앱을 멈추지 않는다(리뷰 R2-8). */ }
                .collect { now ->
                    val before = wasLoggedIn
                    wasLoggedIn = now
                    if (before && !now) {
                        clear()
                        quietly { onLoggedOut() }
                    }
                }
        }
    }

    /** 미리 준비를 시작한다(로그인한 동안 Overlay가 처음 그려질 때, 홈 칸이 먼저 그려질 때). 이미 시작했으면 아무것도 안 한다. */
    fun warm() {
        // 로그아웃 상태면(clear 뒤 늦게 온 warm) 앞 회원 그림을 다시 준비하지 않는다(리뷰 R2-7).
        if (genderJob?.isActive == true || !loggedIn.value) return
        _state.value = MyAvatarState.Loading
        genderJob = scope.launch {
            myGender
                .map { it?.let(::avatarGenderOf) }
                .distinctUntilChanged()
                .catch { _state.value = MyAvatarState.Failed }
                .collect(::onGender)
        }
        // 셀카 화면을 다시 열지 않아도 남은 작업이 이어지게 따라가기를 잇는다(KEEP이라 돌고 있으면 그대로다).
        scope.launch { quietly { resumeSelfie() } }
        // 셀카 결과는 화면이 없어도 워커가 적용한다. 성공을 받으면 바뀐 머리로 다시 준비한다([임시:DR-성-13]).
        selfieJob = scope.launch {
            // 첫 값은 지금 상태라 버린다(앞에서 이미 적용한 성공이면 준비가 이미 반영한다).
            avatars.selfieProgress()
                .drop(1)
                .filterIsInstance<SelfieProgress.Succeeded>()
                .distinctUntilChanged()
                .catch { /* 진행을 못 들어도 칸은 그대로 둔다(리뷰 R2-8). */ }
                .collect { refresh() }
        }
    }

    /**
     * 입력을 다시 읽는다(착용 저장, 배경 바꿈, 셀카 결과 적용 뒤). 그동안 앞 Ready는 그대로 둔다(로딩 표시 없음). Failed였으면 Loading으로
     * 두고 다시 시도한다. 성별을 아직 모르면 아무것도 안 한다(성별을 알면 어차피 준비한다).
     */
    fun refresh() {
        val known = gender ?: return
        if (_state.value is MyAvatarState.Failed) _state.value = MyAvatarState.Loading
        start(known)
    }

    /** 칸 진입(홈): 준비가 끝난 상태(Ready·Failed)면 다시 읽고, 준비 중이면 그대로 둔다(데우는 작업을 끊지 않는다). */
    fun revalidate() {
        val current = _state.value
        if (current is MyAvatarState.Ready || current is MyAvatarState.Failed) refresh()
    }

    /** 칸 진입(러닝 중): 실패했으면 한 번 다시 시도한다. 측정 중에는 다시 읽기를 늘리지 않는다. */
    fun retryIfFailed() {
        if (_state.value is MyAvatarState.Failed) refresh()
    }

    /** 로그아웃: 진행 중 작업과 성별 구독을 멈추고 세대를 올려 늦은 결과를 버린 뒤 Idle로 둔다. 합성 캐시는 비우지 않는다([임시:D-성-139]). */
    fun clear() {
        generation++
        genderJob?.cancel()
        selfieJob?.cancel()
        work?.cancel()
        genderJob = null
        selfieJob = null
        work = null
        gender = null
        _state.value = MyAvatarState.Idle
    }

    private fun onGender(next: AvatarGender?) {
        gender = next
        // 다른 성별 그림을 잠깐이라도 보이지 않게 앞 결과를 버린다
        _state.value = MyAvatarState.Loading
        if (next == null) {
            generation++
            work?.cancel()
            return
        }
        start(next)
    }

    private fun start(target: AvatarGender) {
        work?.cancel()
        val mine = ++generation
        work = scope.launch {
            val next =
                try {
                    load(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 입력 읽기가 예외를 던져도 앱을 멈추지 않고 실패로 둔다(리뷰 R2-8).
                    MyAvatarState.Failed
                }
            if (mine == generation) _state.value = next
        }
    }

    private suspend fun load(target: AvatarGender): MyAvatarState = coroutineScope {
        val riv = launch { quietly { preload(target) } }
        val runnerCall = async { avatars.runner() }
        val shopCall = async { avatars.shop(null) }
        val avatar = avatars.avatar()
        val runner = runnerCall.await()
        if (avatar !is Outcome.Success || runner !is Outcome.Success) {
            shopCall.cancel()
            return@coroutineScope MyAvatarState.Failed
        }
        val items = shopCall.await().getOrNull()?.items.orEmpty()
        val face = heads.look(avatar.value.faceKey)
        val rive = RiveAvatarLook(
            gender = target,
            outfit = outfitOf(avatar.value.equipped, items),
            bodyShape = bodyShapeOf(runner.value.bodyVariant),
            runningAmount = runner.value.runAmount,
            headLayers = face.headLayers
        )
        riv.join()
        quietly { prepare(rive) }
        MyAvatarState.Ready(MyAvatarLook(rive, avatar.value.backgroundKey, avatar.value.faceKey, face.faceLayers))
    }

    /** 미리 읽기·미리 합성은 앞당기기일 뿐이라 실패해도 준비를 막지 않는다(취소는 그대로 넘긴다). */
    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 뷰가 같은 파일을 다시 읽고 합성한다. 그래도 실패하면 칸이 onError로 기본 그림을 보인다.
        } catch (e: OutOfMemoryError) {
            // 합성 텍스처를 만들 메모리가 없다. 뷰가 다시 시도한다.
        }
    }
}

/** 저장소 작업에서 놓친 예외를 기록만 하고 앱을 멈추지 않는다(리뷰 R2-8). */
private val LOG_FAILURE =
    CoroutineExceptionHandler { _, e -> Log.w("MyAvatarStore", "내 아바타 준비 실패: ${e.javaClass.simpleName}") }

/** 프로필 성별 → Rive 몸체(FR-162: 여성이면 여 몸체, 남성·비공개는 남 몸체). */
private fun avatarGenderOf(gender: Gender): AvatarGender =
    if (gender == Gender.FEMALE) AvatarGender.FEMALE else AvatarGender.MALE
