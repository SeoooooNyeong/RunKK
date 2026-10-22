package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.run.FreeRunGoal
import com.a207.runkk.app.domain.run.FreeRunGoalUseCase
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.TodayPlanCache
import com.a207.runkk.app.domain.settings.GuidanceSettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val records = FakeRecordRepository()
    private val pending = FakePendingRunRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneId.of("Asia/Seoul"))

    private val account = FakeAccountRepository()
    private val avatar = FakeAvatarRepository()

    private val settings = FakeSettingsRepository()
    private val km3 = GuidanceSettings.DEFAULT.copy(freeRunTarget = Distance(3_000))

    private val todayPlans = TodayPlanCache()

    private fun viewModel() =
        HomeViewModel(records, pending, account, avatar, FreeRunGoalUseCase(settings), todayPlans, clock)

    @Test
    fun `오늘 계획을 읽으면 앱의 오늘 계획 사본에 알린다(워치 시작 화면, FR-042)`() {
        viewModel()
        assertEquals(PlanSnapshot(Distance(5_000), Pace(330)), todayPlans.plan.value)
    }

    @Test
    fun `오늘 계획을 읽지 못하면 앱의 오늘 계획 사본은 그대로 둔다`() {
        val known = PlanSnapshot(Distance(7_000), null)
        todayPlans.put(known)
        records.todayPlanResult = Outcome.Failure(AppError.Network)
        viewModel()
        assertEquals(known, todayPlans.plan.value)
    }

    @Test
    fun `홈은 오늘 날짜의 대시보드와 오늘 계획을 읽는다`() {
        settings.guidance = km3.copy(shouldFollowCoachPlan = true)
        val vm = viewModel()
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(sampleDashboard, state.dashboard)
        assertEquals(FreeRunGoal(PlanSnapshot(Distance(5_000), Pace(330)), isFromCoachPlan = true), state.freeRunGoal)
        assertEquals(listOf(LocalDate.of(2026, 10, 20)), records.dashboardDates)
    }

    @Test
    fun `오늘 계획이 없으면 자유런 카드는 설정 목표 3km를 보인다(FR-048)`() {
        records.todayPlanResult = Outcome.Success(null)
        settings.guidance = km3
        val vm = viewModel()
        assertEquals(
            FreeRunGoal(PlanSnapshot(Distance(3_000), null), isFromCoachPlan = false),
            vm.state.value.freeRunGoal
        )
    }

    @Test
    fun `코치 계획 따르기가 꺼져 있으면 오늘 계획이 있어도 설정 목표를 보인다`() {
        settings.guidance = km3
        val vm = viewModel()
        assertEquals(
            FreeRunGoal(PlanSnapshot(Distance(3_000), null), isFromCoachPlan = false),
            vm.state.value.freeRunGoal
        )
    }

    @Test
    fun `설정을 바꾸고 홈으로 돌아오면 다시 읽지 않아도 목표가 바로 바뀐다`() {
        records.todayPlanResult = Outcome.Success(null)
        val vm = viewModel()
        assertEquals(FreeRunGoal.NONE, vm.state.value.freeRunGoal)
        settings.guidance = GuidanceSettings.DEFAULT.copy(freeRunTarget = Distance(10_000))
        assertEquals(PlanSnapshot(Distance(10_000), null), vm.state.value.freeRunGoal.plan)
        settings.guidance = GuidanceSettings.DEFAULT
        assertEquals(FreeRunGoal.NONE, vm.state.value.freeRunGoal)
    }

    @Test
    fun `우리 집 아바타는 셀카 얼굴이면 부위 그림을 받고 아니면 받지 않는다`() {
        val plain = viewModel()
        assertEquals(emptyList(), plain.state.value.faceLayers)
        assertEquals(emptyList(), avatar.faceKeys)

        val layer = com.a207.runkk.app.core.model.FaceLayer(com.a207.runkk.app.core.model.FacePart.HAIR, byteArrayOf(1))
        avatar.avatarResult = com.a207.runkk.app.core.model.Outcome.Success(
            com.a207.runkk.app.domain.avatar.Avatar(
                "selfie:face/7/s1",
                null,
                "NORMAL",
                "DAY",
                emptyMap(),
                10,
                true,
                emptyMap()
            )
        )
        avatar.faceResult = com.a207.runkk.app.core.model.Outcome.Success(listOf(layer))
        val selfie = viewModel()
        assertEquals(listOf(layer), selfie.state.value.faceLayers)
        assertEquals(listOf<String?>("selfie:face/7/s1"), avatar.faceKeys)
    }

    @Test
    fun `우리 집·상단 아바타는 서버 얼굴 키(기본 캐릭터 default 키)를 그대로 넘긴다`() {
        // 서버 GET /avatar 모양: 기본 캐릭터면 faceKey "default:BEAR"와 defaultCharacterId "BEAR"가 함께 온다.
        avatar.avatarResult = Outcome.Success(
            com.a207.runkk.app.domain.avatar.Avatar(
                "default:BEAR",
                "BEAR",
                "STAGE1",
                "DAYTIME",
                mapOf(
                    com.a207.runkk.app.domain.avatar.ItemSlot.TOP to 101L,
                    com.a207.runkk.app.domain.avatar.ItemSlot.HAT to 401L
                ),
                100,
                true,
                emptyMap()
            )
        )
        val vm = viewModel()
        assertEquals("default:BEAR", vm.state.value.faceKey)
        assertEquals(emptyList(), vm.state.value.faceLayers)
    }

    @Test
    fun `미전송 기록 수는 보관함 변화를 따라간다(FR-039)`() {
        val vm = viewModel()
        assertEquals(0, vm.state.value.pendingCount)
        pending.pendingRuns.value = listOf(completedRun("a"), completedRun("b"))
        assertEquals(2, vm.state.value.pendingCount)
    }

    @Test
    fun `대시보드를 못 읽으면 오류를 보이고 다시 읽는다`() {
        records.dashboardResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.error)
        assertNull(vm.state.value.dashboard)
        records.dashboardResult = Outcome.Success(sampleDashboard)
        vm.refresh()
        assertNull(vm.state.value.error)
        assertEquals(sampleDashboard, vm.state.value.dashboard)
    }

    @Test
    fun `오늘 계획이 없어도 대시보드는 보인다`() {
        records.todayPlanResult = Outcome.Failure(AppError.Network)
        val vm = viewModel()
        settings.guidance = GuidanceSettings.DEFAULT.copy(shouldFollowCoachPlan = true)
        assertEquals(FreeRunGoal.NONE, vm.state.value.freeRunGoal)
        assertEquals(sampleDashboard, vm.state.value.dashboard)
    }

    @Test
    fun `상단에 본인 닉네임·티어·레벨과 리워드 잔액을 보인다(HiHome)`() {
        val header = viewModel().state.value.header
        assertEquals(HomeHeader(nickname = "달리는곰", tier = "골드", level = 3, balance = 1_250), header)
        assertEquals(listOf(com.a207.runkk.app.domain.account.AccountRepository.SELF), account.profileIds)
    }

    @Test
    fun `프로필·잔액을 못 읽어도 홈은 보이고 그 자리는 비운다`() {
        account.profileResult = Outcome.Failure(AppError.Network)
        avatar.avatarResult = Outcome.Failure(AppError.Network)
        val state = viewModel().state.value
        assertEquals(HomeHeader(), state.header)
        assertFalse(state.isHeaderLoading)
        assertEquals(sampleDashboard, state.dashboard)
    }

    @Test
    fun `러닝 종류 탭을 바꾼다`() {
        val vm = viewModel()
        assertEquals(HomeRunMode.FREE, vm.state.value.mode)
        vm.onModeSelected(HomeRunMode.BATTLE)
        assertEquals(HomeRunMode.BATTLE, vm.state.value.mode)
    }
}
