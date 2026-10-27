package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

/** 배경 키 → 그림(FR-202, D-107): 기본 키는 단계 그림, 모르는 키는 단계별 기본 그림, 키가 없으면 기본 배경. */
class AvatarBackdropTest {
    @Test
    fun `키가 없으면 기본 배경이다`() {
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes(null))
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes(null, stage = "DOMESTIC"))
    }

    @Test
    fun `D-107 기본 키는 단계 그림으로 찾는다`() {
        assertEquals(R.drawable.avatar_backdrop_domestic, avatarBackdropRes("JEJU"))
        assertEquals(R.drawable.avatar_backdrop_east_asia, avatarBackdropRes("BANGKOK"))
        assertEquals(R.drawable.avatar_backdrop_intercontinental, avatarBackdropRes("KUALA_LUMPUR"))
        assertEquals(R.drawable.avatar_backdrop_intercontinental, avatarBackdropRes("EARTH_LAP"))
    }

    @Test
    fun `모르는 키는 단계별 기본 그림, 단계도 모르면 기본 배경이다`() {
        assertEquals(R.drawable.avatar_backdrop_east_asia, avatarBackdropRes("LM_23", stage = "EAST_ASIA"))
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes("LM_23"))
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes("LM_23", stage = "MOON"))
    }
}
