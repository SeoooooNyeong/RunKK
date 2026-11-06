package com.a207.runkk.app

import com.a207.runkk.app.core.navigation.Routes
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** 홈 배경 음악을 트는 화면(사용자 요청 2026-10-31): 로그인한 뒤 모든 화면에서 이어지고, 로그인·가입 화면과 측정 중에는 멈춘다. */
class MusicRouteTest {
    @Test
    fun `탭 화면과 결과 화면에서는 홈 음악을 튼다`() {
        listOf(Routes.HOME, Routes.ME, Routes.RANKING, Routes.RECORD_RUN, Routes.BATTLE_FRIEND, Routes.NOTIFICATIONS)
            .forEach { assertTrue(isMusicRoute(Routes.Key(it)), it) }
        assertTrue(isMusicRoute(Routes.RunResult("r1", isJustFinished = true)))
        assertTrue(isMusicRoute(Routes.BattleResult(1L, isJustFinished = true)))
        assertTrue(isMusicRoute(Routes.BattleQuickMatch(1_000L)))
    }

    @Test
    fun `로그인·가입 화면에서는 틀지 않는다(로그인 음악이 따로 있다)`() {
        listOf(Routes.LOGIN, Routes.SIGN_UP, Routes.GUEST_START).forEach {
            assertFalse(isMusicRoute(Routes.Key(it)), it)
        }
    }

    @Test
    fun `측정 중(자유런, 대결 카운트다운·진행, 크루런)에는 멈춘다`() {
        assertFalse(isMusicRoute(Routes.Key(Routes.RUN)))
        assertFalse(isMusicRoute(Routes.BattleRunning(1L, "이O주", 1_000L, "CELEBRITY")))
        assertFalse(isMusicRoute(Routes.CrewRunLobby(1L)))
        assertFalse(isMusicRoute(Routes.CrewRunResult(1L)), "크루런 결과도 크루런 경로라 멈춘다(srs J-7, TF-V12-01)")
    }
}
