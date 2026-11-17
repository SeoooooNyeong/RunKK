package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.geometry.Rect
import kotlin.math.max

/**
 * 러닝 결과 무대와 기록 카드 그림 칸의 배치([임시:D-성-280], [임시:D-성-290], [임시:FR-성-33], [임시:FR-성-34]). 순수 계산이다.
 * 무대 그림(1080×1200, 트랙 윗선 아래에서 18%)의 사각형 [background], 전신 아바타 칸 [avatar](발끝이 칸 바닥), 발끝 줄 [feetY],
 * 트랙 윗선 [trackTop]을 같은 단위(dp 또는 px)로 준다. 홈 무대 배치(core.avatar의 AvatarStageLayout)와는 별개다.
 */
data class ResultStageLayout(val background: Rect, val avatar: Rect, val feetY: Float, val trackTop: Float) {
    /** 발밑 그림자 타원의 폭([임시:D-성-287]): 아바타 칸 높이를 아트보드 높이로 바꾼 값(÷0.64)의 0.24. */
    val shadowWidth: Float get() = SHADOW_WIDTH * avatar.height / FIGURE_OF_ARTBOARD

    /** 발밑 그림자 타원의 높이: 같은 아트보드 높이의 0.048. */
    val shadowHeight: Float get() = SHADOW_HEIGHT * avatar.height / FIGURE_OF_ARTBOARD

    companion object {
        /** 결과 화면 수치(폭 360 기준, 결정 U-7): 물리 화면 위쪽 끝에서 잰 dp다([임시:D-성-309]). */
        const val TRACK_TOP = 232f
        const val FEET = 266f
        const val AVATAR_H = 180f

        /** 아바타 칸 너비(아트보드 비율 0.8). */
        const val AVATAR_W = 144f

        /** 무대 그림의 트랙 윗선: 그림 높이의 아래에서 18%(core.avatar RiveAvatarGeometry.FLOOR_FROM_BOTTOM과 같다, [임시:D-성-288]). */
        const val TRACK_FROM_BOTTOM = 0.18f

        /** 결과 내용 시트가 시작하는 높이. */
        const val SHEET_TOP = 300f

        /** 무대 그림 규격 가로/세로(1080/1200). 그림 크기를 모를 때 쓴다. */
        const val STAGE_ASPECT = 0.9f

        /** 그림자 비율과 불투명도(홈 무대 그림자와 같다: core.avatar RiveAvatarGeometry). */
        const val SHADOW_WIDTH = 0.24f
        const val SHADOW_HEIGHT = 0.048f
        const val SHADOW_ALPHA_LIGHT = 0.18f
        const val SHADOW_ALPHA_DARK = 0.24f
        const val SHADOW_BLUR_DP = 4f

        /** 전신 정지 그림 칸 높이 대비 아트보드 높이의 역수(정지 그림 틀이 아트보드의 0.64다). */
        private const val FIGURE_OF_ARTBOARD = 0.64f

        /** 카드 그림 칸에서 아바타 칸 높이 = 칸 높이 × 0.80. */
        private const val CARD_AVATAR_SHARE = 0.80f

        /**
         * 결과 화면 무대(폭 [widthDp]). 트랙 윗선 232, 발끝 266, 아바타 높이 180은 폭과 상관없이 같고, 그림은 가로를 채우되 바닥이
         * 시트(300) 아래로 내려오도록 높이 하한을 둔다(하한이 걸리면 양옆이 잘린다). [imageAspect]는 그림 가로/세로다.
         */
        fun forResult(widthDp: Float, imageAspect: Float): ResultStageLayout {
            val aspect = if (imageAspect > 0f) imageAspect else STAGE_ASPECT
            val height = max(widthDp / aspect, (SHEET_TOP - TRACK_TOP) / TRACK_FROM_BOTTOM)
            val width = height * aspect
            val left = (widthDp - width) / 2
            val top = TRACK_TOP - (1 - TRACK_FROM_BOTTOM) * height
            val avatarLeft = (widthDp - AVATAR_W) / 2
            return ResultStageLayout(
                background = Rect(left, top, left + width, top + height),
                avatar = Rect(avatarLeft, FEET - AVATAR_H, avatarLeft + AVATAR_W, FEET),
                feetY = FEET,
                trackTop = TRACK_TOP
            )
        }

        /**
         * 기록 카드 그림 칸([boxWidth]×[boxHeight]). 그림을 칸에 채워 아래에 맞추고, 아바타는 보이는 트랙의 가운데(아래에서 9%)에 선다.
         * 아바타 칸 높이는 칸 높이의 0.80이다.
         */
        fun forCard(boxWidth: Float, boxHeight: Float, imageAspect: Float): ResultStageLayout {
            val aspect = if (imageAspect > 0f) imageAspect else STAGE_ASPECT
            val height = max(boxHeight, boxWidth / aspect)
            val width = height * aspect
            val left = (boxWidth - width) / 2
            val feet = boxHeight - TRACK_FROM_BOTTOM / 2 * height
            val avatarHeight = CARD_AVATAR_SHARE * boxHeight
            val avatarWidth = avatarHeight * AVATAR_W / AVATAR_H
            val avatarLeft = (boxWidth - avatarWidth) / 2
            return ResultStageLayout(
                background = Rect(left, boxHeight - height, left + width, boxHeight),
                avatar = Rect(avatarLeft, feet - avatarHeight, avatarLeft + avatarWidth, feet),
                feetY = feet,
                trackTop = boxHeight - TRACK_FROM_BOTTOM * height
            )
        }
    }
}
