package com.a207.runkk.app.ui.record

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.ui.share.CardSharer
import com.a207.runkk.app.ui.share.CardSide
import com.a207.runkk.app.ui.share.RunCardRenderer
import com.a207.runkk.app.ui.share.ShareOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 기록 카드 팝업(FR-068·FR-069): RecordRepository.card 값을 RunCardRenderer로 앞·뒷면 이미지로 만들고,
 * CardSharer로 기기 저장·다른 앱·스토리에 공유한다. 앞면 배경은 고른 랜드마크 배경이고 키는 AvatarRepository.backgroundChoice()로
 * 읽어 렌더러에 넘긴다(FR-202, D-140). 키를 읽지 못하면 기본 배경으로 그린다.
 */
@HiltViewModel
class RecordCardViewModel internal constructor(
    private val records: RecordRepository,
    private val avatars: AvatarRepository,
    private val renderer: RunCardRenderer,
    private val sharer: CardSharer,
    private val targets: CardShareTargets,
    /** 카드 그리기 디스패처(리뷰 N-10). */
    private val renderDispatcher: CoroutineDispatcher
) : ViewModel() {
    @Inject
    constructor(
        records: RecordRepository,
        avatars: AvatarRepository,
        renderer: RunCardRenderer,
        sharer: CardSharer,
        targets: CardShareTargets
    ) : this(records, avatars, renderer, sharer, targets, Dispatchers.Default)

    private val _state = MutableStateFlow(RecordCardUiState())
    val state: StateFlow<RecordCardUiState> = _state.asStateFlow()
    private var loadedId: String? = null

    fun load(recordId: String) {
        if (loadedId == recordId) return
        loadedId = recordId
        _state.value = RecordCardUiState()
        viewModelScope.launch {
            val background = async { avatars.backgroundChoice().getOrNull() }
            when (val result = records.card(recordId)) {
                is Outcome.Success -> {
                    val card = result.value
                    // ui.share는 domain.avatar를 모르므로 키·단계 문자열로 풀어 넘긴다(D-185).
                    val chosen = background.await()
                    val (key, stage) = chosen?.key to chosen?.stage
                    val (front, back) = withContext(renderDispatcher) {
                        renderer.render(card, CardSide.FRONT, key, stage) to
                            renderer.render(card, CardSide.BACK, key, stage)
                    }
                    _state.update { it.copy(isLoading = false, card = card, front = front, back = back) }
                }

                is Outcome.Failure -> {
                    background.cancel()
                    loadedId = null
                    _state.update { it.copy(isLoading = false, error = RecordErrorText.of(result.error)) }
                }
            }
        }
    }

    fun onSideSelected(side: CardSide) = _state.update { it.copy(side = side) }

    /**
     * 카드 팝업 ↔ 카드 공유 단계(G3). 공유 단계는 앞면(기록)으로 시작하고, 코스 면은 "코스 지도 표시"를 켤 때만 고른다
     * (보드 G3 스위치 기본 끔).
     */
    fun onShareStep(isOpen: Boolean) = _state.update {
        it.copy(isShareStep = isOpen, side = if (isOpen) CardSide.FRONT else it.side)
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /** [captured]는 공유 단계가 화면 카드 앞면을 그대로 찍은 이미지다. 있으면 앞면 공유에 그것을 쓴다(기록 카드와 같은 모양). */
    fun onShare(kind: CardShareKind, captured: Bitmap? = null) {
        val s = _state.value
        val bitmap = (if (s.side == CardSide.FRONT) captured ?: s.front else s.back) ?: return
        if (s.isSharing) return
        _state.update { it.copy(isSharing = true) }
        viewModelScope.launch {
            val outcome = sharer.share(bitmap, s.side, targets.of(kind))
            _state.update { it.copy(isSharing = false, message = messageOf(outcome)) }
        }
    }

    private fun messageOf(outcome: ShareOutcome): UiText? = when (outcome) {
        ShareOutcome.Shared -> null
        ShareOutcome.Saved -> UiText.Res(R.string.record_card_saved)
        is ShareOutcome.Fallback -> UiText.Res(outcome.messageRes)
        is ShareOutcome.Failed -> UiText.Res(outcome.messageRes)
    }
}
