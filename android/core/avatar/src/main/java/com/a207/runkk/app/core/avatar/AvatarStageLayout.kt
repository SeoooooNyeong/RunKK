package com.a207.runkk.app.core.avatar

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 칸 하나의 바닥 규격 배치(design-oo 05-30 4.4). 모든 값은 칸과 같은 단위(px 또는 dp)이고 칸 왼쪽 위가 원점이다.
 * 아트보드 상자([artboardLeft], [artboardTop], [artboardWidth] x [artboardHeight])에 Rive 뷰를 `Fit.CONTAIN`으로 넣으면
 * 여백 없이 맞고 발끝 줄이 [floorY]에 온다. 배경 없는 칸은 아트보드가 칸 아래로 넘친다(발 아래 빈 자리).
 */
data class AvatarStageLayout(
    val slotWidth: Float,
    val slotHeight: Float,
    /** Crop·BottomCenter로 그린 배경 그림 높이. 배경 없는 칸이면 null. */
    val drawnImageHeight: Float?,
    val floorY: Float,
    val artboardHeight: Float,
    val artboardWidth: Float,
    val artboardLeft: Float,
    val artboardTop: Float
) {
    val shadowWidth: Float get() = RiveAvatarGeometry.SHADOW_WIDTH * artboardHeight
    val shadowHeight: Float get() = RiveAvatarGeometry.SHADOW_HEIGHT * artboardHeight
    val shadowCenterX: Float get() = slotWidth / 2
    val shadowCenterY: Float get() = floorY

    /**
     * 좌우로 뒤집은 달리기 뷰를 가로로 옮길 거리. 뷰 가운데를 축으로 뒤집으면 몸이 `|1 − 2c| × 아트보드 너비`만큼 옮겨 간다
     * ([bodyCenterX] = c). 그 차이가 칸 너비의 [RiveAvatarGeometry.MIRROR_TOLERANCE]를 넘을 때만 `(2c − 1) × 아트보드 너비`를
     * 돌려주고, 아니면 0이다.
     */
    fun mirrorShift(bodyCenterX: Float = RiveAvatarGeometry.RUN_BODY_CENTER_X): Float {
        val difference = abs(1 - 2 * bodyCenterX) * artboardWidth
        return if (difference >
            RiveAvatarGeometry.MIRROR_TOLERANCE * slotWidth
        ) {
            (2 * bodyCenterX - 1) * artboardWidth
        } else {
            0f
        }
    }

    companion object {
        /**
         * 배경 그림이 있는 칸. 그림([imageWidth] x [imageHeight])을 아래 가운데 정렬로 잘라 채운다(`ContentScale.Crop`,
         * `Alignment.BottomCenter`). 그림 크기를 모르면(0 이하) 칸과 같은 비율로 본다.
         */
        fun of(slotWidth: Float, slotHeight: Float, imageWidth: Float, imageHeight: Float): AvatarStageLayout {
            val drawn = if (imageWidth > 0f && imageHeight > 0f) {
                max(slotHeight, slotWidth * imageHeight / imageWidth)
            } else {
                slotHeight
            }
            val floorY = slotHeight - RiveAvatarGeometry.FLOOR_FROM_BOTTOM * drawn
            var artboard = RiveAvatarGeometry.ARTBOARD_OF_SLOT * slotHeight
            val minTop = RiveAvatarGeometry.MIN_TOP_MARGIN * slotHeight
            if (floorY - RiveAvatarGeometry.FOOT_LINE * artboard < minTop) {
                artboard = (floorY - minTop) / RiveAvatarGeometry.FOOT_LINE
            }
            artboard = min(artboard, slotWidth / RiveAvatarGeometry.ASPECT)
            return placed(slotWidth, slotHeight, drawn, floorY, artboard.coerceAtLeast(0f))
        }

        /** 배경 없는 칸(러닝 중 지도 마커 88x88dp): 바닥선 94%, 아트보드 = min(1.17 × 칸 높이, 칸 너비 / 0.8). */
        fun withoutBackdrop(slotWidth: Float, slotHeight: Float): AvatarStageLayout {
            val floorY = RiveAvatarGeometry.BARE_FLOOR * slotHeight
            val artboard = min(
                RiveAvatarGeometry.BARE_ARTBOARD_OF_SLOT * slotHeight,
                slotWidth / RiveAvatarGeometry.ASPECT
            )
            return placed(slotWidth, slotHeight, null, floorY, artboard)
        }

        private fun placed(
            width: Float,
            height: Float,
            drawn: Float?,
            floorY: Float,
            artboard: Float
        ): AvatarStageLayout {
            val artboardWidth = artboard * RiveAvatarGeometry.ASPECT
            return AvatarStageLayout(
                slotWidth = width,
                slotHeight = height,
                drawnImageHeight = drawn,
                floorY = floorY,
                artboardHeight = artboard,
                artboardWidth = artboardWidth,
                artboardLeft = (width - artboardWidth) / 2,
                artboardTop = floorY - RiveAvatarGeometry.FOOT_LINE * artboard
            )
        }
    }
}
