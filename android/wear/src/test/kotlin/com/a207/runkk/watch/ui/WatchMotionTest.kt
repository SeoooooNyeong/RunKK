package com.a207.runkk.watch.ui

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import com.a207.runkk.app.core.model.Distance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 워치 화면 모션 규칙(W41, 피그마 프로토타입): 화면 전환 단위, DISSOLVE 길이, 측정 고리 구간 넘김, 움직임 줄이기. */
class WatchMotionTest {
    private val companion = WatchUiState(isPhoneConnected = true, isRunning = true, distanceMeters = 1_000)

    @Test
    fun `같은 종류 상태는 값이 달라도 같은 화면이라 전환하지 않는다`() {
        assertEquals(
            screenKey(WatchFlow.Countdown(WatchMode.FREE, Distance(3_000), 3), companion),
            screenKey(WatchFlow.Countdown(WatchMode.FREE, Distance(3_000), 2), companion)
        )
        assertNotEquals(
            screenKey(WatchFlow.Main(), companion),
            screenKey(WatchFlow.FreeGoal(listOf(WatchFlow.Choice(null))), companion)
        )
    }

    @Test
    fun `동행 표시는 측정과 일시정지를 다른 화면으로 본다`() {
        val running = screenKey(WatchFlow.Companion, companion)
        val paused = screenKey(WatchFlow.Companion, companion.copy(isPaused = true, isPausable = true))
        assertNotEquals(running, paused)
        assertEquals(running, screenKey(WatchFlow.Companion, companion.copy(distanceMeters = 1_200)))
    }

    @Test
    fun `매칭 중이나 매칭 완료가 끼면 250ms 아니면 220ms로 겹쳐 나타난다`() {
        val picker = WatchFlow.BattleDistance::class
        val matching = WatchFlow.Matching::class
        val matched = WatchFlow.Matched::class
        val countdown = WatchFlow.Countdown::class
        assertEquals(MATCH_FADE_MS, screenFadeMillis(picker, matching))
        assertEquals(MATCH_FADE_MS, screenFadeMillis(matching, matched))
        assertEquals(MATCH_FADE_MS, screenFadeMillis(matched, countdown))
        assertEquals(SCREEN_FADE_MS, screenFadeMillis(countdown, WatchFlow.Running::class))
        assertEquals(250, MATCH_FADE_MS)
        assertEquals(220, SCREEN_FADE_MS)
    }

    @Test
    fun `무제한 고리가 다 찼다가 0 근처로 돌아오면 구간을 넘긴 것이다`() {
        assertTrue(isLapWrap(0.99f, 0.01f))
        assertFalse(isLapWrap(0.40f, 0.41f))
        assertFalse(isLapWrap(0.41f, 0.40f))
    }

    @Test
    fun `움직임을 줄이면 바로 바뀌고 아니면 정한 길이와 곡선으로 움직인다`() {
        assertIs<SnapSpec<Float>>(motionSpec<Float>(still = true, durationMs = DIAL_MS, easing = DialEasing))
        val tween =
            assertIs<TweenSpec<Float>>(motionSpec<Float>(still = false, durationMs = DIAL_MS, easing = DialEasing))
        assertEquals(250, tween.durationMillis)
        assertEquals(DialEasing, tween.easing)
        val delayed = assertIs<TweenSpec<Float>>(
            motionSpec<Float>(still = false, durationMs = CAROUSEL_DURATION_MS, easing = CarouselEasing, delayMs = 120)
        )
        assertEquals(120, delayed.delay)
    }

    @Test
    fun `다이얼 곡선은 넘쳤다 돌아오고 화면 곡선은 넘치지 않는다`() {
        assertTrue(DialEasing.transform(0.5f) > 1f)
        assertEquals(1f, DialEasing.transform(1f), 0.001f)
        assertTrue(ScreenEasing.transform(0.5f) in 0.5f..1f)
    }
}
