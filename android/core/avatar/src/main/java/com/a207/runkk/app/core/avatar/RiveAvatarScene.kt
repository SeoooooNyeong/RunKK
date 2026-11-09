package com.a207.runkk.app.core.avatar

/**
 * 승인된 Rive 파일과 상태 머신 계약(아바타 PoC 온보딩 4절 "Rive 최신 기준"). 이름과 값은 Rive 원본의 계약이라 그대로 쓴다.
 *
 * | 화면 | res/raw |
 * |---|---|
 * | 여자 서 있기 | avatar_idle_layered |
 * | 남자 서 있기 | avatar_male_idle |
 * | 여자 달리기 | avatar_run_approved |
 * | 남자 달리기 | avatar_male_run |
 */
internal object RiveAvatarScene {
    const val INPUT_IS_RUNNING = "isRunning"
    const val INPUT_OUTFIT_ID = "outfit_id"
    const val INPUT_BODY_SHAPE = "body_shape"
    const val INPUT_SELECTED_TOP = "selected_top"
    const val INPUT_SELECTED_BOTTOM = "selected_bottom"
    const val INPUT_SELECTED_SHOES = "selected_shoes"

    private const val BODY_SHAPE_STEP = 50f
    private val LONG_SLEEVE_TOPS = 3..6

    fun rawResource(gender: AvatarGender, motion: AvatarMotion): Int = when (gender) {
        AvatarGender.FEMALE -> if (motion == AvatarMotion.RUN) R.raw.avatar_run_approved else R.raw.avatar_idle_layered
        AvatarGender.MALE -> if (motion == AvatarMotion.RUN) R.raw.avatar_male_run else R.raw.avatar_male_idle
    }

    fun artboard(motion: AvatarMotion): String = if (motion == AvatarMotion.RUN) "Runner" else "Idle"

    fun stateMachine(motion: AvatarMotion): String = if (motion == AvatarMotion.RUN) "Avatar" else "Idle"

    fun bodyShapeValue(shape: AvatarBodyShape): Float = shape.stage * BODY_SHAPE_STEP

    /** 남자 선택 몸 정면의 상의 표면: 기본 0, 긴소매(3..6)는 팔·몸통 UV가 따로라 2, 그 밖은 1. 그림은 standing_body에 그린다. */
    fun topSurface(gender: AvatarGender, top: Int): Float = when {
        top == 0 -> 0f
        gender == AvatarGender.MALE && top in LONG_SLEEVE_TOPS -> 2f
        else -> 1f
    }

    fun bottomSurface(bottom: Int): Float = if (bottom == 0) 0f else 1f

    fun shoesSurface(shoes: Int): Float = if (shoes == 0) 0f else 1f
}
