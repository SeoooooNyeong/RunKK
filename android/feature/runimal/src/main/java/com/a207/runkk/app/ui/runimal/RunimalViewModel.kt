package com.a207.runkk.app.ui.runimal

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runimal.RunimalRepository
import com.a207.runkk.app.ui.share.CapturedCard
import com.a207.runkk.app.ui.share.CardSharer
import com.a207.runkk.app.ui.share.CardSide
import com.a207.runkk.app.ui.share.RunimalCardRenderer
import com.a207.runkk.app.ui.share.ShareImageFramer
import com.a207.runkk.app.ui.share.ShareOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MY 러니멀(FR-151~FR-154, FR-166, FR-168): RunimalRepository.status로 유형 카드·16유형·남은 횟수를 보이고,
 * 카드 공유는 MY Runimal 오른쪽 위 "공유"로 여는 공유 단계(K-3)에서 한다. 단계가 화면 카드를 찍어 넘기면 ShareImageFramer로
 * 카드 아래에 워드마크를 붙여 CardSharer로 보낸다(사용자 결정 2026-11-09). 찍은 이미지가 없으면 RunimalRepository.card를
 * ui.share의 RunimalCardRenderer로 그려 보낸다.
 */
@HiltViewModel
class RunimalViewModel internal constructor(
    private val runimal: RunimalRepository,
    private val renderer: RunimalCardRenderer,
    private val sharer: CardSharer,
    private val targets: RunimalShareTargets,
    private val framer: ShareImageFramer,
    private val renderDispatcher: CoroutineDispatcher
) : ViewModel() {
    @Inject
    constructor(
        runimal: RunimalRepository,
        renderer: RunimalCardRenderer,
        sharer: CardSharer,
        targets: RunimalShareTargets,
        framer: ShareImageFramer
    ) : this(runimal, renderer, sharer, targets, framer, Dispatchers.Default)

    private val _state = MutableStateFlow(RunimalUiState())
    val state: StateFlow<RunimalUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            when (val result = runimal.status()) {
                // 유형이 바뀌었을 수 있어 그려 둔 카드는 버린다.
                is Outcome.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        status = result.value,
                        cardImage = null,
                        isShareStep = it.isShareStep && result.value.type != null
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /** 공유 단계 열기·닫기. 유형이 없으면 열지 않는다(FR-168). */
    fun onShareStep(isOpen: Boolean) = _state.update { it.copy(isShareStep = isOpen && it.canShare) }

    /** [captured]는 공유 단계가 화면 카드를 찍은 이미지다. 있으면 워드마크를 붙여 보내고, 없으면 렌더러 이미지를 보낸다. */
    fun onShare(kind: RunimalShareKind, captured: CapturedCard? = null) {
        val s = _state.value
        if (s.isSharing || !s.canShare) return
        _state.update { it.copy(isSharing = true) }
        viewModelScope.launch {
            var message: UiText? = null
            var image: Bitmap? = s.cardImage
            try {
                if (captured != null) {
                    val framed = withContext(renderDispatcher) { framer.frame(captured) }
                    message = messageOf(sharer.share(framed, CardSide.FRONT, targets.of(kind)))
                    return@launch
                }
                if (image == null) {
                    when (val card = runimal.card()) {
                        is Outcome.Success -> image = withContext(renderDispatcher) {
                            renderer.render(card.value)
                        }

                        is Outcome.Failure -> message = shareError(card.error)
                    }
                }
                image?.let { message = messageOf(sharer.share(it, CardSide.FRONT, targets.of(kind))) }
            } finally {
                // 그리기·공유가 예외로 끝나도 공유 중 표시를 풀어 다시 누를 수 있게 한다.
                _state.update { it.copy(isSharing = false, cardImage = image ?: it.cardImage, message = message) }
            }
        }
    }

    private fun shareError(error: AppError): UiText = if ((error as? AppError.Rejected)?.code == NO_TYPE) {
        UiText.Res(R.string.runimal_share_no_type)
    } else {
        RunkkErrorText.of(error)
    }

    private fun messageOf(outcome: ShareOutcome): UiText? = when (outcome) {
        ShareOutcome.Shared -> null
        ShareOutcome.Saved -> UiText.Res(R.string.runimal_saved_to_gallery)
        is ShareOutcome.Fallback -> UiText.Res(outcome.messageRes)
        is ShareOutcome.Failed -> UiText.Res(outcome.messageRes)
    }

    private companion object {
        const val NO_TYPE = "NO_TYPE"
    }
}
