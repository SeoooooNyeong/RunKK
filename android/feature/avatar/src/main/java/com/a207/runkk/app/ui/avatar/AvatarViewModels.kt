package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- 아바타 만들기(가입 5단계·얼굴 바꾸기) ----------

data class AvatarSetupUiState(
    val isLoading: Boolean = true,
    /** 오늘 셀카 아바타를 만들 수 있는가(하루 1회, FR-157). 모르면 참으로 두고 서버가 거부한다. */
    val canCreateSelfie: Boolean = true,
    val selected: DefaultCharacter? = null,
    val isWorking: Boolean = false,
    val error: UiText? = null
)

sealed interface AvatarSetupEvent {
    data object Done : AvatarSetupEvent
}

/**
 * 아바타 만들기(FR-155~FR-158): 셀카(앱이 줄인 JPEG)를 createFromSelfie로 보내거나 기본 캐릭터 4종 중 하나를 chooseDefault로 고른다.
 * 셀카 실패(동의 없음·하루 1회 초과·변환 실패)는 사유와 함께 기본 캐릭터 선택을 안내한다.
 */
@HiltViewModel
class AvatarSetupViewModel internal constructor(
    private val avatars: AvatarRepository,
    /** 셀카 줄이기 디스패처(리뷰 N-10). */
    private val encodeDispatcher: CoroutineDispatcher
) : ViewModel() {
    @Inject
    constructor(avatars: AvatarRepository) : this(avatars, Dispatchers.Default)

    private val _state = MutableStateFlow(AvatarSetupUiState())
    val state: StateFlow<AvatarSetupUiState> = _state.asStateFlow()

    private val _events = Channel<AvatarSetupEvent>(Channel.BUFFERED)
    val events: Flow<AvatarSetupEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val avatar = avatars.avatar().getOrNull()
            _state.update {
                it.copy(
                    isLoading = false,
                    canCreateSelfie = avatar?.canCreateSelfieToday ?: true,
                    selected = it.selected ?: DefaultCharacter.of(avatar?.defaultCharacterId)
                )
            }
        }
    }

    fun onCharacterSelected(character: DefaultCharacter) = _state.update { it.copy(selected = character, error = null) }

    /** 셀카 촬영을 취소했거나 사진을 읽지 못했을 때. */
    fun onSelfieUnavailable() = _state.update { it.copy(error = UiText.Res(R.string.avatar_setup_selfie_unavailable)) }

    /** 찍은 셀카를 줄여(긴 변 1024px·JPEG·1MB) 보낸다. 줄이는 동안에도 다시 누르지 못하게 먼저 isWorking을 켠다. */
    fun onSelfieCaptured(bitmap: android.graphics.Bitmap) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            val jpeg = withContext(encodeDispatcher) { SelfieEncoder.encode(bitmap) }
            upload(jpeg)
        }
    }

    fun onSelfie(jpeg: ByteArray) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch { upload(jpeg) }
    }

    private suspend fun upload(jpeg: ByteArray) {
        when (val result = avatars.createFromSelfie(jpeg)) {
            is Outcome.Success -> {
                _state.update { it.copy(isWorking = false) }
                _events.trySend(AvatarSetupEvent.Done)
            }

            is Outcome.Failure -> {
                val code = (result.error as? AppError.Rejected)?.code
                _state.update {
                    it.copy(
                        isWorking = false,
                        canCreateSelfie = it.canCreateSelfie && code !in SELFIE_BLOCKED,
                        error = AvatarErrorText.of(result.error)
                    )
                }
            }
        }
    }

    fun onConfirmCharacter() {
        val character = _state.value.selected ?: return
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            when (val result = avatars.chooseDefault(character.name)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isWorking = false) }
                    _events.trySend(AvatarSetupEvent.Done)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isWorking = false, error = AvatarErrorText.of(result.error))
                }
            }
        }
    }
}

/** 오늘 다시 셀카를 시도해도 거부되는 사유(하루 1회 초과·운영 중지). */
private val SELFIE_BLOCKED = setOf("SELFIE_LIMIT", "SELFIE_RETRY_LIMIT", "SELFIE_DISABLED")

// ---------- 아바타 꾸미기 ----------

data class AvatarUiState(
    val isLoading: Boolean = true,
    val avatar: Avatar? = null,
    /** 셀카 얼굴 부위 그림. 셀카 얼굴이 아니거나 받지 못하면 비어 있고 자리 그림을 그린다. */
    val faceLayers: List<FaceLayer> = emptyList(),
    /** 보유 아이템 이름을 찾으려고 읽은 상점 목록(보유 여부 포함). */
    val items: List<Shop.Item> = emptyList(),
    val slot: ItemSlot = ItemSlot.TOP,
    val isSaving: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
) {
    val ownedInSlot: List<Shop.Item> get() = items.filter { it.isOwned && it.slot == slot }

    fun isEquipped(item: Shop.Item): Boolean = avatar?.equipped?.get(item.slot) == item.itemId

    fun itemName(itemId: Long?): String? = itemId?.let { id -> items.firstOrNull { it.itemId == id }?.name }
}

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
            when (val avatar = avatars.avatar()) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            avatar = avatar.value,
                            items = shop.await().getOrNull()?.items.orEmpty()
                        )
                    }
                    loadFace(avatar.value.faceKey)
                }

                is Outcome.Failure -> {
                    shop.cancel()
                    _state.update { it.copy(isLoading = false, error = AvatarErrorText.of(avatar.error)) }
                }
            }
        }
    }

    /** 마지막으로 그림을 받은 얼굴 키. 같은 얼굴이면 다시 받지 않는다(묶음 키가 바뀌면 새 얼굴이다). */
    private var faceKeyShown: String? = null

    /** 셀카 얼굴 부위 그림을 받는다. 받지 못하면 비워 두어 자리 그림을 그린다. */
    private suspend fun loadFace(faceKey: String?) {
        if (faceKey == faceKeyShown && (faceKey == null || _state.value.faceLayers.isNotEmpty())) return
        faceKeyShown = faceKey
        val layers = if (faceKey == null) emptyList() else avatars.faceLayers(faceKey).getOrNull().orEmpty()
        _state.update { it.copy(faceLayers = layers) }
    }

    private var resumedOnce = false

    /** 상점·얼굴 바꾸기에서 돌아오면 다시 읽는다(처음 표시는 init이 읽는다). */
    fun onResumed() {
        if (resumedOnce) load()
        resumedOnce = true
    }

    fun onSlot(slot: ItemSlot) = _state.update { it.copy(slot = slot) }

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

// ---------- 상점 ----------

data class ShopUiState(
    val isLoading: Boolean = true,
    /** null이면 전체. */
    val slot: ItemSlot? = null,
    val shop: Shop? = null,
    val confirming: Shop.Item? = null,
    val isBuying: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
)

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
        _state.update { it.copy(slot = slot) }
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            when (val result = avatars.shop(_state.value.slot)) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, shop = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = AvatarErrorText.of(result.error))
                }
            }
        }
    }

    fun onPick(item: Shop.Item) {
        if (item.isOwned || !item.isOnSale) return
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

                is Outcome.Failure -> _state.update {
                    it.copy(isBuying = false, message = AvatarErrorText.of(result.error))
                }
            }
        }
    }
}
