package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.AvatarRunner
import com.a207.runkk.app.domain.avatar.BackgroundUnlockSeenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 내 러너(피그마 H-2, W8): 아바타(GET /avatar)·내 러너(GET /avatar/runner)·착용 이름용 상점 목록을 함께 읽고, 셀카 머리면 렌더러 머리
 * 레이어를 얼굴마다 한 번 만든다([SelfieHeads]). 들어올 때마다 새로 연 배경이 있으면 해금 팝업(H-6)을 띄운다(기기 기록 [BackgroundUnlockSeenStore]).
 */
@HiltViewModel
class AvatarRunnerViewModel @Inject constructor(
    private val avatars: AvatarRepository,
    private val seenStore: BackgroundUnlockSeenStore,
    private val clock: Clock,
    private val heads: SelfieHeads
) : ViewModel() {
    private val _state = MutableStateFlow(AvatarRunnerUiState())
    val state: StateFlow<AvatarRunnerUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            val runnerCall = async { avatars.runner() }
            val shopCall = async { avatars.shop(null) }
            val avatar = avatars.avatar()
            val runner = runnerCall.await()
            if (avatar !is Outcome.Success || runner !is Outcome.Success) {
                shopCall.cancel()
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

    /** 꾸미기·상점에서 돌아오면 다시 읽는다(처음 표시는 init이 읽는다). */
    fun onResumed() {
        if (resumedOnce) load()
        resumedOnce = true
    }
}
