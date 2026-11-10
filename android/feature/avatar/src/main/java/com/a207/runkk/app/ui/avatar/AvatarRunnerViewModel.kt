package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.BackgroundUnlockSeenStore
import com.a207.runkk.app.domain.avatar.SelfieProgress
import com.a207.runkk.app.domain.record.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 내 러너(피그마 제안 B안, W8): 아바타(GET /avatar)·내 러너(GET /avatar/runner)·착용 이름용 상점 목록을 함께 읽고, 셀카 머리면 렌더러 머리
 * 레이어를 얼굴마다 한 번 만든다([SelfieHeads]). 레벨은 본인 요약, 배경 수는 배경 목록에서 읽는다. 들어올 때마다 새로 연 배경이 있으면
 * 해금 팝업(H-6)을 띄운다(기기 기록 [BackgroundUnlockSeenStore]). 셀카 작업 진행을 들어 얼굴 줄에 보이고, 셀카가 끝나 얼굴이 바뀌면
 * 다시 읽는다(design-oo 05-30 [임시:D-성-118]).
 */
@HiltViewModel
class AvatarRunnerViewModel internal constructor(
    private val avatars: AvatarRepository,
    private val seenStore: BackgroundUnlockSeenStore,
    private val clock: Clock,
    private val heads: SelfieHeads,
    private val myAvatar: MyAvatarStore,
    /** 본인 레벨(본인 요약). 모르면 null. */
    private val level: suspend () -> Int?,
    /** 이번 주 횟수·거리와 연속 러닝 일수(대시보드). 못 읽으면 null. */
    private val stats: suspend () -> RunnerStats?
) : ViewModel() {
    @Inject
    constructor(
        avatars: AvatarRepository,
        seenStore: BackgroundUnlockSeenStore,
        clock: Clock,
        heads: SelfieHeads,
        accounts: AccountRepository,
        records: RecordRepository,
        myAvatar: MyAvatarStore
    ) : this(
        avatars,
        seenStore,
        clock,
        heads,
        myAvatar,
        level = { accounts.mySummary().getOrNull()?.level },
        stats = {
            records.dashboard(LocalDate.now(clock.withZone(RUNNER_ZONE))).getOrNull()?.let {
                RunnerStats(it.weekly?.actualRuns, it.weekly?.actualDistance, it.streakDays)
            }
        }
    )

    private val _state = MutableStateFlow(AvatarRunnerUiState())
    val state: StateFlow<AvatarRunnerUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
        // 러닝 저장 뒤 체형·러닝량이 바뀌었을 수 있으므로 들어올 때 홈·러닝 칸도 다시 준비한다(design-oo 05-30 [임시:D-성-104]).
        myAvatar.revalidate()
        viewModelScope.launch {
            var isFirst = true
            avatars.selfieProgress().collect { progress ->
                val before = _state.value.selfie
                _state.update { it.copy(selfie = progress) }
                // 처음 받은 값은 init의 읽기가 이미 반영한다.
                if (!isFirst && progress is SelfieProgress.Succeeded && before != progress) load()
                isFirst = false
            }
        }
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            val runnerCall = async { avatars.runner() }
            val shopCall = async { avatars.shop(null) }
            val levelCall = async { level() }
            val statsCall = async { stats() }
            val avatar = avatars.avatar()
            val runner = runnerCall.await()
            if (avatar !is Outcome.Success || runner !is Outcome.Success) {
                shopCall.cancel()
                levelCall.cancel()
                statsCall.cancel()
                val failure = (avatar as? Outcome.Failure) ?: (runner as Outcome.Failure)
                _state.update { it.copy(isLoading = false, error = AvatarErrorText.of(failure.error)) }
                return@launch
            }
            val items = shopCall.await().getOrNull()?.items.orEmpty()
            _state.update {
                it.copy(
                    isLoading = false,
                    avatar = avatar.value,
                    runner = runner.value,
                    look = it.look.copy(
                        outfit = outfitOf(avatar.value.equipped, items),
                        bodyShape = bodyShapeOf(runner.value.bodyVariant),
                        runAmount = runner.value.runAmount
                    )
                )
            }
            loadHead(avatar.value.faceKey)
            _state.update { it.copy(level = levelCall.await(), stats = statsCall.await()) }
            checkUnlocks()
        }
    }

    /** 얼굴은 [SelfieHeads]가 얼굴 키마다 한 번 만들어 둔다(같은 얼굴이면 같은 값이라 렌더러가 다시 그리지 않는다). */
    private suspend fun loadHead(faceKey: String?) {
        val face = heads.look(faceKey)
        _state.update { it.copy(faceLayers = face.faceLayers, look = it.look.copy(headLayers = face.headLayers)) }
    }

    private suspend fun checkUnlocks() {
        val backgrounds = avatars.backgrounds().getOrNull() ?: return
        _state.update {
            it.copy(
                unlockedBackgrounds = backgrounds.items.count { item -> item.isUnlocked },
                totalBackgrounds = backgrounds.items.size
            )
        }
        val (unlock, seen) = findNewUnlocks(backgrounds, seenStore.load(), clock.instant())
        seenStore.save(seen)
        _state.update { it.copy(unlock = unlock) }
    }

    /** 해금 팝업 닫기: 보인 배경을 기기에 남긴다. */
    fun onUnlockClose() {
        val unlock = _state.value.unlock ?: return
        markSeen(unlock)
    }

    /** 해금 팝업 "배경 바꾸기": 제목 배경을 고른다. 고르면 보인 배경을 기기에 남기고 닫고, 못 고르면 팝업을 둔다(다시 누르거나 닫는다). */
    fun onUnlockChange() {
        val unlock = _state.value.unlock ?: return
        viewModelScope.launch {
            if (avatars.chooseBackground(unlock.title.key) is Outcome.Success) {
                myAvatar.refresh()
                markSeen(unlock)
                _state.update { s -> s.copy(avatar = s.avatar?.copy(backgroundKey = unlock.title.key)) }
            }
        }
    }

    private fun markSeen(unlock: BackgroundUnlock) {
        val seen = seenStore.load() ?: BackgroundUnlockSeenStore.Seen(emptySet(), clock.instant())
        seenStore.save(seen.copy(keys = seen.keys + unlock.keys))
        _state.update { it.copy(unlock = null) }
    }

    private var resumedOnce = false

    /** 꾸미기·상점·얼굴 만들기에서 돌아오면 다시 읽는다(처음 표시는 init이 읽는다). */
    fun onResumed() {
        if (resumedOnce) load()
        resumedOnce = true
    }
}

/** 대시보드 기준 날짜의 시간대(홈과 같다). */
private val RUNNER_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
