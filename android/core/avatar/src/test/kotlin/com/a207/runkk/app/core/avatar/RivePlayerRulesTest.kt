package com.a207.runkk.app.core.avatar

import android.view.View
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 뷰 규칙: 좌우 뒤집기는 달리기 플레이어만 `scaleX = -1`([임시:D-성-130], [임시:D-성-133]), 서기는 늘 1. 플레이어는 그 동작의 첫 텍스처가
 * 들어간 뒤에야 보이고(기본 그림 숨김), 보이지 않다가 보이게 된 때가 `onReady`다([임시:D-성-105]).
 */
class RivePlayerRulesTest {
    @Test
    fun `뒤집기는 달리기 플레이어 scaleX만 -1로 하고 서기는 1이다`() {
        assertEquals(-1f, RivePlayerRules.scaleX(AvatarMotion.RUN, mirrored = true))
        assertEquals(1f, RivePlayerRules.scaleX(AvatarMotion.RUN, mirrored = false))
        assertEquals(1f, RivePlayerRules.scaleX(AvatarMotion.STAND, mirrored = true))
        assertEquals(1f, RivePlayerRules.scaleX(AvatarMotion.STAND, mirrored = false))
    }

    @Test
    fun `지금 동작이 아니면 GONE, 텍스처 전이면 INVISIBLE, 텍스처가 들어가면 VISIBLE이다`() {
        val applied = setOf(AvatarMotion.STAND)
        assertEquals(View.GONE, RivePlayerRules.visibility(AvatarMotion.RUN, AvatarMotion.STAND, applied))
        assertEquals(View.VISIBLE, RivePlayerRules.visibility(AvatarMotion.STAND, AvatarMotion.STAND, applied))
        assertEquals(View.INVISIBLE, RivePlayerRules.visibility(AvatarMotion.RUN, AvatarMotion.RUN, applied))
    }

    @Test
    fun `보이지 않다가 보이게 된 때만 준비 알림이다`() {
        assertTrue(RivePlayerRules.becameVisible(View.INVISIBLE, View.VISIBLE))
        assertTrue(RivePlayerRules.becameVisible(View.GONE, View.VISIBLE))
        assertFalse(RivePlayerRules.becameVisible(View.VISIBLE, View.VISIBLE), "이미 보이면 다시 알리지 않는다")
        assertFalse(RivePlayerRules.becameVisible(View.VISIBLE, View.GONE))
        assertFalse(RivePlayerRules.becameVisible(View.INVISIBLE, View.INVISIBLE))
    }
}
