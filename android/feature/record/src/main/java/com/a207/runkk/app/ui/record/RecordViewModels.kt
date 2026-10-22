package com.a207.runkk.app.ui.record

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.record.RunCard
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.ui.share.CardSharer
import com.a207.runkk.app.ui.share.CardSide
import com.a207.runkk.app.ui.share.DeviceSaveTarget
import com.a207.runkk.app.ui.share.RunCardRenderer
import com.a207.runkk.app.ui.share.ShareOutcome
import com.a207.runkk.app.ui.share.ShareTarget
import com.a207.runkk.app.ui.share.StoryShareTarget
import com.a207.runkk.app.ui.share.SystemShareTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- 기록 목록·달력 ----------

enum class RecordView { CALENDAR, LIST }

data class RecordListUiState(
    val month: YearMonth,
    val view: RecordView = RecordView.CALENDAR,
    val isLoading: Boolean = true,
    val runs: List<RunSummary> = emptyList(),
    val dailyDistance: Map<LocalDate, Distance> = emptyMap(),
    val pendingCount: Int = 0,
    val error: UiText? = null,
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
) {
    /** 이번 달 집계에 들어가는 러닝 수(집계 제외 빼고). */
    val validCount: Int get() = runs.count { it.isValid }

    val validDistance: Distance get() = runs.filter {
        it.isValid
    }.fold(Distance.ZERO) { acc, run -> acc.plus(run.distance) }
}

/**
 * 기록 목록·달력(C1, FR-057·FR-058). 본인 기록(RecordRepository.runs(SELF, 달의 첫날, 끝날))을 최신순으로 보이고
 * 날짜별 거리를 달력에 칠한다. 기기의 미전송 기록 수(PendingRunRepository.observePending)를 함께 보인다(13.2).
 */
@HiltViewModel
class RecordListViewModel @Inject constructor(
    private val records: RecordRepository,
    private val pendingRuns: PendingRunRepository,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(RecordListUiState(month = YearMonth.now(clock), zone = clock.zone))
    val state: StateFlow<RecordListUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            pendingRuns.observePending().collect { runs -> _state.update { it.copy(pendingCount = runs.size) } }
        }
    }

    fun refresh() {
        val month = _state.value.month
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = records.runs(RecordRepository.SELF, month.atDay(1), month.atEndOfMonth())) {
                is Outcome.Success -> {
                    val runs = result.value.runs.sortedByDescending { it.startedAt }
                    val zone = clock.zone
                    val daily = runs.groupBy { it.startedAt.atZone(zone).toLocalDate() }
                        .mapValues { (_, list) -> list.fold(Distance.ZERO) { acc, run -> acc.plus(run.distance) } }
                    _state.update {
                        if (it.month !=
                            month
                        ) {
                            it
                        } else {
                            it.copy(isLoading = false, runs = runs, dailyDistance = daily)
                        }
                    }
                }

                is Outcome.Failure -> _state.update {
                    if (it.month !=
                        month
                    ) {
                        it
                    } else {
                        it.copy(
                            isLoading = false,
                            runs = emptyList(),
                            dailyDistance = emptyMap(),
                            error = RunkkErrorText.of(result.error)
                        )
                    }
                }
            }
        }
    }

    fun onPreviousMonth() = moveMonth(-1)

    fun onNextMonth() = moveMonth(1)

    fun onViewSelected(view: RecordView) = _state.update { it.copy(view = view) }

    private fun moveMonth(delta: Long) {
        _state.update { it.copy(month = it.month.plusMonths(delta)) }
        refresh()
    }
}

// ---------- 기록 상세 ----------

data class RecordDetailUiState(
    val isLoading: Boolean = true,
    val recordId: String? = null,
    val detail: RecordDetail? = null,
    val error: UiText? = null
)

/** 기록 상세(C2, FR-059·FR-060·FR-078·FR-079·FR-185·FR-186). */
@HiltViewModel
class RecordDetailViewModel @Inject constructor(private val records: RecordRepository) : ViewModel() {
    private val _state = MutableStateFlow(RecordDetailUiState())
    val state: StateFlow<RecordDetailUiState> = _state.asStateFlow()

    fun load(recordId: String, isForced: Boolean = false) {
        if (!isForced && _state.value.recordId == recordId && _state.value.detail != null) return
        _state.value = RecordDetailUiState(recordId = recordId)
        viewModelScope.launch {
            when (val result = records.run(recordId)) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, detail = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RecordErrorText.of(result.error))
                }
            }
        }
    }
}

// ---------- 기록 카드 팝업·공유 ----------

/** 공유 대상 3종(C-012): 기기 저장, 다른 앱, 스토리. */
enum class CardShareKind(@StringRes val labelRes: Int) {
    SAVE(R.string.record_card_share_save),
    SYSTEM(R.string.record_card_share_system),
    STORY(R.string.record_card_share_story)
}

/** ui.share의 공유 대상 묶음. 시험에서는 가짜 대상을 넣는다. */
class CardShareTargets(val save: ShareTarget, val system: ShareTarget, val story: ShareTarget) {
    @Inject
    constructor(save: DeviceSaveTarget, system: SystemShareTarget, story: StoryShareTarget) :
        this(save as ShareTarget, system as ShareTarget, story as ShareTarget)

    fun of(kind: CardShareKind): ShareTarget = when (kind) {
        CardShareKind.SAVE -> save
        CardShareKind.SYSTEM -> system
        CardShareKind.STORY -> story
    }
}

data class RecordCardUiState(
    val isLoading: Boolean = true,
    val card: RunCard? = null,
    val front: Bitmap? = null,
    val back: Bitmap? = null,
    val side: CardSide = CardSide.FRONT,
    val isSharing: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
) {
    /** 뒷면(코스 지도)을 공유할 수 있는가(FR-011·FR-069). 최종 판단은 CardSharer가 설정 사본으로 한다. */
    val canShareBack: Boolean get() = card?.isCourseMapShared == true
}

/**
 * 기록 카드 팝업(FR-068·FR-069): RecordRepository.card 값을 RunCardRenderer로 앞·뒷면 이미지로 만들고,
 * CardSharer로 기기 저장·다른 앱·스토리에 공유한다.
 */
@HiltViewModel
class RecordCardViewModel internal constructor(
    private val records: RecordRepository,
    private val renderer: RunCardRenderer,
    private val sharer: CardSharer,
    private val targets: CardShareTargets,
    /** 카드 그리기 디스패처(리뷰 N-10). */
    private val renderDispatcher: CoroutineDispatcher
) : ViewModel() {
    @Inject
    constructor(
        records: RecordRepository,
        renderer: RunCardRenderer,
        sharer: CardSharer,
        targets: CardShareTargets
    ) : this(records, renderer, sharer, targets, Dispatchers.Default)

    private val _state = MutableStateFlow(RecordCardUiState())
    val state: StateFlow<RecordCardUiState> = _state.asStateFlow()
    private var loadedId: String? = null

    fun load(recordId: String) {
        if (loadedId == recordId) return
        loadedId = recordId
        _state.value = RecordCardUiState()
        viewModelScope.launch {
            when (val result = records.card(recordId)) {
                is Outcome.Success -> {
                    val card = result.value
                    val (front, back) = withContext(renderDispatcher) {
                        renderer.render(card, CardSide.FRONT) to renderer.render(card, CardSide.BACK)
                    }
                    _state.update { it.copy(isLoading = false, card = card, front = front, back = back) }
                }

                is Outcome.Failure -> {
                    loadedId = null
                    _state.update { it.copy(isLoading = false, error = RecordErrorText.of(result.error)) }
                }
            }
        }
    }

    fun onSideSelected(side: CardSide) = _state.update { it.copy(side = side) }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onShare(kind: CardShareKind) {
        val s = _state.value
        val bitmap = (if (s.side == CardSide.FRONT) s.front else s.back) ?: return
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
