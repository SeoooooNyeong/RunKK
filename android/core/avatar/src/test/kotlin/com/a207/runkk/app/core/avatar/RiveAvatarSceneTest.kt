package com.a207.runkk.app.core.avatar

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rive 파일 선택과 상태 머신 입력(PoC MainActivity.playerResource·applyTone). 이름과 값은 승인 자산의 계약이라 바꾸지 않는다.
 */
class RiveAvatarSceneTest {
    @Test
    fun `남녀·동작마다 4개 화면 매핑 Rive 파일을 고른다`() {
        assertEquals(R.raw.avatar_idle_layered, RiveAvatarScene.rawResource(AvatarGender.FEMALE, AvatarMotion.STAND))
        assertEquals(R.raw.avatar_run_approved, RiveAvatarScene.rawResource(AvatarGender.FEMALE, AvatarMotion.RUN))
        assertEquals(R.raw.avatar_male_idle, RiveAvatarScene.rawResource(AvatarGender.MALE, AvatarMotion.STAND))
        assertEquals(R.raw.avatar_male_run, RiveAvatarScene.rawResource(AvatarGender.MALE, AvatarMotion.RUN))
    }

    @Test
    fun `정면은 Idle, 달리기는 Runner 아트보드와 Avatar 상태 머신이다`() {
        assertEquals("Idle", RiveAvatarScene.artboard(AvatarMotion.STAND))
        assertEquals("Idle", RiveAvatarScene.stateMachine(AvatarMotion.STAND))
        assertEquals("Runner", RiveAvatarScene.artboard(AvatarMotion.RUN))
        assertEquals("Avatar", RiveAvatarScene.stateMachine(AvatarMotion.RUN))
        assertEquals("isRunning", RiveAvatarScene.INPUT_IS_RUNNING)
        assertEquals("outfit_id", RiveAvatarScene.INPUT_OUTFIT_ID)
        assertEquals("body_shape", RiveAvatarScene.INPUT_BODY_SHAPE)
    }

    @Test
    fun `체형은 body_shape 0·50·100이다`() {
        assertEquals(listOf(0f, 50f, 100f), AvatarBodyShape.entries.map(RiveAvatarScene::bodyShapeValue))
    }

    @Test
    fun `남자 정면 의상 표면은 긴소매 상의 3~6은 2, 기본을 포함한 그 밖은 1이고 하의·신발은 늘 1이다(D-747)`() {
        assertEquals(listOf(1f, 1f, 1f), (0..2).map { RiveAvatarScene.topSurface(AvatarGender.MALE, it) })
        assertEquals(listOf(2f, 2f, 2f, 2f), (3..6).map { RiveAvatarScene.topSurface(AvatarGender.MALE, it) })
        assertEquals(1f, RiveAvatarScene.topSurface(AvatarGender.FEMALE, 4))
        assertEquals(listOf(1f, 1f, 1f), listOf(0, 1, 6).map(RiveAvatarScene::bottomSurface))
        assertEquals(listOf(1f, 1f, 1f), listOf(0, 3, 6).map(RiveAvatarScene::shoesSurface))
        assertEquals("selected_top", RiveAvatarScene.INPUT_SELECTED_TOP)
        assertEquals("selected_bottom", RiveAvatarScene.INPUT_SELECTED_BOTTOM)
        assertEquals("selected_shoes", RiveAvatarScene.INPUT_SELECTED_SHOES)
    }
}
