package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 배경 키 → 그림(FR-202, D-107, [임시:D-성-186]): 기본 키는 도시마다 제 그림, 모르는 키는 단계별 기본 그림, 키가 없으면 기본 배경. */
class AvatarBackdropTest {
    @Test
    fun `키가 없으면 기본 배경이다`() {
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes(null))
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes(null, stage = "DOMESTIC"))
    }

    @Test
    fun `D-성-186 기본 키는 도시마다 제 그림으로 찾는다`() {
        assertEquals(R.drawable.avatar_backdrop_jeju, avatarBackdropRes("JEJU"))
        assertEquals(R.drawable.avatar_backdrop_bangkok, avatarBackdropRes("BANGKOK"))
        assertEquals(R.drawable.avatar_backdrop_kuala_lumpur, avatarBackdropRes("KUALA_LUMPUR"))
        assertEquals(R.drawable.avatar_backdrop_earth_lap, avatarBackdropRes("EARTH_LAP"))
    }

    @Test
    fun `서버 기본 개인 랜드마크 키는 모두 제 도시 그림으로 찾고 서로 겹치지 않는다`() {
        // 서버 SettingKey.Defaults.LANDMARKS 의 개인 단계(국내·동아시아·대륙 간) 키 사본(H-성-2). 서버 목록을 바꾸면 여기도 고친다.
        val serverKeys = listOf(
            "INCHEON" to "DOMESTIC",
            "CHUNCHEON" to "DOMESTIC",
            "DAEJEON" to "DOMESTIC",
            "JEONJU" to "DOMESTIC",
            "YEOSU" to "DOMESTIC",
            "BUSAN" to "DOMESTIC",
            "JEJU" to "DOMESTIC",
            "BEIJING" to "EAST_ASIA",
            "TOKYO" to "EAST_ASIA",
            "TAIPEI" to "EAST_ASIA",
            "HONG_KONG" to "EAST_ASIA",
            "HANOI" to "EAST_ASIA",
            "BANGKOK" to "EAST_ASIA",
            "KUALA_LUMPUR" to "INTERCONTINENTAL",
            "HONOLULU" to "INTERCONTINENTAL",
            "SYDNEY" to "INTERCONTINENTAL",
            "LONDON" to "INTERCONTINENTAL",
            "PARIS" to "INTERCONTINENTAL",
            "LOS_ANGELES" to "INTERCONTINENTAL",
            "NEW_YORK" to "INTERCONTINENTAL",
            "SANTIAGO" to "INTERCONTINENTAL",
            "EARTH_LAP" to "INTERCONTINENTAL"
        )
        val stageDrawables = setOf(
            R.drawable.avatar_backdrop_default,
            R.drawable.avatar_backdrop_domestic,
            R.drawable.avatar_backdrop_east_asia,
            R.drawable.avatar_backdrop_intercontinental
        )
        assertEquals(22, serverKeys.size)
        val drawables = serverKeys.map { (key, stage) ->
            avatarBackdropRes(key, stage).also { assertFalse(it in stageDrawables, key) }
        }
        assertEquals(22, drawables.toSet().size)
    }

    @Test
    fun `지운 개성 키는 기본 키가 아니다`() {
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes("KAESONG"))
        assertEquals(R.drawable.avatar_backdrop_chuncheon, avatarBackdropRes("CHUNCHEON"))
    }

    @Test
    fun `모르는 키는 단계별 기본 그림, 단계도 모르면 기본 배경이다`() {
        assertEquals(R.drawable.avatar_backdrop_east_asia, avatarBackdropRes("LM_23", stage = "EAST_ASIA"))
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes("LM_23"))
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes("LM_23", stage = "MOON"))
    }
}
