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
    fun `서버 기본 개인 랜드마크 키는 모두 제 단계 그림으로 찾는다`() {
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
        val drawableOf = mapOf(
            "DOMESTIC" to R.drawable.avatar_backdrop_domestic,
            "EAST_ASIA" to R.drawable.avatar_backdrop_east_asia,
            "INTERCONTINENTAL" to R.drawable.avatar_backdrop_intercontinental
        )
        assertEquals(22, serverKeys.size)
        serverKeys.forEach { (key, stage) ->
            assertEquals(drawableOf.getValue(stage), avatarBackdropRes(key), key)
        }
    }

    @Test
    fun `지운 개성 키는 기본 키가 아니다`() {
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes("KAESONG"))
        assertEquals(R.drawable.avatar_backdrop_domestic, avatarBackdropRes("CHUNCHEON"))
    }

    @Test
    fun `모르는 키는 단계별 기본 그림, 단계도 모르면 기본 배경이다`() {
        assertEquals(R.drawable.avatar_backdrop_east_asia, avatarBackdropRes("LM_23", stage = "EAST_ASIA"))
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes("LM_23"))
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes("LM_23", stage = "MOON"))
    }
}
