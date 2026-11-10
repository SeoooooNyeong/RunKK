package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

/** 아바타 무대 배경(05-30 4.3 규격 1080×1200)과 화면 전체 배경(세로 그림)을 나눈다. */
class AvatarStageBackdropTest {
    @Test
    fun `키가 없으면 무대용 기본 배경, 다크면 밤 그림이다`() {
        assertEquals(R.drawable.avatar_stage_default, avatarStageBackdropRes(null, isDark = false))
        assertEquals(R.drawable.avatar_stage_default_night, avatarStageBackdropRes(null, isDark = true))
    }

    @Test
    fun `단계 키는 화면 전체 배경과 같은 단계 그림이다`() {
        assertEquals(R.drawable.avatar_backdrop_domestic, avatarStageBackdropRes("CHUNCHEON", isDark = true))
        assertEquals(R.drawable.avatar_backdrop_east_asia, avatarStageBackdropRes("TOKYO", isDark = false))
        assertEquals(
            R.drawable.avatar_backdrop_intercontinental,
            avatarStageBackdropRes("LM_9", "INTERCONTINENTAL", false)
        )
    }

    @Test
    fun `모르는 키와 모르는 단계는 무대용 기본 배경이다`() {
        assertEquals(R.drawable.avatar_stage_default, avatarStageBackdropRes("UNKNOWN", isDark = false))
    }

    @Test
    fun `화면 전체 기본 배경은 무대용 그림이 아니다`() {
        assertEquals(R.drawable.avatar_backdrop_default, avatarBackdropRes(null))
    }
}
