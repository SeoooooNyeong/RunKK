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

    /**
     * 남자 서기 상의 표면(selected_top): 실제로 그리는 상의 묶음의 maleTopSurface(1 반팔·민소매, 2 긴소매, DR-성-1). 고른 묶음에
     * 그 체형의 남자 서기 파일이 없으면 합성이 base_top을 그리므로([OutfitLayerComposer.layers]) 표면도 base_top 값이다. 기본 상의
     * (0 = base_top)도 묶음이라 riv 내장 기본 옷 몸(표면 0)은 쓰지 않는다(D-성-115). 목록이 없으면 기존 표(0·1·2 → 1, 3..6 → 2).
     * 여자 서기 riv에는 이 입력이 없다. 파일 확인은 [OutfitLibrary]의 캐시(합성 스레드가 먼저 읽은 결과)를 쓴다.
     */
    fun topSurface(gender: AvatarGender, top: Int, shape: AvatarBodyShape = AvatarBodyShape.BASE): Float {
        if (gender == AvatarGender.FEMALE) return 1f
        val catalog = OutfitLibrary.catalog ?: return (if (top in LONG_SLEEVE_TOPS) 2f else 1f)
        val drawn = catalog.bundle(OutfitPart.TOP, top)
            ?.takeIf { OutfitLibrary.exists(it.file(gender, OutfitSlot.STAND, shape)) }
            ?: catalog.bundle(OutfitPart.TOP, OutfitCatalog.baseKey(OutfitPart.TOP))
        return (drawn?.maleTopSurface ?: 1).toFloat()
    }

    /** 남자 서기 하의 표면: 모든 묶음이 아틀라스 표면 1(D-성-115). */
    @Suppress("UNUSED_PARAMETER")
    fun bottomSurface(bottom: Int): Float = 1f

    /** 남자 서기 신발 표면: 모든 묶음이 아틀라스 표면 1(D-성-115). */
    @Suppress("UNUSED_PARAMETER")
    fun shoesSurface(shoes: Int): Float = 1f
}
