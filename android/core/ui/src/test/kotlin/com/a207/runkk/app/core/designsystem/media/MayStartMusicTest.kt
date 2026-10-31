package com.a207.runkk.app.core.designsystem.media

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** 배경 음악을 처음 틀 때 다른 앱 음악에 양보하는 규칙(FR-235, D-346). */
class MayStartMusicTest {
    @Test
    fun `처음 틀 때 다른 앱 음악이 나오고 있으면 틀지 않는다`() {
        assertFalse(mayStartMusic(hasPlayed = false, isMusicActive = true, ownPlaying = 0))
    }

    @Test
    fun `처음 틀 때 아무 음악도 없으면 튼다`() {
        assertTrue(mayStartMusic(hasPlayed = false, isMusicActive = false, ownPlaying = 0))
    }

    @Test
    fun `나오는 음악이 우리 앞 화면 음악이면 다른 앱 음악으로 보지 않고 튼다(로그인에서 홈으로 넘어갈 때)`() {
        assertTrue(mayStartMusic(hasPlayed = false, isMusicActive = true, ownPlaying = 1))
    }

    @Test
    fun `한 번 튼 뒤 멈췄다 다시 틀 때는 다른 앱 음악을 보지 않고 이어 튼다`() {
        assertTrue(mayStartMusic(hasPlayed = true, isMusicActive = true, ownPlaying = 0))
    }
}
