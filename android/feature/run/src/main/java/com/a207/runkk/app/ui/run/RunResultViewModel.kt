package com.a207.runkk.app.ui.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.PendingRunRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RunResultUiState(
    val loading: Boolean = true,
    val recordId: String? = null,
    val cardKind: CardKind? = null,
    val startedAt: Instant? = null,
    val distance: Distance? = null,
    val elapsed: Duration? = null,
    val averagePace: Pace? = null,
    val averageHeartRate: Int? = null,
    val route: List<GeoPoint> = emptyList(),
    val weather: String? = null,
    val calories: Int? = null,
    val newRecords: List<String> = emptyList(),
    val heartRateZones: List<Pair<String, Duration>> = emptyList(),
    /** 서버에 저장된 기록(날씨·칼로리·신기록이 붙음)인가. 거짓이면 기기 보관 기록이고 전송 대기 중이다. */
    val uploaded: Boolean = false,
    val error: String? = null
)

/**
 * 러닝 결과(B4, FR-078·FR-079·FR-184~FR-186). 방금 끝난 기록을 기기 보관함(PendingRunRepository.pending)에서 먼저 보이고,
 * 서버에 올라갔으면 RecordRepository.run의 날씨·칼로리·신기록·심박 구간을 더한다. 계산은 서버가 한다.
 */
@HiltViewModel
class RunResultViewModel @Inject constructor(
    private val pendingRuns: PendingRunRepository,
    private val records: RecordRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RunResultUiState())
    val state: StateFlow<RunResultUiState> = _state.asStateFlow()

    /** [recordId]의 결과를 읽는다. 같은 기록을 다시 부르면 무시한다. */
    fun load(recordId: String) {
        if (_state.value.recordId == recordId && !_state.value.loading) return
        _state.value = RunResultUiState(recordId = recordId)
        viewModelScope.launch {
            val local = pendingRuns.pending().getOrNull()?.firstOrNull { it.recordId == recordId }
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
                        route = local.route.map { p -> p.point }
                    )
                }
            }
            when (val server = records.run(recordId)) {
                is Outcome.Success -> {
                    val detail = server.value
                    val summary = detail.summary
                    _state.update {
                        it.copy(
                            loading = false,
                            uploaded = true,
                            cardKind = summary.cardKind,
                            startedAt = summary.startedAt,
                            distance = summary.distance,
                            elapsed = summary.elapsed,
                            averagePace = summary.averagePace ?: averagePace(summary.elapsed, summary.distance),
                            route = detail.route.ifEmpty { it.route },
                            weather = detail.weather,
                            calories = detail.calories,
                            newRecords = detail.newRecords,
                            heartRateZones = detail.heartRateZones.toList()
                        )
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = if (local == null) RunkkErrorText.of(server.error) else null)
                }
            }
        }
    }

    private fun averagePace(elapsed: Duration, distance: Distance): Pace? =
        if (distance.meters > 0 && !elapsed.isZero) Pace.of(elapsed, distance) else null
}
