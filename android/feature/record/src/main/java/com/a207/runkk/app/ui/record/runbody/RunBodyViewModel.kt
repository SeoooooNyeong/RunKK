package com.a207.runkk.app.ui.record.runbody

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.runbody.RunBodyRepository
import com.a207.runkk.app.domain.runbody.RunBodySession
import com.a207.runkk.app.domain.runbody.RunBodyStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 런바디 화면(D-453 RunBodySessionCard·RunBodyMonthSection, FR-066·FR-266·FR-267). 요약 탭은 리포트(RecordRepository.report, 코멘트),
 * 최근 10회 변화(RunBodyRepository.trend, G-14), 주간 러닝 기록(고른 주의 RecordRepository.runs)이고, 러닝별 탭은
 * 최근 [RECENT_DAYS]일 기록(RecordRepository.runs)과 고른 기록의 분석(RunBodyRepository.session), 월간 탭은 월간 집계(RunBodyRepository.month)다.
 * 분석은 러닝 저장 때 서버가 자동 제출한다(S-31). 대기·분석 중이면 처음 [FAST_POLLS]번은 [POLL_MILLIS]마다(NFR-053 60초), 그 뒤로는 간격을
 * 두 배씩 늘려(상한 [MAX_POLL_MILLIS]) 모두 [MAX_POLL_WAIT_MILLIS]까지 다시 읽어 완료되면 같은 칸에 결과를 보인다(MR !91 e2e 4). 그 뒤에도
 * 대기·분석 중이면 "다시 확인"으로, 실패면 "상태 다시 확인"([onRetrySession])으로 다시 읽는다. 러닝 선택(G-30)의 러닝 점수는 기록 목록과 함께 읽고
 * (RunBodyRepository.scores), 못 읽으면 점수 없이 목록만 보인다.
 *
 * 날짜·시각은 기기 시간대([zone], 러닝 결과 화면과 같은 ZoneId.systemDefault())로 셈한다. 앱 Clock은 UTC(DeviceBindings)라
 * clock.zone을 쓰면 오늘·"낮/밤 러닝"이 9시간 어긋난다(MR !91 e2e 2).
 */
@HiltViewModel
class RunBodyViewModel internal constructor(
    private val records: RecordRepository,
    private val runBody: RunBodyRepository,
    private val clock: Clock,
    private val zone: ZoneId
) : ViewModel() {
    @Inject
    constructor(records: RecordRepository, runBody: RunBodyRepository, clock: Clock) :
        this(records, runBody, clock, ZoneId.systemDefault())

    private val _state = MutableStateFlow(RunBodyUiState(today = today(), zone = zone))
    val state: StateFlow<RunBodyUiState> = _state.asStateFlow()
    private var hasRuns = false
    private var hasMonth = false
    private var runsJob: Job? = null
    private var sessionJob: Job? = null
    private var monthJob: Job? = null
    private var scoresJob: Job? = null
    private var trendJob: Job? = null
    private var weekJob: Job? = null

    init {
        refreshReport()
        refreshTrend()
        refreshWeek()
    }

    fun onTab(tab: RunBodyTab) {
        _state.update { it.copy(tab = tab, page = RunBodyPage.MAIN) }
        when (tab) {
            RunBodyTab.SUMMARY -> Unit
            RunBodyTab.RUN -> if (!hasRuns) refreshRuns()
            RunBodyTab.MONTH -> if (!hasMonth) refreshMonth()
        }
    }

    fun refreshReport() {
        _state.update { it.copy(isReportLoading = true, reportError = null) }
        viewModelScope.launch {
            when (val result = records.report()) {
                is Outcome.Success -> _state.update { it.copy(isReportLoading = false, report = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isReportLoading = false, reportError = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    /** 요약 최근 10회 변화(G-14). */
    fun refreshTrend() {
        _state.update { it.copy(isTrendLoading = true, trendError = null) }
        trendJob?.cancel()
        trendJob = viewModelScope.launch {
            when (val result = runBody.trend()) {
                is Outcome.Success -> _state.update { it.copy(isTrendLoading = false, trend = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isTrendLoading = false, trendError = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    /** 요약 주간 러닝 기록(G-14): 고른 주의 본인 기록(앞뒤 하루를 더 읽어 기기 시간대로 거른다). */
    fun refreshWeek() {
        val week = _state.value.week
        _state.update { it.copy(isWeekLoading = true, weekError = null) }
        weekJob?.cancel()
        weekJob = viewModelScope.launch {
            val (from, to) = weekQueryRange(week)
            val result = records.runs(RecordRepository.SELF, from, to)
            _state.update { s ->
                when {
                    s.week != week -> s

                    result is Outcome.Success -> s.copy(isWeekLoading = false, weekRuns = result.value.runs)

                    result is Outcome.Failure -> s.copy(
                        isWeekLoading = false,
                        weekRuns = null,
                        weekError = RunkkErrorText.of(result.error)
                    )

                    else -> s
                }
            }
        }
    }

    fun onPreviousWeek() {
        if (_state.value.canGoPreviousWeek) moveWeek(-1)
    }

    fun onNextWeek() {
        if (_state.value.canGoNextWeek) moveWeek(1)
    }

    private fun moveWeek(delta: Long) {
        _state.update { it.copy(week = it.week.plusWeeks(delta), weekRuns = null) }
        refreshWeek()
    }

    /** 주간/월간 토글(G-14). 다시 읽지 않고 빈도 줄만 바꾼다. */
    fun onPeriod(period: RunBodyPeriod) = _state.update { it.copy(period = period) }

    /** 최근 기록을 읽고, 고른 기록이 없거나 목록에 없으면 가장 최근 기록을 골라 분석을 읽는다. */
    fun refreshRuns() {
        hasRuns = true
        val today = today()
        _state.update { it.copy(isRunsLoading = true, runsError = null, today = today) }
        loadScores()
        runsJob?.cancel()
        runsJob = viewModelScope.launch {
            when (val result = records.runs(RecordRepository.SELF, today.minusDays(RECENT_DAYS - 1), today)) {
                is Outcome.Success -> {
                    val runs = result.value.runs.sortedByDescending { it.startedAt }
                    val before = _state.value.selectedRecordId
                    val selected =
                        before?.takeIf { id -> runs.any { it.recordId == id } } ?: runs.firstOrNull()?.recordId
                    _state.update { it.copy(isRunsLoading = false, runs = runs, selectedRecordId = selected) }
                    if (selected != null && (selected != before || _state.value.session !is RunBodySessionState.Done)) {
                        loadSession(selected)
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isRunsLoading = false, runsError = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onOpenPick() = _state.update { it.copy(page = RunBodyPage.PICK) }

    fun onPick(run: RunSummary) {
        val isChanged = run.recordId != _state.value.selectedRecordId
        _state.update { it.copy(page = RunBodyPage.MAIN, selectedRecordId = run.recordId) }
        if (isChanged) loadSession(run.recordId)
    }

    /** "상태 다시 확인"(G-19). */
    fun onRetrySession() {
        _state.value.selectedRecordId?.let(::loadSession)
    }

    /** 화면에 돌아왔다. 출생연도 안내 중이었으면(프로필 편집에서 돌아옴) 다시 읽는다(MR !91 e2e 1). */
    fun onResume() {
        if (_state.value.session == RunBodySessionState.NeedsBirthYear) onRetrySession()
    }

    /** "구간과 피드백 자세히 보기"(G-20). 결과가 있을 때만 연다. */
    fun onOpenDetail() {
        if (_state.value.session is RunBodySessionState.Done) _state.update { it.copy(page = RunBodyPage.DETAIL) }
    }

    /** 러닝 선택·상세에서 뒤로면 탭 화면으로 돌아오고 참을 돌려준다. 탭 화면이면 거짓(화면을 닫는다). */
    fun onBack(): Boolean {
        if (_state.value.page == RunBodyPage.MAIN) return false
        _state.update { it.copy(page = RunBodyPage.MAIN) }
        return true
    }

    fun onPreviousMonth() {
        if (_state.value.canGoPreviousMonth) moveMonth(-1)
    }

    fun onNextMonth() {
        if (_state.value.canGoNextMonth) moveMonth(1)
    }

    fun refreshMonth() {
        hasMonth = true
        val month = _state.value.month
        _state.update { it.copy(isMonthLoading = true, monthError = null) }
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            val result = runBody.month(month)
            _state.update { s ->
                when {
                    s.month != month -> s

                    result is Outcome.Success -> s.copy(isMonthLoading = false, monthData = result.value)

                    result is Outcome.Failure -> s.copy(
                        isMonthLoading = false,
                        monthData = null,
                        monthError = RunkkErrorText.of(result.error)
                    )

                    else -> s
                }
            }
        }
    }

    private fun moveMonth(delta: Long) {
        _state.update { it.copy(month = it.month.plusMonths(delta), monthData = null) }
        refreshMonth()
    }

    /** 러닝 선택의 러닝 점수. 실패해도 목록은 그대로 그린다(점수만 빠진다). */
    private fun loadScores() {
        scoresJob?.cancel()
        scoresJob = viewModelScope.launch {
            val result = runBody.scores()
            if (result is Outcome.Success) {
                _state.update { s -> s.copy(scores = result.value.associateBy { it.recordId }) }
            }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(zone))

    private fun loadSession(recordId: String) {
        sessionJob?.cancel()
        _state.update { it.copy(session = RunBodySessionState.Loading) }
        sessionJob = viewModelScope.launch {
            var polls = 0
            var waited = 0L
            while (true) {
                val next = sessionState(runBody.session(recordId))
                _state.update { if (it.selectedRecordId == recordId) it.copy(session = next) else it }
                val isWaiting = next == RunBodySessionState.Pending || next == RunBodySessionState.Analyzing
                // 기다리다 분석이 끝난 기록은 서버가 점수를 막 남겼으니 러닝 선택 점수를 다시 읽는다(G-30)
                if (polls > 0 && next is RunBodySessionState.Done) loadScores()
                if (!isWaiting || waited >= MAX_POLL_WAIT_MILLIS) break
                val pause = pollDelay(polls)
                polls++
                waited += pause
                delay(pause)
            }
        }
    }

    private fun sessionState(result: Outcome<RunBodySession>): RunBodySessionState = when (result) {
        is Outcome.Success -> when (result.value.status) {
            RunBodyStatus.PENDING -> RunBodySessionState.Pending

            RunBodyStatus.ANALYZING -> RunBodySessionState.Analyzing

            RunBodyStatus.DONE -> result.value.result?.let(RunBodySessionState::Done) ?: RunBodySessionState.Failed

            RunBodyStatus.FAILED -> if (result.value.failureReason == RunBodyRepository.BIRTH_YEAR_MISSING) {
                RunBodySessionState.NeedsBirthYear
            } else {
                RunBodySessionState.Failed
            }
        }

        is Outcome.Failure -> {
            val error = result.error
            when {
                error is AppError.Rejected && error.code == RunBodyRepository.NOT_FOUND ->
                    RunBodySessionState.NotAnalyzable

                error.isTemporary() -> RunBodySessionState.Unavailable

                else -> RunBodySessionState.Failed
            }
        }
    }

    /** ai 일시 장애(서버 503 RUNBODY_UNAVAILABLE)나 연결 실패: 분석 실패가 아니라 잠시 뒤 다시 읽으면 된다(MR !91 e2e 5). */
    private fun AppError.isTemporary(): Boolean =
        this == AppError.Network || (this is AppError.Server && status == UNAVAILABLE_STATUS)

    companion object {
        private const val UNAVAILABLE_STATUS = 503

        /** 러닝 선택 목록의 기간(기록 목록 탭과 같은 90일). */
        const val RECENT_DAYS = 90L

        /** 대기·분석 중 처음 다시 읽는 간격(서버 상태 조회와 같은 5초, D-443). */
        const val POLL_MILLIS = 5_000L

        /** [POLL_MILLIS] 간격으로 읽는 횟수: 12번 × 5초 = 60초(NFR-053). */
        const val FAST_POLLS = 12

        /** 늘린 간격의 상한. */
        const val MAX_POLL_MILLIS = 40_000L

        /** 다시 읽기를 이어 가는 시간 상한(3분). 넘으면 멈추고 그 상태와 "다시 확인"을 보인다(MR !91 e2e 4). */
        const val MAX_POLL_WAIT_MILLIS = 180_000L

        private const val MAX_DOUBLINGS = 3

        /** [poll]번째 다시 읽기 전 기다림: 처음 [FAST_POLLS]번은 5초, 그 뒤 10·20·40초, 이후 40초. */
        fun pollDelay(poll: Int): Long {
            if (poll < FAST_POLLS) return POLL_MILLIS
            val doublings = (poll - FAST_POLLS + 1).coerceAtMost(MAX_DOUBLINGS)
            return (POLL_MILLIS shl doublings).coerceAtMost(MAX_POLL_MILLIS)
        }
    }
}
