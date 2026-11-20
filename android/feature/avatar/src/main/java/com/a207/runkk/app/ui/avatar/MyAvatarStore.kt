package com.a207.runkk.app.ui.avatar

import android.content.Context
import android.util.Log
import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.AvatarLookDisk
import com.a207.runkk.app.core.avatar.AvatarMotion
import com.a207.runkk.app.core.avatar.AvatarPrepareTrace
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.avatar.preloadRiveAvatar
import com.a207.runkk.app.core.avatar.prepareRiveAvatarLook
import com.a207.runkk.app.core.designsystem.SelfFaceTone
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 내 아바타 입력을 프로세스에 한 번 모아 두는 저장소(design-oo 05-30 D-735, D-736). 홈·러닝 중 칸이 같은 입력을
 * 나눠 쓴다.
 *
 * - [warm]: 내 성별(`AccountRepository.myGender`)을 구독한다. 모르면(null) Loading에 머물고 그림을 고르지 않는다(FR-162 CR-86).
 *   알면 그 성별의 riv를 미리 읽고([preloadRiveAvatar]) 아바타·내 러너·상점 목록과 생성 머리를 모은 뒤 서기 모습을 미리 합성해
 *   ([prepareRiveAvatarLook]) Ready로 둔다. Ready 뒤에 같은 모습의 달리기 동작(motion = RUN)도 이어서 미리 합성해 측정 화면 칸이
 *   Rive 파일 읽기·텍스처 합성을 기다리지 않게 한다(D-813). 달리기 준비는 Ready를 늦추지 않는다. 성별이 바뀌면 앞 그림을
 *   버리고 다시 준비한다.
 * - 기기에 남긴 마지막 성별(AvatarRepository.savedSelfGender)이 있으면 프로필(성별) 응답을 기다리지 않고 그 성별로 바로 준비를
 *   시작한다(Jira 307). 내 머리는 기기에 남긴 층을 쓰므로 받지 않는다. 프로필이 다른 성별을 주면 다시 준비하고, 같은 성별이면 그대로 둔다.
 *   알게 된 성별은 기기에 남긴다.
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
    /** 셀카 머리 캐시. 전신 정지 그림 칸이 다른 러너 머리도 이것으로 받는다(S15P31A207-300). */
    internal val heads: SelfieHeads,
    private val preload: suspend (AvatarGender) -> Unit,
    /** 모습 미리 합성(동작별, [prepareRiveAvatarLook]). */
    private val prepare: suspend (RiveAvatarLook, AvatarMotion) -> Unit,
    private val scope: CoroutineScope,
    /** 로그인 상태(SessionRepository.loggedIn). 참 → 거짓이면 비운다(D-793, 리뷰 R2-7). */
    private val loggedIn: StateFlow<Boolean> = MutableStateFlow(true),
    /** 로그아웃 뒷정리(보낸 사진 지움, 리뷰 R2-11). */
    private val onLoggedOut: suspend () -> Unit = {},
    /** 기기에 남은 셀카 작업 따라가기 잇기(리뷰 R2-3, D-795). */
    private val resumeSelfie: suspend () -> Unit = {},
    /** 기기에 남은 셀카 작업을 앞 화면에서 한 번 직접 확인(D-820). */
    private val checkPending: suspend () -> Unit = {},
    /** 기기에 남긴 마지막 내 얼굴 그을림(앱을 켠 첫 화면부터 쓴다, D-854). */
    private val savedTone: () -> SelfFaceTone = { SelfFaceTone() },
    /** 내 얼굴 그을림을 기기에 남긴다. 로그아웃이면 빈 값을 남긴다. */
    private val saveTone: (SelfFaceTone) -> Unit = {},
    /**
     * 기기에 남긴 내 마지막 얼굴 키(AvatarRepository.savedSelfFaceKey). [warm]이 프로필(성별) 응답을 기다리지 않고 이 키로 내 머리를
     * 받아(디스크 캐시면 바로) 합성을 시작한다(S15P31A207-300).
     */
    private val savedFaceKey: () -> String? = { null },
    /** 기기에 남긴 내 마지막 성별(AvatarRepository.savedSelfGender). 있으면 [warm]이 프로필 응답을 기다리지 않는다(Jira 307). */
    private val savedGender: () -> Gender? = { null },
    /** 알게 된 내 성별을 기기에 남긴다(AvatarRepository.rememberSelfGender). */
    private val saveGender: suspend (Gender) -> Unit = {}
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
        // 합성 결과는 내 회원 칸에 남겨 앱을 다시 켜도 같은 입력이면 다시 합성하지 않는다(S15P31A207-345 9회차).
        prepare = { look, motion -> prepareMyLook(context, accounts.selfMemberId(), look, motion) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + LOG_FAILURE),
        loggedIn = sessions.loggedIn,
        onLoggedOut = { photo.clear() },
        resumeSelfie = { avatars.pendingSelfieJobId()?.let(follower::resume) },
        checkPending = { follower.checkNow() },
        savedTone = { FaceTonePrefs.read(context) },
        saveTone = { FaceTonePrefs.write(context, it) },
        savedFaceKey = avatars::savedSelfFaceKey,
        savedGender = avatars::savedSelfGender,
        saveGender = avatars::rememberSelfGender
    )

    /** 셀카 작업 진행(홈 칸의 진행 배지가 듣는다, 피그마 AV-7 4). */
    val selfieProgress: Flow<SelfieProgress> get() = avatars.selfieProgress()

    private val _state = MutableStateFlow<MyAvatarState>(MyAvatarState.Idle)
    val state: StateFlow<MyAvatarState> = _state.asStateFlow()

    /**
     * 내 얼굴 그을림(얼굴 원이 쓴다, D-854). 몸 합성(Ready)을 기다리지 않는다: 아바타·러너 응답을 받자마자 바꾸고 기기에
     * 남겨, 앱을 다시 켜면 첫 화면부터 홈·랭킹·크루런의 내 얼굴이 같은 톤이다.
     */
    private val _faceTone = MutableStateFlow(savedTone())
    val faceTone: StateFlow<SelfFaceTone> = _faceTone.asStateFlow()

    private var generation = 0
    private var genderJob: Job? = null
    private var selfieJob: Job? = null
    private var work: Job? = null
    private var gender: AvatarGender? = null

    /** 마지막으로 본 로그인 상태(로그인 → 로그아웃 바뀜을 가린다). */
    private var wasLoggedIn = loggedIn.value

    /** 준비할 성별을 아는가(기기에 남긴 성별이나 프로필 성별). 모르면 [warm] 뒤에도 성별을 알 때까지 Loading이다. */
    val isGenderKnown: Boolean get() = gender != null

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
        // 내 머리는 프로필(성별) 응답을 기다리지 않고 마지막 얼굴 키로 바로 받아 합성해 둔다(S15P31A207-300). 준비([load])가 같은
        // 키를 찾으면 SelfieHeads가 받는 중인 것을 기다리거나 담아 둔 것을 준다. 키가 바뀌었으면 이 결과는 쓰이지 않을 뿐이다.
        savedFaceKey()?.takeIf(::isSelfieHead)?.let { key -> scope.launch { quietly { heads.look(key) } } }
        // 성별도 기기에 남긴 것이 있으면 프로필 응답을 기다리지 않고 그 성별로 바로 준비한다(Jira 307).
        savedGender()?.let(::avatarGenderOf)?.let { early ->
            gender = early
            start(early)
        }
        genderJob = scope.launch {
            myGender
                .onEach { known -> if (known != null) quietly { saveGender(known) } }
                .map { it?.let(::avatarGenderOf) }
                .distinctUntilChanged()
                .catch { _state.value = MyAvatarState.Failed }
                .collect(::onGender)
        }
        // 셀카 화면을 다시 열지 않아도 남은 작업이 이어지게 따라가기를 잇는다(KEEP이라 돌고 있으면 그대로다). 이어서 바로 한 번
        // 직접 확인한다: 서버 current로 들여온 작업(재설치·다른 기기 접수)은 앞 화면 확인이 들여오기 전에 끝나 워커만 남았고, 워커가
        // 돌지 못하는 망에서는 「얼굴 만드는 중」이 남았다(D-855). 확인은 워커와 겹쳐도 한 번만 처리한다.
        scope.launch {
            quietly { resumeSelfie() }
            quietly { checkPending() }
        }
        // 셀카 결과는 화면이 없어도 워커가 적용한다. 성공을 받으면 바뀐 머리로 다시 준비한다(DR-13).
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
     * 홈 칸이 보일 때(앱이 다시 앞으로 온 때 포함): 기기에 남은 셀카 작업을 워커와 따로 한 번 직접 확인한다(D-820). 워커가
     * 네트워크 제약(인터넷 확인된 연결)을 못 채워 돌지 않아도 끝난 작업의 「얼굴 만드는 중」이 남지 않는다. 로그아웃 상태면 묻지 않는다.
     */
    fun checkSelfie() {
        if (!loggedIn.value) return
        scope.launch { quietly { checkPending() } }
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

    /** 로그아웃: 진행 중 작업과 성별 구독을 멈추고 세대를 올려 늦은 결과를 버린 뒤 Idle로 둔다. 합성 캐시는 비우지 않는다(D-771). */
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
        // 다음 회원에게 앞 회원 얼굴 톤을 남기지 않는다
        _faceTone.value = SelfFaceTone()
        saveTone(SelfFaceTone())
    }

    private fun onGender(next: AvatarGender?) {
        val current = gender
        // 남긴 성별로 이미 준비하고 있으면: 프로필을 아직 모르는 값(null)은 기다리고, 같은 성별이면 그대로 둔다.
        if (current != null && (next == null || next == current)) return
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
            if (mine != generation) return@launch
            _state.value = next
            // 러닝 칸이 기다리지 않게 같은 모습의 달리기 동작도 미리 합성한다(D-813). 다음 준비가 이 작업을 취소한다.
            if (next is MyAvatarState.Ready) quietly { prepare(next.look.rive, AvatarMotion.RUN) }
        }
    }

    private suspend fun load(target: AvatarGender): MyAvatarState = coroutineScope {
        val rivAt = AvatarPrepareTrace.now()
        val riv = launch {
            quietly { preload(target) }
            AvatarPrepareTrace.mark("riv-read", rivAt, "gender=$target")
        }
        val inputsAt = AvatarPrepareTrace.now()
        val runnerCall = async { avatars.runner() }
        val shopCall = async { avatars.shop(null) }
        val avatar = avatars.avatar()
        val runner = runnerCall.await()
        AvatarPrepareTrace.mark("inputs", inputsAt)
        if (avatar !is Outcome.Success || runner !is Outcome.Success) {
            shopCall.cancel()
            return@coroutineScope MyAvatarState.Failed
        }
        // 얼굴 톤은 몸 합성을 기다리지 않고 바로 바꾼다(D-854)
        val tone = SelfFaceTone(avatar.value.faceKey, runner.value.runAmount)
        if (tone != _faceTone.value) {
            _faceTone.value = tone
            saveTone(tone)
        }
        val items = shopCall.await().getOrNull()?.items.orEmpty()
        val headAt = AvatarPrepareTrace.now()
        val face = heads.look(avatar.value.faceKey)
        AvatarPrepareTrace.mark("head", headAt)
        val rive = RiveAvatarLook(
            gender = target,
            outfit = outfitOf(avatar.value.equipped, items),
            bodyShape = bodyShapeOf(runner.value.bodyVariant),
            runningAmount = runner.value.runAmount,
            headLayers = face.headLayers
        )
        val rivWaitAt = AvatarPrepareTrace.now()
        riv.join()
        AvatarPrepareTrace.mark("riv-wait", rivWaitAt)
        val toneAt = AvatarPrepareTrace.now()
        quietly { prepare(rive, AvatarMotion.STAND) }
        AvatarPrepareTrace.mark("tone", toneAt, "motion=STAND")
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

/**
 * 내 모습 합성(홈 칸과 같은 공용 캐시). [member] 회원 칸에 결과를 남기고, 같은 입력의 사본이 있으면 합성하지 않고 그것을 올린다
 * (S15P31A207-345 9회차). 로그아웃 상태(null)면 기기에 남기지 않는다.
 */
internal suspend fun prepareMyLook(context: Context, member: Long?, look: RiveAvatarLook, motion: AvatarMotion) =
    prepareRiveAvatarLook(
        context,
        look.gender,
        look.outfit,
        look.bodyShape,
        look.runningAmount,
        look.headLayers,
        motion,
        member?.let { AvatarLookDisk.member(context, it) }
    )

/** 프로필 성별 → Rive 몸체(FR-162: 여성이면 여 몸체, 남성·비공개는 남 몸체). */
private fun avatarGenderOf(gender: Gender): AvatarGender =
    if (gender == Gender.FEMALE) AvatarGender.FEMALE else AvatarGender.MALE
