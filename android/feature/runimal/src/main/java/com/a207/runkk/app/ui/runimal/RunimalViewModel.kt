package com.a207.runkk.app.ui.runimal

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runimal.RunimalRepository
import com.a207.runkk.app.domain.runimal.RunimalStatus
import com.a207.runkk.app.ui.share.CardSharer
import com.a207.runkk.app.ui.share.CardSide
import com.a207.runkk.app.ui.share.DeviceSaveTarget
import com.a207.runkk.app.ui.share.RunimalCardRenderer
import com.a207.runkk.app.ui.share.ShareOutcome
import com.a207.runkk.app.ui.share.ShareTarget
import com.a207.runkk.app.ui.share.StoryShareTarget
import com.a207.runkk.app.ui.share.SystemShareTarget
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

/** 공유 대상 3종(C-012): 기기 저장, 다른 앱, 스토리. */
enum class RunimalShareKind(val label: String) {
    SAVE("기기 저장"),
    SYSTEM("다른 앱"),
    STORY("스토리")
}

/** ui.share의 공유 대상 묶음. 시험에서는 가짜 대상을 넣는다. */
class RunimalShareTargets(val save: ShareTarget, val system: ShareTarget, val story: ShareTarget) {
    @Inject
    constructor(save: DeviceSaveTarget, system: SystemShareTarget, story: StoryShareTarget) :
        this(save as ShareTarget, system as ShareTarget, story as ShareTarget)

    fun of(kind: RunimalShareKind): ShareTarget = when (kind) {
        RunimalShareKind.SAVE -> save
        RunimalShareKind.SYSTEM -> system
        RunimalShareKind.STORY -> story
    }
}

data class RunimalUiState(
    val loading: Boolean = true,
    val status: RunimalStatus? = null,
    /** 공유할 카드 이미지(공유를 처음 누를 때 만든다). */
    val cardImage: Bitmap? = null,
    val sharing: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    /** 다음 판정까지 남은 횟수 문구(FR-154): 유형 전이면 "임시 유형까지 n회", 뒤면 "다음 확정까지 n회". */
    val remainingLabel: String?
        get() {
            val s = status ?: return null
            val n = s.remainingRuns ?: return null
            return if (s.type == null) "임시 유형까지 ${n}회" else "다음 확정까지 ${n}회"
        }

    /** 임시·확정 유형이 있을 때만 공유한다(FR-168). */
    val canShare: Boolean get() = status?.type != null
}

/**
 * MY Runimal(FR-151~FR-154, FR-166, FR-168): RunimalRepository.status로 유형 카드·16유형·남은 횟수를 보이고,
 * 카드 공유는 RunimalRepository.card를 ui.share의 RunimalCardRenderer로 그려 CardSharer로 보낸다.
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
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            when (val result = runimal.status()) {
                // 유형이 바뀌었을 수 있어 그려 둔 카드는 버린다.
                is Outcome.Success -> _state.update {
                    it.copy(loading = false, status = result.value, cardImage = null)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onShare(kind: RunimalShareKind) {
        val s = _state.value
        if (s.sharing || !s.canShare) return
        _state.update { it.copy(sharing = true) }
        viewModelScope.launch {
            var message: String? = null
            var image: Bitmap? = s.cardImage
            try {
                if (image == null) {
                    when (val card = runimal.card()) {
                        is Outcome.Success -> image = withContext(renderDispatcher) { renderer.render(card.value) }
                        is Outcome.Failure -> message = shareError(card.error)
                    }
                }
                image?.let { message = messageOf(sharer.share(it, CardSide.FRONT, targets.of(kind))) }
            } finally {
                // 그리기·공유가 예외로 끝나도 공유 중 표시를 풀어 다시 누를 수 있게 한다.
                _state.update { it.copy(sharing = false, cardImage = image ?: it.cardImage, message = message) }
            }
        }
    }

    private fun shareError(error: AppError): String =
        if ((error as? AppError.Rejected)?.code == NO_TYPE) "유형이 생기면 카드를 공유할 수 있어요" else RunkkErrorText.of(error)

    private fun messageOf(outcome: ShareOutcome): String? = when (outcome) {
        ShareOutcome.Shared -> null
        ShareOutcome.Saved -> "사진첩에 저장했어요"
        is ShareOutcome.Fallback -> outcome.message
        is ShareOutcome.Failed -> outcome.message
    }

    private companion object {
        const val NO_TYPE = "NO_TYPE"
    }
}
