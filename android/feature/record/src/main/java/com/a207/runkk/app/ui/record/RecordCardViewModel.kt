package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.ui.share.CapturedCard
import com.a207.runkk.app.ui.share.CardSharer
import com.a207.runkk.app.ui.share.CardSide
import com.a207.runkk.app.ui.share.RunCardRenderer
import com.a207.runkk.app.ui.share.ShareImageFramer
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
import com.a207.runkk.app.ui.share.R as ShareR

/**
 * 기록 카드 팝업(FR-068·FR-069): RecordRepository.card 값을 RunCardRenderer로 앞·뒷면 이미지로 만들고,
 * CardSharer로 기기 저장·다른 앱·스토리에 공유한다. 공유 단계는 화면에 그린 면(앞면 기록, 뒷면 코스)을 찍어 넘기고,
 * 이 ViewModel이 ShareImageFramer로 카드 아래에 워드마크를 붙여 보낸다(K-2). 찍은 이미지가 없을 때만 렌더러 이미지를 쓴다.
 * 앞면 배경은 고른 랜드마크 배경이고 키는 AvatarRepository.backgroundChoice()로 읽어 렌더러에 넘긴다(FR-202, D-140).
 * 키를 읽지 못하면 기본 배경으로 그린다.
 */
@HiltViewModel
class RecordCardViewModel internal constructor(
    private val records: RecordRepository,
    private val avatars: AvatarRepository,
    private val renderer: RunCardRenderer,
    private val sharer: CardSharer,
    private val targets: CardShareTargets,
    private val framer: ShareImageFramer,
    /** 카드 그리기 디스패처(리뷰 N-10). */
    private val renderDispatcher: CoroutineDispatcher
) : ViewModel() {
    @Inject
    constructor(
        records: RecordRepository,
        avatars: AvatarRepository,
        renderer: RunCardRenderer,
        sharer: CardSharer,
        targets: CardShareTargets,
        framer: ShareImageFramer
    ) : this(records, avatars, renderer, sharer, targets, framer, Dispatchers.Default)

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

    /** 코스 지도 공유가 꺼진 기록에서 잠긴 "코스 지도 표시"를 누르면 공유가 거부될 때와 같은 안내를 보인다(FR-069). */
    fun onCourseLocked() = _state.update { it.copy(message = UiText.Res(ShareR.string.share_course_map_off)) }

    /**
     * [captured]는 공유 단계가 지금 보이는 면(앞면 또는 코스 면)을 그대로 찍은 이미지다. 있으면 그 아래에 워드마크를 붙여 공유하고
     * (사용자 결정 2026-11-09: 워드마크는 공유 이미지에만), 없으면 렌더러가 그린 면을 보낸다.
     */
    fun onShare(kind: CardShareKind, captured: CapturedCard? = null) {
        val s = _state.value
        if (s.isSharing) return
        val rendered = if (s.side == CardSide.FRONT) s.front else s.back
        if (captured == null && rendered == null) return
        _state.update { it.copy(isSharing = true) }
        viewModelScope.launch {
            var message: UiText? = null
            try {
                val bitmap = captured?.let { withContext(renderDispatcher) { framer.frame(it) } } ?: rendered
                if (bitmap != null) message = messageOf(sharer.share(bitmap, s.side, targets.of(kind)))
            } finally {
                _state.update { it.copy(isSharing = false, message = message) }
            }
        }
    }

    private fun messageOf(outcome: ShareOutcome): UiText? = when (outcome) {
        ShareOutcome.Shared -> null
        ShareOutcome.Saved -> UiText.Res(R.string.record_card_saved)
        is ShareOutcome.Fallback -> UiText.Res(outcome.messageRes)
        is ShareOutcome.Failed -> UiText.Res(outcome.messageRes)
    }
}
