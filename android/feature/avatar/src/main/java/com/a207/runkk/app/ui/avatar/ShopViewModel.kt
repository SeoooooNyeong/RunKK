package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
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
 * 아바타 상점(피그마 제안 A안, FR-160): 부위별 목록·가격·보유·잠김·잔액, 칸을 고르면 무대에 입혀 보고, 확인 뒤 구매하고, 가진 것은 바로
 * 입는다. 잔액 판정은 서버가 한다. 상점 그림은 아이템 그림 키 규칙([shopArtRes])으로 찾는다.
 */
@HiltViewModel
class ShopViewModel @Inject constructor(
    private val avatars: AvatarRepository,
    private val heads: SelfieHeads,
    private val myAvatar: MyAvatarStore
) : ViewModel() {
    private val _state = MutableStateFlow(ShopUiState())
    val state: StateFlow<ShopUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
        loadAvatar()
    }

    /** 부위 칩. 입어 보던 칸은 그대로 둔다(다른 부위와 함께 입어 본다). */
    fun onSlot(slot: ItemSlot) = _state.update { it.copy(slot = slot) }

    fun load() {
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            when (val result = avatars.shop(null)) {
                is Outcome.Success -> _state.update { s ->
                    // 고른 칸은 새 목록의 값으로 바꾼다. 사라졌거나 잠겼으면 고르기를 푼다.
                    val fresh = s.selected?.let { old -> result.value.items.firstOrNull { it.itemId == old.itemId } }
                    s.copy(
                        isLoading = false,
                        shop = result.value,
                        selected = fresh?.takeIf {
                            !it.isLocked || it.isOwned
                        }
                    )
                        .withLook()
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = AvatarErrorText.of(result.error))
                }
            }
        }
    }

    /** 무대에 그릴 내 아바타(착용·체형·러닝량·얼굴). 못 읽으면 무대는 로딩 표시만 둔다. */
    private fun loadAvatar() {
        viewModelScope.launch {
            val runner = async { avatars.runner().getOrNull() }
            val avatar = avatars.avatar().getOrNull() ?: return@launch
            val runAmount = runner.await()?.runAmount ?: 0
            _state.update {
                it.copy(
                    avatar = avatar,
                    look = it.look.copy(bodyShape = bodyShapeOf(bodyVariantOf(avatar.bodyShape)), runAmount = runAmount)
                ).withLook()
            }
            val face = heads.look(avatar.faceKey)
            _state.update { it.copy(faceLayers = face.faceLayers, look = it.look.copy(headLayers = face.headLayers)) }
        }
    }

    /**
     * 칸을 고른다(입어 보기). 잠긴 아이템은 고르지 않고 해금 조건을 안내한다. 판매 종료이고 가지지 않은 것은 고르지 않는다. 같은 칸을 다시
     * 누르면 고르기를 푼다.
     */
    fun onPick(item: Shop.Item) {
        if (item.isLocked && !item.isOwned) {
            _state.update {
                it.copy(message = item.unlockLabel?.let(UiText::Plain) ?: UiText.Res(R.string.avatar_shop_locked))
            }
            return
        }
        if (!item.isOwned && !item.isOnSale) return
        _state.update { it.copy(selected = if (it.selected?.itemId == item.itemId) null else item).withLook() }
    }

    /** 무대의 벗기기: 입어 보던 칸을 푼다. */
    fun onClearTryOn() = _state.update { it.copy(selected = null).withLook() }

    /** 구매 단추: 잔액을 알면 모자란 아이템은 확인 창 없이 바로 안내하고(FR-160), 아니면 확인(H-5)을 연다. */
    fun onBuyRequest() {
        val item = _state.value.selected?.takeIf { !it.isOwned } ?: return
        val balance = _state.value.shop?.balance
        if (balance != null && item.price > balance) {
            _state.update { it.copy(message = UiText.Res(R.string.avatar_error_insufficient_reward)) }
            return
        }
        _state.update { it.copy(confirming = item) }
    }

    fun onDismiss() = _state.update { it.copy(confirming = null) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onBuy() {
        val item = _state.value.confirming ?: return
        if (_state.value.isBuying) return
        // 늦게 끝난 목록 읽기가 구매로 바뀐 보유 표시를 되돌리지 않게 진행 중인 읽기를 멈춘다.
        job?.cancel()
        _state.update { it.copy(confirming = null, isBuying = true, isLoading = false) }
        viewModelScope.launch {
            when (val result = avatars.purchase(item.itemId)) {
                is Outcome.Success -> _state.update { s ->
                    val owned = item.copy(isOwned = true)
                    s.copy(
                        isBuying = false,
                        // 산 칸은 그대로 입어 보는 채 두어 바로 「입기」를 누를 수 있게 한다.
                        selected = owned,
                        message = uiText(R.string.avatar_shop_bought, item.name),
                        shop = s.shop?.copy(
                            balance = result.value,
                            items = s.shop.items.map { if (it.itemId == item.itemId) owned else it }
                        )
                    )
                }

                is Outcome.Failure -> {
                    _state.update { it.copy(isBuying = false, message = AvatarErrorText.of(result.error)) }
                    // 그사이 잠겼거나(ITEM_LOCKED) 목록이 바뀌었으면 다시 읽는다.
                    if (result.error == AppError.Rejected(ITEM_LOCKED)) {
                        _state.update { it.copy(selected = null).withLook() }
                        load()
                    }
                }
            }
        }
    }

    /** 입기: 가진 칸을 바로 입는다(저장). 거부되면 사유를 안내한다. */
    fun onEquip() {
        val s = _state.value
        val item = s.selected?.takeIf { it.isOwned } ?: return
        if (s.isEquipping || s.isEquipped(item)) return
        _state.update { it.copy(isEquipping = true) }
        viewModelScope.launch {
            when (val result = avatars.equip(mapOf(item.slot to item.itemId))) {
                is Outcome.Success -> {
                    val reason = result.value.rejected[item.slot]
                    // 홈·러닝 칸도 새 착용으로 다시 준비한다. 거부되면 바뀐 것이 없다(리뷰 R2-6). update 람다 밖에서 부른다.
                    if (reason == null) myAvatar.refresh()
                    _state.update {
                        it.copy(
                            isEquipping = false,
                            avatar = result.value,
                            selected = null,
                            message = if (reason == null) {
                                uiText(R.string.avatar_shop_equipped, item.name)
                            } else {
                                uiText(
                                    R.string.avatar_equip_rejected,
                                    UiText.Res(item.slot.labelRes),
                                    AvatarErrorText.ofCode(reason)
                                )
                            }
                        ).withLook()
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isEquipping = false, message = AvatarErrorText.of(result.error))
                }
            }
        }
    }

    private fun ShopUiState.withLook(): ShopUiState =
        copy(look = look.copy(outfit = outfitOf(tryOn, shop?.items.orEmpty())))

    private companion object {
        const val ITEM_LOCKED = "ITEM_LOCKED"
    }
}
