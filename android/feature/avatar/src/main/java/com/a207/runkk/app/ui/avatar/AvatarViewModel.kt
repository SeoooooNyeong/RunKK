package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.SelfieJob
import com.a207.runkk.app.domain.avatar.Shop
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 아바타 꾸미기(FR-161~FR-164): 얼굴·체형·피부톤·착용 아이템·잔액을 보이고 보유 아이템을 부위마다 입히거나 벗긴다.
 * 거부된 착용은 Avatar.rejected 사유로 보인다.
 */
@HiltViewModel
class AvatarViewModel @Inject constructor(private val avatars: AvatarRepository) : ViewModel() {
    private val _state = MutableStateFlow(AvatarUiState())
    val state: StateFlow<AvatarUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            val shop = async { avatars.shop(null) }
            val runner = async { avatars.runner() }
            finishPendingSelfie()
            when (val avatar = avatars.avatar()) {
                is Outcome.Success -> {
                    val items = shop.await().getOrNull()?.items.orEmpty()
                    val runAmount = runner.await().getOrNull()?.runAmount ?: 0
                    _state.update {
                        it.copy(
                            isLoading = false,
                            avatar = avatar.value,
                            items = items,
                            look = it.look.withAvatar(avatar.value, items, runAmount)
                        )
                    }
                    loadFace(avatar.value.faceKey)
                }

                is Outcome.Failure -> {
                    shop.cancel()
                    runner.cancel()
                    _state.update { it.copy(isLoading = false, error = AvatarErrorText.of(avatar.error)) }
                }
            }
        }
    }

    /**
     * 얼굴 만들기 화면을 떠난 뒤 서버가 셀카 얼굴을 다 만들었으면 결과를 받아 내 얼굴을 바꾼다(S15P31A207-248). 아직 만드는 중이면
     * 얼굴 만들기 화면에 다시 들어올 때 이어 조회한다.
     */
    private suspend fun finishPendingSelfie() {
        val jobId = avatars.pendingSelfieJobId() ?: return
        if (avatars.selfieJob(jobId).getOrNull()?.status == SelfieJob.Status.SUCCEEDED) avatars.finishSelfie(jobId)
    }

    /** 마지막으로 그림을 받은 얼굴 키. 같은 얼굴이면 다시 받지 않는다(묶음 키가 바뀌면 새 얼굴이다). */
    private var faceKeyShown: String? = null

    /** 셀카 얼굴 부위 그림을 받는다. 받지 못하면 비워 두어 자리 그림을 그린다. */
    private suspend fun loadFace(faceKey: String?) {
        val shown = _state.value
        val isHeadMissing = isSelfieHead(faceKey) && shown.look.headLayers == null
        if (faceKey == faceKeyShown && (faceKey == null || shown.faceLayers.isNotEmpty()) && !isHeadMissing) return
        faceKeyShown = faceKey
        val layers = if (faceKey == null) emptyList() else avatars.faceLayers(faceKey).getOrNull().orEmpty()
        val head = loadHeadLayers(avatars, faceKey)
        _state.update { it.copy(faceLayers = layers, look = it.look.copy(headLayers = head)) }
    }

    private var resumedOnce = false

    /** 상점·얼굴 바꾸기에서 돌아오면 다시 읽는다(처음 표시는 init이 읽는다). */
    fun onResumed() {
        if (resumedOnce) load()
        resumedOnce = true
    }

    fun onSlot(slot: ItemSlot) = _state.update { it.copy(slot = slot, isBackgroundTab = false) }

    /** 배경 탭으로(J3). 알림의 배경 해금(AVATAR_BACKGROUND)도 이 탭으로 연다. */
    fun onBackgroundTab() = _state.update { it.copy(isBackgroundTab = true) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /** 같은 부위는 하나만 입는다(FR-161): 입은 것을 누르면 벗기고, 아니면 바꿔 입힌다. */
    fun onToggle(item: Shop.Item) {
        val s = _state.value
        if (s.isSaving || s.avatar == null || !item.isOwned) return
        val next = if (s.isEquipped(item)) null else item.itemId
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = avatars.equip(mapOf(item.slot to next))) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isSaving = false,
                        avatar = result.value,
                        look = it.look.copy(outfit = outfitOf(result.value.equipped, it.items)),
                        message = result.value.rejected[item.slot]?.let { reason ->
                            uiText(
                                R.string.avatar_equip_rejected,
                                UiText.Res(item.slot.labelRes),
                                AvatarErrorText.ofCode(reason)
                            )
                        }
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isSaving = false, message = AvatarErrorText.of(result.error))
                }
            }
        }
    }
}
