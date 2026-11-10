package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 아바타 상점(FR-160): 부위별 목록·가격·보유 여부·잔액, 확인 뒤 구매. 잔액 판정은 서버가 한다. */
@HiltViewModel
class ShopViewModel @Inject constructor(private val avatars: AvatarRepository) : ViewModel() {
    private val _state = MutableStateFlow(ShopUiState())
    val state: StateFlow<ShopUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun onSlot(slot: ItemSlot?) {
        if (slot == _state.value.slot) return
        _state.update { it.copy(slot = slot, selected = null) }
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            when (val result = avatars.shop(_state.value.slot)) {
                is Outcome.Success -> _state.update { s ->
                    // 고른 칸은 새 목록의 값으로 바꾼다. 사라졌거나 보유·잠김·판매 종료가 됐으면 고르기를 푼다.
                    val fresh = s.selected?.let { old -> result.value.items.firstOrNull { it.itemId == old.itemId } }
                    s.copy(
                        isLoading = false,
                        shop = result.value,
                        selected = fresh?.takeIf { !it.isOwned && !it.isLocked && it.isOnSale }
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = AvatarErrorText.of(result.error))
                }
            }
        }
    }

    /**
     * 칸을 고른다(피그마 H-4). 잠긴 아이템은 고르지 않고 해금 조건을 안내한다. 보유·판매 종료 아이템은 고르지 않는다. 같은 칸을 다시 누르면
     * 고르기를 푼다.
     */
    fun onPick(item: Shop.Item) {
        if (item.isLocked) {
            _state.update {
                it.copy(
                    message =
                        item.unlockLabel?.let(UiText::Plain) ?: UiText.Res(R.string.avatar_shop_locked)
                )
            }
            return
        }
        if (item.isOwned || !item.isOnSale) return
        _state.update { it.copy(selected = if (it.selected?.itemId == item.itemId) null else item) }
    }

    /** 구매 바 "n에 사기": 잔액을 알면 모자란 아이템은 확인 창 없이 바로 안내하고(FR-160), 아니면 확인(H-5)을 연다. */
    fun onBuyRequest() {
        val item = _state.value.selected ?: return
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
                is Outcome.Success -> {
                    _state.update { s ->
                        s.copy(
                            isBuying = false,
                            selected = null,
                            message = uiText(R.string.avatar_shop_bought, item.name),
                            shop = s.shop?.copy(
                                balance = result.value,
                                items = s.shop.items.map {
                                    if (it.itemId ==
                                        item.itemId
                                    ) {
                                        it.copy(isOwned = true)
                                    } else {
                                        it
                                    }
                                }
                            )
                        )
                    }
                }

                is Outcome.Failure -> {
                    _state.update { it.copy(isBuying = false, message = AvatarErrorText.of(result.error)) }
                    // 그사이 잠겼거나(ITEM_LOCKED) 목록이 바뀌었으면 다시 읽는다.
                    if (result.error == AppError.Rejected(ITEM_LOCKED)) {
                        _state.update { it.copy(selected = null) }
                        load()
                    }
                }
            }
        }
    }

    private companion object {
        const val ITEM_LOCKED = "ITEM_LOCKED"
    }
}
