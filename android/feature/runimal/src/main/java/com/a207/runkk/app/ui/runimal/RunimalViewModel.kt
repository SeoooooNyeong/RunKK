package com.a207.runkk.app.ui.runimal

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runimal.RunimalRepository
import com.a207.runkk.app.ui.share.CardSharer
import com.a207.runkk.app.ui.share.CardSide
import com.a207.runkk.app.ui.share.RunimalCardRenderer
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
 * 카드 공유는 RunimalRepository.card를 유형 그림(RunimalArt)과 함께 ui.share의 RunimalCardRenderer로 그려 CardSharer로 보낸다.
 */
@HiltViewModel
class RunimalViewModel internal constructor(
    private val runimal: RunimalRepository,
    private val renderer: RunimalCardRenderer,
    private val sharer: CardSharer,
    private val targets: RunimalShareTargets,
    private val renderDispatcher: CoroutineDispatcher
) : ViewModel() {
    @Inject
    constructor(
        runimal: RunimalRepository,
        renderer: RunimalCardRenderer,
        sharer: CardSharer,
        targets: RunimalShareTargets
    ) : this(runimal, renderer, sharer, targets, Dispatchers.Default)

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
                    it.copy(isLoading = false, status = result.value, cardImage = null)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onShare(kind: RunimalShareKind) {
        val s = _state.value
        if (s.isSharing || !s.canShare) return
        _state.update { it.copy(isSharing = true) }
        viewModelScope.launch {
            var message: UiText? = null
            var image: Bitmap? = s.cardImage
            try {
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
