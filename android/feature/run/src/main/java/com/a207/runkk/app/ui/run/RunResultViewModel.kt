package com.a207.runkk.app.ui.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 러닝 결과(B4, FR-078·FR-079·FR-184~FR-186). 방금 끝난 기록을 기기 보관함(PendingRunRepository.pending)에서 먼저 보이고,
 * 서버에 올라갔으면 RecordRepository.run의 날씨·칼로리·신기록·심박 구간을 더한다. 계산은 서버가 한다.
 * 서버가 무효로 판정한 기록이면 "집계 제외"를 보이고 칼로리는 뺀다(서버 판정 전, 곧 보관함 기록만 있을 때는 보이지 않는다).
 * 거리·시간·페이스는 서버가 초과 시속 구간을 뺀 값이다(FR-047).
 * 정지 구간 합계("일시정지 n분 제외")는 기기 보관 기록에만 있어(서버 기록 상세에 없음) 보관함에서 읽은 경우에만 보인다.
 */
@HiltViewModel
class RunResultViewModel @Inject constructor(
    private val pendingRuns: PendingRunRepository,
    private val records: RecordRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RunResultUiState())
    val state: StateFlow<RunResultUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /**
     * [recordId]의 결과를 읽는다. 같은 기록을 다시 부르면 무시하고, [isForced]면 다시 읽는다.
     * 종료 직후에는 보관함 저장(RunTracker의 비동기 저장)이 아직 끝나지 않았을 수 있어 보관함에 나타날 때까지 잠깐 기다린다.
     */
    fun load(recordId: String, isForced: Boolean = false) {
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
                        averageHeartRate = local.heartRates.takeIf { hr ->
                            hr.isNotEmpty()
                        }?.map { hr -> hr.bpm }?.average()?.roundToInt(),
                        route = local.route.map { p -> p.point },
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
                        route = detail.route.ifEmpty { it.route },
                        weather = detail.weather,
                        // 무효(집계 제외) 기록은 칼로리를 보이지 않는다(사용자 결정, 순간이동 거리로 센 값이 나오지 않게).
                        isExcluded = !summary.isValid,
                        calories = detail.calories.takeIf { summary.isValid },
                        newRecords = detail.newRecords,
                        heartRateZones = detail.heartRateZones.toList()
                    )
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
    }

    private fun averagePace(elapsed: Duration, distance: Distance): Pace? =
        if (distance.meters > 0 && !elapsed.isZero) Pace.of(elapsed, distance) else null
}
