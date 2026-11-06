package com.a207.runkk.app.ui.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.AchievementIcon
import com.a207.runkk.app.core.designsystem.AchievementRow
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.PendingRunRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 러닝 결과(B-6, D-282, FR-078·FR-079·FR-184~FR-186). 방금 끝난 기록을 기기 보관함(PendingRunRepository.pending)에서 먼저 보이고,
 * 서버에 올라갔으면 RecordRepository.run의 구간 페이스·신기록·심박 구간·리워드 적립을 더한다. 계산은 서버가 한다.
 * 거리·시간·페이스는 서버가 초과 시속 구간을 뺀 값이다(FR-047). 유효·집계 제외는 화면에 보이지 않는다(D29).
 * 휴식(정지 구간 합)은 기기 보관 기록의 정지 구간이나 서버 pausedSeconds로 보인다.
 * 측정 직후 연 결과에서 서버 기록에 티어 평가(evaluation)가 실리면 평가 화면으로 한 번 이동한다(대결 결과와 같은 방식, D-284, U8-6).
 */
@HiltViewModel
class RunResultViewModel @Inject constructor(
    private val pendingRuns: PendingRunRepository,
    private val records: RecordRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RunResultUiState())
    val state: StateFlow<RunResultUiState> = _state.asStateFlow()

    private val _events = Channel<RunResultEvent>(Channel.BUFFERED)
    val events: Flow<RunResultEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    /**
     * 평가 화면을 띄울 차례인가: 측정 직후 진입이고 아직 띄우지 않았다. 한 번만 연다(다시 읽기·보관함 비움 뒤 재조회에도 다시 열지 않는다).
     */
    private var isEvaluationDue = false

    /**
     * [recordId]의 결과를 읽는다. 같은 기록을 다시 부르면 무시하고, [isForced]면 다시 읽는다. [opensEvaluation]은 측정 직후의 첫 읽기에서만
     * 참으로 넘긴다(U8-6).
     * 종료 직후에는 보관함 저장(RunTracker의 비동기 저장)이 아직 끝나지 않았을 수 있어 보관함에 나타날 때까지 잠깐 기다린다.
     */
    fun load(recordId: String, opensEvaluation: Boolean = false, isForced: Boolean = false) {
        if (opensEvaluation && _state.value.recordId == null) isEvaluationDue = true
        if (!isForced && _state.value.recordId == recordId) return
        loadJob?.cancel()
        _state.value = RunResultUiState(recordId = recordId)
        loadJob = viewModelScope.launch {
            val local = withTimeoutOrNull(LOCAL_WAIT_MILLIS) {
                pendingRuns.observePending().mapNotNull { runs -> runs.firstOrNull { it.recordId == recordId } }.first()
            } ?: pendingRuns.pending().getOrNull()?.firstOrNull { it.recordId == recordId }
            if (local != null) {
                _state.update {
                    it.copy(
                        cardKind = local.cardKind,
                        startedAt = local.startedAt,
                        distance = local.distance,
                        elapsed = local.elapsed,
                        averagePace = averagePace(local.elapsed, local.distance),
                        heartRateSource = RunResultUiState.HEART_SOURCE_WATCH,
                        averageHeartRate = local.heartRates.takeIf { hr ->
                            hr.isNotEmpty()
                        }?.map { hr -> hr.bpm }?.average()?.roundToInt(),
                        pausedTotal = local.pauses
                            .fold(Duration.ZERO) { sum, p -> sum.plus(Duration.between(p.from, p.to)) }
                            .takeIf { total -> !total.isZero }
                    )
                }
            }
            if (applyServer(recordId, local == null) || local == null) return@launch
            // 아직 서버에 없다(종료 직후 전송 작업이 도는 중). 전송 작업은 서버 저장 뒤 보관함에서 지우므로(UploadPendingRunsUseCase),
            // 보관함에서 빠지면 서버 기록을 다시 읽어 "보내는 중"을 "달력에 저장했어요"로 바꾼다(E2E 시나리오 3에서 찾은 결함).
            pendingRuns.observePending().first { runs -> runs.none { it.recordId == recordId } }
            applyServer(recordId, shouldShowError = false)
        }
    }

    /** 서버 기록을 읽어 상태에 더한다. 읽었으면 true. 실패하면 [shouldShowError]일 때만 오류 문구를 보인다. */
    private suspend fun applyServer(recordId: String, shouldShowError: Boolean): Boolean =
        when (val server = records.run(recordId)) {
            is Outcome.Success -> {
                val detail = server.value
                val summary = detail.summary
                _state.update {
                    it.copy(
                        isLoading = false,
                        isUploaded = true,
                        cardKind = summary.cardKind,
                        startedAt = summary.startedAt,
                        distance = summary.distance,
                        elapsed = summary.elapsed,
                        averagePace = summary.averagePace ?: averagePace(summary.elapsed, summary.distance),
                        averageHeartRate = detail.averageHeartRate ?: it.averageHeartRate,
                        splitPaces = detail.splitPaces,
                        newRecords = detail.newRecords,
                        heartRateZones = detail.heartRateZones,
                        rewardPoints = detail.rewardPoints,
                        heartRateSource = RunResultUiState.HEART_SOURCE_WATCH,
                        // 서버는 출생연도가 있을 때만 구간을 준다: 심박은 있는데 구간이 없으면 출생연도가 없다.
                        hasBirthYear = (detail.averageHeartRate ?: it.averageHeartRate) == null ||
                            detail.heartRateZones.isNotEmpty(),
                        achievements = achievementsOf(detail.newRecords, detail.rewardPoints),
                        pausedTotal = detail.paused.takeIf { paused -> !paused.isZero } ?: it.pausedTotal
                    )
                }
                detail.evaluation?.let { evaluation ->
                    if (isEvaluationDue) {
                        isEvaluationDue = false
                        _events.trySend(RunResultEvent.NavigateEvaluation(evaluation))
                    }
                }
                true
            }

            is Outcome.Failure -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = if (shouldShowError) RunkkErrorText.of(server.error) else it.error
                    )
                }
                false
            }
        }

    private companion object {
        const val LOCAL_WAIT_MILLIS = 3_000L

        /** 서버 신기록 문구의 이름·값 구분자("최장 거리 신기록 · 직전 4.80km"). */
        const val RECORD_SEPARATOR = " · "

        /** 성과 줄: 신기록(서버 문구를 이름·값으로 나눈다)과 리워드 합계 「리워드」 「+n」. */
        fun achievementsOf(newRecords: List<String>, rewardPoints: Int?): List<AchievementRow> = newRecords.map {
            AchievementRow(
                AchievementIcon.STAR,
                UiText.Plain(it.substringBefore(RECORD_SEPARATOR)),
                it.substringAfter(RECORD_SEPARATOR, missingDelimiterValue = "")
            )
        } + listOfNotNull(
            rewardPoints?.let {
                AchievementRow(AchievementIcon.REWARD, UiText.Res(R.string.run_result_reward), "+$it")
            }
        )
    }

    private fun averagePace(elapsed: Duration, distance: Distance): Pace? =
        if (distance.meters > 0 && !elapsed.isZero) Pace.of(elapsed, distance) else null
}
