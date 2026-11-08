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
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 런바디 화면(D-453 RunBodySessionCard·RunBodyMonthSection, FR-066·FR-266·FR-267). 요약 탭은 리포트(RecordRepository.report)와 최근
 * 10회 변화(RunBodyRepository.trend, G-14), 러닝별 탭은
 * 최근 [RECENT_DAYS]일 기록(RecordRepository.runs)과 고른 기록의 분석(RunBodyRepository.session), 월간 탭은 월간 집계(RunBodyRepository.month)다.
 * 분석은 러닝 저장 때 서버가 자동 제출한다(S-31). 대기·분석 중이면 [POLL_MILLIS]마다 [MAX_POLLS]번까지 다시 읽어 완료되면 같은 칸에 결과를 보인다
 * (NFR-053 60초). 실패면 "상태 다시 확인"([onRetrySession])으로 다시 읽는다. 러닝 선택(G-30)의 러닝 점수는 기록 목록과 함께 읽고
 * (RunBodyRepository.scores), 못 읽으면 점수 없이 목록만 보인다.
 */
@HiltViewModel
class RunBodyViewModel @Inject constructor(
    private val records: RecordRepository,
    private val runBody: RunBodyRepository,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(RunBodyUiState(today = LocalDate.now(clock), zone = clock.zone))
    val state: StateFlow<RunBodyUiState> = _state.asStateFlow()
    private var hasRuns = false
    private var hasMonth = false
    private var runsJob: Job? = null
    private var sessionJob: Job? = null
    private var monthJob: Job? = null
    private var scoresJob: Job? = null
    private var trendJob: Job? = null

    init {
        refreshReport()
        refreshTrend()
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

    /** 주간/월간 토글(G-14). 다시 읽지 않고 빈도 줄만 바꾼다. */
    fun onPeriod(period: RunBodyPeriod) = _state.update { it.copy(period = period) }

    /** 최근 기록을 읽고, 고른 기록이 없거나 목록에 없으면 가장 최근 기록을 골라 분석을 읽는다. */
    fun refreshRuns() {
        hasRuns = true
        val today = LocalDate.now(clock)
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

    fun onPreviousMonth() = moveMonth(-1)

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

    private fun loadSession(recordId: String) {
        sessionJob?.cancel()
        _state.update { it.copy(session = RunBodySessionState.Loading) }
        sessionJob = viewModelScope.launch {
            var polls = 0
            while (true) {
                val next = sessionState(runBody.session(recordId))
                _state.update { if (it.selectedRecordId == recordId) it.copy(session = next) else it }
                val isWaiting = next == RunBodySessionState.Pending || next == RunBodySessionState.Analyzing
                // 기다리다 분석이 끝난 기록은 서버가 점수를 막 남겼으니 러닝 선택 점수를 다시 읽는다(G-30)
                if (polls > 0 && next is RunBodySessionState.Done) loadScores()
                if (!isWaiting || polls >= MAX_POLLS) break
                polls++
                delay(POLL_MILLIS)
            }
        }
    }

    private fun sessionState(result: Outcome<RunBodySession>): RunBodySessionState = when (result) {
        is Outcome.Success -> when (result.value.status) {
            RunBodyStatus.PENDING -> RunBodySessionState.Pending
            RunBodyStatus.ANALYZING -> RunBodySessionState.Analyzing
            RunBodyStatus.DONE -> result.value.result?.let(RunBodySessionState::Done) ?: RunBodySessionState.Failed
            RunBodyStatus.FAILED -> RunBodySessionState.Failed
        }

        is Outcome.Failure -> {
            val error = result.error
            if (error is AppError.Rejected && error.code == RunBodyRepository.NOT_FOUND) {
                RunBodySessionState.NotAnalyzable
            } else {
                RunBodySessionState.Failed
            }
        }
    }

    companion object {
        /** 러닝 선택 목록의 기간(기록 목록 탭과 같은 90일). */
        const val RECENT_DAYS = 90L

        /** 대기·분석 중 다시 읽는 간격(서버 상태 조회와 같은 5초, D-443). */
        const val POLL_MILLIS = 5_000L

        /** 다시 읽는 횟수 상한: 12번 × 5초 = 60초(NFR-053). 넘으면 멈추고 그 상태를 그대로 보인다. */
        const val MAX_POLLS = 12
    }
}
