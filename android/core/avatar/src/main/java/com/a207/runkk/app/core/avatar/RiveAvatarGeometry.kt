package com.a207.runkk.app.core.avatar

/**
 * Rive 아바타 아트보드와 바닥 규격 상수(design-oo 05-30 4.2·4.4, D-737, D-739, D-764).
 * 네 riv 파일 모두 아트보드 800x1000이고 캐릭터는 가로 가운데, 발끝은 위에서 80.4%다. riv가 바뀌면 이 값과 피그마 프레임 가이드를
 * 함께 고친다(H-74).
 */
object RiveAvatarGeometry {
    /** 아트보드 가로/세로(800/1000). */
    const val ASPECT = 0.8f

    /** 아트보드 위에서 발끝 줄까지 비율(그림자 중심 y 804/1000). */
    const val FOOT_LINE = 0.804f

    /** 배경 그림의 바닥선: 그려진 그림 높이의 아래에서 18%. */
    const val FLOOR_FROM_BOTTOM = 0.18f

    /** 아트보드 높이 = 칸 높이 × 0.9(배경이 있는 칸). */
    const val ARTBOARD_OF_SLOT = 0.9f

    /** 아트보드 위 여백 하한: 칸 높이의 6%. */
    const val MIN_TOP_MARGIN = 0.06f

    /** 배경 없는 칸(러닝 중 지도 마커)의 바닥선: 칸 위에서 94%(그림자가 칸 밖으로 잘리지 않게). */
    const val BARE_FLOOR = 0.94f

    /** 배경 없는 칸의 아트보드 높이 = 칸 높이 × 1.17(아트보드 위 끝 = 칸 위 끝). */
    const val BARE_ARTBOARD_OF_SLOT = 1.17f

    /** 그림자 타원 폭 = 아트보드 높이 × 0.24(정면 몸 폭 238/800 = 아트보드 너비의 0.30, DR-5). */
    const val SHADOW_WIDTH = 0.24f

    /** 그림자 타원 높이 = 아트보드 높이 × 0.048(폭 × 0.2). */
    const val SHADOW_HEIGHT = 0.048f

    /** 그림자 불투명도(검정): 라이트 18%. */
    const val SHADOW_ALPHA_LIGHT = 0.18f

    /** 그림자 불투명도(검정): 다크 24%(D-764). */
    const val SHADOW_ALPHA_DARK = 0.24f

    /** 그림자 흐림 반지름(dp). */
    const val SHADOW_BLUR_DP = 4f

    /**
     * 달리기 몸의 가로 중심(아트보드 너비 비율). PoC 실기기 달리기 화면(applied-running.png)의 몸 픽셀 무게중심이 아트보드 가운데보다
     * 아트보드 너비의 0.9% 왼쪽이었다. 실기기 RUN 스냅샷으로 다시 재면 이 값을 고친다(설계 검증 28.7 조건 3).
     */
    const val RUN_BODY_CENTER_X = 0.49f

    /** 뒤집기 전후 몸 가로 위치 차이 허용: 칸 너비의 3%(D-765). 넘으면 [AvatarStageLayout.mirrorShift]로 옮긴다. */
    const val MIRROR_TOLERANCE = 0.03f

    /** 그림자 불투명도(라이트 18%, 다크 24%). */
    fun shadowAlpha(darkTheme: Boolean): Float = if (darkTheme) SHADOW_ALPHA_DARK else SHADOW_ALPHA_LIGHT
}
