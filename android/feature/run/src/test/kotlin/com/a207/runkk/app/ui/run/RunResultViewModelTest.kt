package com.a207.runkk.app.ui.run

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.AchievementIcon
import com.a207.runkk.app.core.designsystem.AchievementRow
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.Evaluation
import com.a207.runkk.app.domain.run.HeartRateSample
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class RunResultViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val pending = FakePendingRunRepository()
    private val records = FakeRecordRepository()

    @Test
    fun `서버에 아직 없으면 기기 보관 기록으로 결과를 보인다`() {
        pending.runs.value =
            listOf(
                completedRun(
                    heartRates = listOf(HeartRateSample(start, 150), HeartRateSample(start.plusSeconds(5), 154))
                )
            )
        val vm = RunResultViewModel(pending, records)
        vm.load("rec-1")
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals(Distance(5_020), s.distance)
        assertEquals(Pace.of(java.time.Duration.ofSeconds(1_668), Distance(5_020)), s.averagePace)
        assertEquals(152, s.averageHeartRate)
        assertFalse(s.isUploaded)
        assertNull(s.error)
    }

    @Test
    fun `TC-9729 서버 기록이 있으면 구간 페이스·심박 구간·신기록·리워드·휴식을 더한다(FR-078, FR-079)`() {
        pending.runs.value = listOf(completedRun())
        records.runResult = Outcome.Success(recordDetail())
        val vm = RunResultViewModel(pending, records)
        vm.load("rec-1")
        val s = vm.state.value
        assertTrue(s.isUploaded)
        assertEquals(5, s.splitPaces.size)
        assertEquals(listOf("최장 거리 신기록 · 직전 4.80km"), s.newRecords)
        assertEquals(2, s.heartRateZones.size)
        assertEquals(50, s.rewardPoints)
        assertEquals(151, s.averageHeartRate)
        assertEquals(java.time.Duration.ofSeconds(185), s.pausedTotal)
    }

    @Test
    fun `TC-5849 결과 상태는 심박 구간·출처·출생연도·성과를 담는다`() {
        val zones = listOf("Z1", "Z2", "Z3", "Z4", "Z5").associateWith { java.time.Duration.ofMinutes(3) }
        pending.runs.value = listOf(completedRun())
        records.runResult = Outcome.Success(recordDetail().copy(heartRateZones = zones))
        val withHeart = RunResultViewModel(pending, records).apply { load("rec-1") }.state.value

        assertEquals(zones, withHeart.heartRateZones)
        assertEquals(RunResultUiState.HEART_SOURCE_WATCH, withHeart.heartRateSource)
        assertTrue(withHeart.hasBirthYear)
        assertEquals(
            listOf(
                AchievementRow(AchievementIcon.STAR, UiText.Plain("최장 거리 신기록"), "직전 4.80km"),
                AchievementRow(AchievementIcon.REWARD, UiText.Res(R.string.run_result_reward), "+50")
            ),
            withHeart.achievements
        )
        // 결과에는 티어 표시가 없다(FR-289).
        assertTrue(RunResultUiState::class.java.declaredFields.none { it.name.contains("tier", ignoreCase = true) })

        // 심박 없음: 평균 심박도 구간도 없다 → 지표 두 칸, 「워치를 연결하면…」.
        pending.runs.value = listOf(completedRun())
        records.runResult = Outcome.Success(recordDetail().copy(averageHeartRate = null, heartRateZones = emptyMap()))
        val noHeart = RunResultViewModel(pending, records).apply { load("rec-1") }.state.value
        assertNull(noHeart.averageHeartRate)
        assertTrue(noHeart.hasBirthYear)
        assertEquals(emptyMap(), noHeart.heartRateZones)

        // 출생연도 없음: 심박은 있는데 서버가 구간을 못 냈다 → 「출생연도를 입력하면…」.
        records.runResult = Outcome.Success(recordDetail().copy(heartRateZones = emptyMap()))
        val noBirthYear = RunResultViewModel(pending, records).apply { load("rec-1") }.state.value
        assertEquals(151, noBirthYear.averageHeartRate)
        assertFalse(noBirthYear.hasBirthYear)
    }

    @Test
    fun `S15P31A207-251 크루 목표에 반영된 거리는 신기록과 리워드 사이에 크루 이름과 함께 놓는다(B-6 v2)`() {
        pending.runs.value = listOf(completedRun())
        records.runResult = Outcome.Success(
            recordDetail().copy(crewContribution = Distance(5_210), contributedCrewName = "새벽런")
        )
        val s = RunResultViewModel(pending, records).apply { load("rec-1") }.state.value

        assertEquals(
            listOf(
                AchievementRow(AchievementIcon.STAR, UiText.Plain("최장 거리 신기록"), "직전 4.80km"),
                AchievementRow(AchievementIcon.CREW, UiText.Res(R.string.run_result_crew, listOf("새벽런")), "+5.21km"),
                AchievementRow(AchievementIcon.REWARD, UiText.Res(R.string.run_result_reward), "+50")
            ),
            s.achievements
        )
    }

    @Test
    fun `S15P31A207-251 반영 거리가 없으면 크루 줄이 없고 크루 이름을 모르면 이름 없이 적는다`() {
        pending.runs.value = listOf(completedRun())
        records.runResult = Outcome.Success(recordDetail().copy(crewContribution = null, contributedCrewName = "새벽런"))
        val none = RunResultViewModel(pending, records).apply { load("rec-1") }.state.value
        assertTrue(none.achievements.none { it.icon == AchievementIcon.CREW })

        records.runResult = Outcome.Success(recordDetail().copy(crewContribution = Distance(1_000)))
        val unnamed = RunResultViewModel(pending, records).apply { load("rec-1") }.state.value
        assertEquals(
            AchievementRow(AchievementIcon.CREW, UiText.Res(R.string.run_result_crew_unnamed), "+1.00km"),
            unnamed.achievements.single { it.icon == AchievementIcon.CREW }
        )
    }

    @Test
    fun `TC-9730 서버가 무효로 판정해도 결과 상태에는 집계 제외 값이 없고 거리는 서버 값이다`() {
        pending.runs.value = listOf(completedRun())
        val base = recordDetail()
        records.runResult = Outcome.Success(
            base.copy(summary = base.summary.copy(distance = Distance(360), isValid = false))
        )
        val vm = RunResultViewModel(pending, records)
        vm.load("rec-1")
        val s = vm.state.value
        assertTrue(s.isUploaded)
        assertEquals(Distance(360), s.distance)
        assertTrue(
            RunResultUiState::class.java.declaredFields.none {
                it.name in
                    setOf("isExcluded", "calories", "weather")
            }
        )
    }

    @Test
    fun `어디에도 없으면 오류를 보인다`() = runTest(main.dispatcher) {
        records.runResult = Outcome.Failure(AppError.Network)
        val vm = RunResultViewModel(pending, records)
        vm.load("missing")
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertNull(vm.state.value.distance)
    }

    @Test
    fun `같은 기록을 두 번 부르면 다시 읽지 않는다`() {
        pending.runs.value = listOf(completedRun())
        val vm = RunResultViewModel(pending, records)
        vm.load("rec-1")
        pending.pendingFailure = AppError.Storage
        vm.load("rec-1")
        assertEquals(Distance(5_020), vm.state.value.distance)
    }

    @Test
    fun `종료 직후 보관함 저장이 늦어도 저장되면 결과를 보인다`() = runTest(main.dispatcher) {
        val vm = RunResultViewModel(pending, records)
        vm.load("rec-1")
        assertTrue(vm.state.value.isLoading)
        pending.runs.value = listOf(completedRun())
        advanceUntilIdle()
        assertEquals(Distance(5_020), vm.state.value.distance)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `결과를 보는 중에 전송이 끝나면 서버 기록으로 바꾼다(E2E에서 찾은 결함)`() = runTest(main.dispatcher) {
        pending.runs.value = listOf(completedRun())
        val vm = RunResultViewModel(pending, records)
        vm.load("rec-1")
        advanceUntilIdle()
        assertFalse(vm.state.value.isUploaded)

        // 전송 작업(UploadPendingRunsUseCase)이 서버 저장 뒤 보관함에서 지운다.
        records.runResult = Outcome.Success(recordDetail())
        pending.runs.value = emptyList()
        advanceUntilIdle()

        assertTrue(vm.state.value.isUploaded)
        assertEquals(50, vm.state.value.rewardPoints)
    }

    @Test
    fun `보관함에도 서버에도 없으면 기다린 뒤 오류를 보인다`() = runTest(main.dispatcher) {
        records.runResult = Outcome.Failure(AppError.Network)
        val vm = RunResultViewModel(pending, records)
        vm.load("missing")
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `측정 직후 연 결과는 서버 기록에 티어 평가가 있으면 평가 화면 이동을 한 번만 보낸다(D-284)`() = runTest(main.dispatcher) {
        val evaluation = Evaluation(Evaluation.Kind.FIRST_PLACEMENT, "SILVER", 3, null)
        records.runResult = Outcome.Success(recordDetail().copy(evaluation = evaluation))
        val vm = RunResultViewModel(pending, records)
        vm.events.test {
            vm.load("rec-1", opensEvaluation = true)
            assertEquals(RunResultEvent.NavigateEvaluation(evaluation), awaitItem())
            vm.load("rec-1", isForced = true)
            expectNoEvents()
        }
    }

    @Test
    fun `AN8-1 U8-6 측정 직후가 아닌 진입은 평가가 실려 있어도 평가 화면을 열지 않는다`() = runTest(main.dispatcher) {
        val evaluation = Evaluation(Evaluation.Kind.FIRST_PLACEMENT, "SILVER", 3, null)
        records.runResult = Outcome.Success(recordDetail().copy(evaluation = evaluation))
        val vm = RunResultViewModel(pending, records)
        vm.events.test {
            vm.load("rec-1")
            vm.load("rec-1", isForced = true)
            expectNoEvents()
        }
    }

    @Test
    fun `서버 기록에 평가가 없으면 평가 화면으로 가지 않는다`() = runTest(main.dispatcher) {
        records.runResult = Outcome.Success(recordDetail())
        val vm = RunResultViewModel(pending, records)
        vm.events.test {
            vm.load("rec-1")
            expectNoEvents()
        }
    }
}
