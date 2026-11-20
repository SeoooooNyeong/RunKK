package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.designsystem.StartLoadingMessage.ALMOST_DONE
import com.a207.runkk.app.core.designsystem.StartLoadingMessage.MY_AVATAR_FETCH
import com.a207.runkk.app.core.designsystem.StartLoadingMessage.RANKING
import com.a207.runkk.app.core.designsystem.StartLoadingMessage.START
import com.a207.runkk.app.core.designsystem.StartLoadingMessageHold.Decision
import kotlin.test.assertEquals
import org.junit.Test

/**
 * 시작 로딩 문구 유지 규칙(S15P31A207-345 2차): 첫 문구는 300ms 넘게 이어질 때만 띄우고(받을 것이 없어 바로 넘어가면 문구 없음), 띄운
 * 문구는 최소 600ms 유지한 뒤 바꾼다.
 */
class StartLoadingMessageHoldTest {
    private val hold = StartLoadingMessageHold()

    @Test
    fun `받을 것이 없어 바로 넘어가면 문구를 띄우지 않는다`() {
        assertEquals(Decision(null, 300), hold.next(START, 0))
        assertEquals(Decision(null, null), hold.next(null, 50), "300ms 안에 준비 끝")
    }

    @Test
    fun `첫 문구는 300ms 뒤에 띄운다`() {
        assertEquals(Decision(null, 300), hold.next(START, 1_000))
        assertEquals(Decision(null, 100), hold.next(START, 1_200))
        assertEquals(Decision(START, null), hold.next(START, 1_300))
    }

    @Test
    fun `띄운 문구는 최소 600ms 유지하고 그 뒤 고른 문구로 바꾼다`() {
        hold.next(START, 0)
        hold.next(START, 300)
        assertEquals(Decision(START, 500), hold.next(MY_AVATAR_FETCH, 400), "100ms만 보였다")
        assertEquals(Decision(START, 100), hold.next(RANKING, 800))
        assertEquals(Decision(RANKING, null), hold.next(RANKING, 900), "600ms가 지나면 지금 고른 문구")
        assertEquals(Decision(RANKING, null), hold.next(RANKING, 1_000))
        assertEquals(Decision(RANKING, 500), hold.next(ALMOST_DONE, 1_000))
        assertEquals(Decision(ALMOST_DONE, null), hold.next(ALMOST_DONE, 1_500))
    }

    @Test
    fun `준비가 끝나도 띄운 문구는 최소 시간까지 둔다`() {
        hold.next(START, 0)
        hold.next(START, 300)
        assertEquals(Decision(START, 400), hold.next(null, 500))
        assertEquals(Decision(null, null), hold.next(null, 900))
    }
}
