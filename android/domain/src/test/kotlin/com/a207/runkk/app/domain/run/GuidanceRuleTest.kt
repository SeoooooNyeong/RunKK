package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.settings.GuidanceSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** GuidanceRule 6개(6.1.14 표): GuidanceContext의 todayPlan·gapMeters·rank 입력. */
class GuidanceRuleTest {
    @Test
    fun `Split - 새 1km 지점마다 한 번`() {
        val rule = SplitGuidanceRule()
        assertNull(rule.check(context(progress(999))))
        val g = assertNotNull(rule.check(context(progress(1_000))))
        assertEquals(Guidance.Kind.SPLIT, g.kind)
        assertTrue(g.message.contains("1km"))
        assertNull(rule.check(context(progress(1_500))))
        assertEquals(Guidance.Kind.SPLIT, rule.check(context(progress(2_010)))?.kind)
    }

    @Test
    fun `Split - 음성·진동 설정을 안내 방식에 싣는다`() {
        val settings = GuidanceSettings.DEFAULT.copy(runVoice = false, runVibration = true)
        val g = assertNotNull(SplitGuidanceRule().check(context(progress(1_000), settings = settings)))
        assertEquals(false, g.voice)
        assertEquals(true, g.vibration)
    }

    @Test
    fun `TargetReached - 목표 거리 처음 도달 때 한 번`() {
        val rule = TargetReachedRule()
        val plan = PlanSnapshot(Distance(3_000), null)
        assertNull(rule.check(context(progress(2_999), plan)))
        assertEquals(Guidance.Kind.TARGET_REACHED, rule.check(context(progress(3_000), plan))?.kind)
        assertNull(rule.check(context(progress(3_500), plan)))
    }

    @Test
    fun `TargetReached - 목표가 없으면 안내 없음`() {
        assertNull(TargetReachedRule().check(context(progress(3_000))))
    }

    @Test
    fun `Pace - 코치 계획 따르기 켬, 새 1km 지점, 평균이 목표의 1·1배 이상`() {
        val plan = PlanSnapshot(null, Pace(300))
        val on = GuidanceSettings.DEFAULT.copy(followCoachPlan = true)
        assertEquals(
            Guidance.Kind.PACE,
            PaceGuidanceRule().check(context(progress(1_000, average = Pace(330)), plan, settings = on))?.kind
        )
        assertNull(PaceGuidanceRule().check(context(progress(1_000, average = Pace(329)), plan, settings = on)))
        assertNull(PaceGuidanceRule().check(context(progress(999, average = Pace(400)), plan, settings = on)))
    }

    @Test
    fun `Pace - 코치 계획 따르기 꺼짐이거나 목표 페이스가 없으면 안내 없음`() {
        val off = GuidanceSettings.DEFAULT.copy(followCoachPlan = false)
        val on = GuidanceSettings.DEFAULT.copy(followCoachPlan = true)
        val slow = progress(1_000, average = Pace(400))
        assertNull(PaceGuidanceRule().check(context(slow, PlanSnapshot(null, Pace(300)), settings = off)))
        assertNull(PaceGuidanceRule().check(context(slow, PlanSnapshot(Distance(5_000), null), settings = on)))
    }

    @Test
    fun `Breath - 기준 심박 이상 30초 지속, 직전 안내 뒤 60초`() {
        // 2026년 기준 1996년생 = 30세 → (220 - 30) × 0.9 = 171bpm
        val rule = BreathGuidanceRule()
        fun ctx(second: Long) =
            context(progress(second * 3, second, heartRate = HeartRateSample(at(second), 175)), birthYear = 1996)
        assertNull(rule.check(ctx(0)))
        assertNull(rule.check(ctx(29)))
        assertEquals(Guidance.Kind.BREATH, rule.check(ctx(30))?.kind)
        assertNull(rule.check(ctx(60)))
        assertEquals(Guidance.Kind.BREATH, rule.check(ctx(90))?.kind)
    }

    @Test
    fun `Breath - 기준 아래로 내려가면 지속 시간을 다시 센다`() {
        val rule = BreathGuidanceRule()
        fun ctx(second: Long, bpm: Int) =
            context(progress(second * 3, second, heartRate = HeartRateSample(at(second), bpm)), birthYear = 1996)
        rule.check(ctx(0, 175))
        rule.check(ctx(20, 150))
        assertNull(rule.check(ctx(30, 175)))
        assertEquals(Guidance.Kind.BREATH, rule.check(ctx(60, 175))?.kind)
    }

    @Test
    fun `Breath - 출생연도가 없으면 180bpm, 심박이 없으면 판단하지 않는다`() {
        val rule = BreathGuidanceRule()
        fun ctx(second: Long, bpm: Int) =
            context(progress(second * 3, second, heartRate = HeartRateSample(at(second), bpm)))
        rule.check(ctx(0, 179))
        assertNull(rule.check(ctx(40, 179)))
        assertNull(rule.check(context(progress(300))))
        rule.check(ctx(100, 180))
        assertEquals(Guidance.Kind.BREATH, rule.check(ctx(130, 180))?.kind)
    }

    @Test
    fun `Gap - 중간 알림 간격의 새 지점, 음성 켬 - 앞섬·뒤처짐과 남은 거리`() {
        val settings = GuidanceSettings.DEFAULT.copy(battleVoice = true, gapAlertMeters = 500)
        val plan = PlanSnapshot(Distance(3_000), null)
        val rule = GapGuidanceRule()
        assertNull(rule.check(context(progress(499), plan, gapMeters = 70, settings = settings)))
        val ahead = assertNotNull(rule.check(context(progress(500), plan, gapMeters = 70, settings = settings)))
        assertEquals(Guidance.Kind.GAP, ahead.kind)
        assertTrue(ahead.message.contains("70m 앞서는 중"))
        assertTrue(ahead.message.contains("2500m"))
        val behind = assertNotNull(rule.check(context(progress(1_000), plan, gapMeters = -30, settings = settings)))
        assertTrue(behind.message.contains("30m 뒤처져"))
    }

    @Test
    fun `Gap - 중간 알림 간격이 바뀌어도 이미 지난 지점을 다시 알리지 않는다`() {
        val rule = GapGuidanceRule()
        val every500 = GuidanceSettings.DEFAULT.copy(gapAlertMeters = 500)
        val every1000 = GuidanceSettings.DEFAULT.copy(gapAlertMeters = 1_000)
        assertNotNull(rule.check(context(progress(1_500), gapMeters = 5, settings = every500)))
        assertNull(rule.check(context(progress(1_600), gapMeters = 5, settings = every1000)))
        assertNotNull(rule.check(context(progress(2_000), gapMeters = 5, settings = every1000)))
    }

    @Test
    fun `Gap - 음성 꺼짐이거나 격차가 없으면 안내 없음`() {
        val off = GuidanceSettings.DEFAULT.copy(battleVoice = false)
        assertNull(GapGuidanceRule().check(context(progress(1_000), gapMeters = 70, settings = off)))
        assertNull(GapGuidanceRule().check(context(progress(1_000))))
    }

    @Test
    fun `Rank - 새 1km 지점, 순위 알림 켬 - n명 중 n위`() {
        val rule = RankGuidanceRule()
        val g = assertNotNull(rule.check(context(progress(1_000), rank = CrewRank(2, 5))))
        assertEquals(Guidance.Kind.RANK, g.kind)
        assertTrue(g.message.contains("5명 중 2위"))
        val off = GuidanceSettings.DEFAULT.copy(rankAlert = false)
        assertNull(RankGuidanceRule().check(context(progress(1_000), rank = CrewRank(2, 5), settings = off)))
        assertNull(RankGuidanceRule().check(context(progress(1_000))))
    }

    @Test
    fun `CoachingJudge - 모드마다 규칙 묶음을 조립한다`() {
        assertEquals(
            setOf(
                SplitGuidanceRule::class,
                TargetReachedRule::class,
                PaceGuidanceRule::class,
                BreathGuidanceRule::class
            ),
            CoachingJudge.forMode(RunMode.FREE).rules.map { it::class }.toSet()
        )
        assertEquals(
            setOf(GapGuidanceRule::class, BreathGuidanceRule::class),
            CoachingJudge.forMode(RunMode.BATTLE).rules.map { it::class }.toSet()
        )
        assertEquals(
            setOf(RankGuidanceRule::class, BreathGuidanceRule::class),
            CoachingJudge.forMode(RunMode.CREW_RUN).rules.map { it::class }.toSet()
        )
    }

    @Test
    fun `CoachingJudge - null이 아닌 안내만 모은다`() {
        val judge = CoachingJudge.forMode(RunMode.FREE)
        val guidances = judge.evaluate(context(progress(3_000), PlanSnapshot(Distance(3_000), null)))
        assertEquals(listOf(Guidance.Kind.SPLIT, Guidance.Kind.TARGET_REACHED), guidances.map { it.kind })
    }

    @Test
    fun `F-2 Gap - 대결 진동 설정을 따른다`() {
        val vibrate = GuidanceSettings.DEFAULT.copy(gapAlertMeters = 500, battleVibration = true)
        val silent = GuidanceSettings.DEFAULT.copy(gapAlertMeters = 500, battleVibration = false)
        assertTrue(
            assertNotNull(GapGuidanceRule().check(context(progress(500), gapMeters = 5, settings = vibrate))).vibration
        )
        assertFalse(
            assertNotNull(GapGuidanceRule().check(context(progress(500), gapMeters = 5, settings = silent))).vibration
        )
    }
}
