package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.ItemSlot
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
 * 아바타 꾸미기(피그마 제안 A안, FR-161): 착용 아이템·잔액을 보이고 가진 아이템을 부위마다 입혀 본다. 칸을 누르면 무대에 바로 입혀 보이고
 * (저장 전), 「저장」이 바뀐 부위만 한 번에 보낸다. 거부된 착용은 Avatar.rejected 사유로 보인다. 셀카 결과 적용은 워커가 한다
 * (design-oo 05-30 [임시:D-성-118]).
 */
@HiltViewModel
class AvatarViewModel @Inject constructor(
    private val avatars: AvatarRepository,
    private val heads: SelfieHeads,
    private val myAvatar: MyAvatarStore
) : ViewModel() {
    private val _state = MutableStateFlow(AvatarUiState())
    val state: StateFlow<AvatarUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        // 이미 그린 화면을 다시 읽을 때(상점에서 돌아옴)는 로딩 표시로 바꾸지 않는다(리뷰 R2-15).
        _state.update { it.copy(isLoading = it.avatar == null, error = null) }
        job = viewModelScope.launch {
            val shop = async { avatars.shop(null) }
            val runner = async { avatars.runner() }
            when (val avatar = avatars.avatar()) {
                is Outcome.Success -> {
                    val items = shop.await().getOrNull()?.items.orEmpty()
                    val runAmount = runner.await().getOrNull()?.runAmount ?: 0
                    _state.update {
                        // 저장하지 않은 입어 보기는 다시 읽어도 둔다(상점에서 돌아옴).
                        val draft = if (it.isDirty) it.draft else avatar.value.equipped
                        it.copy(
                            isLoading = false,
                            avatar = avatar.value,
                            items = items,
                            draft = draft,
                            look = it.look.withAvatar(avatar.value.copy(equipped = draft), items, runAmount)
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
     * 셀카 얼굴 그림과 렌더러 머리 레이어. [SelfieHeads]가 얼굴 키마다 한 번 받아 두므로 같은 얼굴이면 다시 받지 않는다. 받지 못하면
     * 비워 두어 자리 그림을 그린다.
     */
    private suspend fun loadFace(faceKey: String?) {
        val face = heads.look(faceKey)
        _state.update { it.copy(faceLayers = face.faceLayers, look = it.look.copy(headLayers = face.headLayers)) }
    }

    private var resumedOnce = false

    /** 상점에서 돌아오면 다시 읽는다(처음 표시는 init이 읽는다). */
    fun onResumed() {
        if (resumedOnce) load()
        resumedOnce = true
    }

    fun onSlot(slot: ItemSlot) = _state.update { it.copy(slot = slot, isBackgroundTab = false) }

    /** 배경 탭으로(J3). 알림의 배경 해금(AVATAR_BACKGROUND)도 이 탭으로 연다. */
    fun onBackgroundTab() = _state.update { it.copy(isBackgroundTab = true) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /**
     * 칸 누름. 같은 부위는 하나만 입는다(FR-161): 입어 보는 것을 누르면 벗기고, 아니면 바꿔 입힌다. 무대에 바로 입혀 보이고 저장은 하지
     * 않는다. 잠긴 칸은 해금 조건을 안내한다.
     */
    fun onToggle(item: Shop.Item) {
        val s = _state.value
        if (s.isSaving || s.avatar == null) return
        if (item.isLocked && !item.isOwned) {
            _state.update {
                it.copy(
                    message =
                        item.unlockLabel?.let(UiText::Plain) ?: UiText.Res(R.string.avatar_shop_locked)
                )
            }
            return
        }
        if (!item.isOwned) return
        val draft = if (s.isEquipped(item)) s.draft - item.slot else s.draft + (item.slot to item.itemId)
        setDraft(draft)
    }

    /** 되돌리기: 들어올 때(마지막 저장)의 착용으로 돌린다. */
    fun onUndo() {
        val avatar = _state.value.avatar ?: return
        setDraft(avatar.equipped)
    }

    private fun setDraft(draft: Map<ItemSlot, Long>) = _state.update {
        it.copy(draft = draft, look = it.look.copy(outfit = outfitOf(draft, it.items)))
    }

    /** 저장: 바뀐 부위만 보낸다(벗긴 부위는 null). 바뀐 것이 없으면 바로 닫는다. 거부된 부위는 사유를 안내하고 화면을 둔다. */
    fun onSave() {
        val s = _state.value
        val avatar = s.avatar ?: return
        if (s.isSaving) return
        val changes = ItemSlot.entries
            .filter { s.draft[it] != avatar.equipped[it] }
            .associateWith { s.draft[it] }
        if (changes.isEmpty()) {
            _state.update { it.copy(isDone = true) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = avatars.equip(changes)) {
                is Outcome.Success -> {
                    val rejected = result.value.rejected.entries.firstOrNull()
                    // 홈·러닝 칸도 새 착용으로 다시 준비한다(design-oo 05-30 3절 6단계). 거부되면 바뀐 것이 없다(리뷰 R2-6).
                    if (rejected == null) myAvatar.refresh()
                    _state.update {
                        it.copy(
                            isSaving = false,
                            avatar = result.value,
                            draft = result.value.equipped,
                            look = it.look.copy(outfit = outfitOf(result.value.equipped, it.items)),
                            isDone = rejected == null,
                            message = rejected?.let { (slot, reason) ->
                                uiText(
                                    R.string.avatar_equip_rejected,
                                    UiText.Res(slot.labelRes),
                                    AvatarErrorText.ofCode(reason)
                                )
                            }
                        )
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isSaving = false, message = AvatarErrorText.of(result.error))
                }
            }
        }
    }
}
