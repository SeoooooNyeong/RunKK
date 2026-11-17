package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 첫 사용 가이드 단계 목록([임시:D-성-294], [임시:D-성-301]): 자유런, 배틀런, 기록런, 내 러너 4단계다. 닫힌 기능의 단계는 빼고 번호를
 * 다시 센다. 옛 8단계의 주간 목표, 크루, 랭킹, 마이, 포인트 단계는 없다.
 */
class GuideStepTest {
    @Test
    fun `2차 배포(모두 열림)는 4단계다`() {
        assertEquals(
            listOf(GuideStep.FREE_RUN, GuideStep.BATTLE_RUN, GuideStep.RECORD_RUN, GuideStep.MY_RUNNER),
            GuideStep.visibleIn(ReleaseScope.ALL_OPEN)
        )
        assertEquals(4, GuideStep.entries.size)
    }

    @Test
    fun `1차 배포는 3단계다`() {
        assertEquals(
            listOf(GuideStep.FREE_RUN, GuideStep.BATTLE_RUN, GuideStep.RECORD_RUN),
            GuideStep.visibleIn(ReleaseScope.FIRST_RELEASE)
        )
    }

    @Test
    fun `AVATAR_CUSTOMIZE가 닫히면 MY_RUNNER가 빠지고 번호를 다시 센다`() {
        val closed = GuideStep.visibleIn(ReleaseScope.opened(setOf(UnreleasedFeature.CREW)))
        assertEquals(3, closed.size)
        assertEquals(false, GuideStep.MY_RUNNER in closed)
        val opened = GuideStep.visibleIn(ReleaseScope.opened(setOf(UnreleasedFeature.AVATAR_CUSTOMIZE)))
        assertEquals(3, opened.indexOf(GuideStep.MY_RUNNER), "넷째 단계")
    }

    @Test
    fun `하단 탭 가운데 기록런 탭만 가이드 앵커다`() {
        assertEquals(
            mapOf(
                RunkkTab.CREW to null,
                RunkkTab.RECORD_RUN to GuideStep.RECORD_RUN,
                RunkkTab.HOME to null,
                RunkkTab.RANKING to null,
                RunkkTab.ME to null
            ),
            RunkkTab.entries.associateWith { it.guideStep }
        )
    }
}
