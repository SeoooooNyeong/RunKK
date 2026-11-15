package com.a207.runkk.app.core.designsystem

/** 트랙 장면 치수와 장식 이동 계산. 모든 치수는 dp, 거리와 속도는 m와 m/s다. */
internal object TrackScene {
    data class Dimensions(
        val skyHeight: Float,
        val laneHeight: Float,
        val rulerHeight: Float,
        val mySize: Float,
        val otherSize: Float,
        val myFootY: Float,
        val otherFootY: Float,
        val tagHeight: Float,
        val runnerScale: Float
    ) {
        val myTagY: Float get() = myFootY - mySize - TAG_GAP - tagHeight
        val otherTagY: Float get() = otherFootY - otherSize - TAG_GAP - tagHeight
    }

    /** 위쪽 조작부(진행 막대와 보기 전환) 아래 6dp를 남기고 주자와 이름표를 함께 줄인다. */
    fun dimensions(cardHeight: Float, overlayBottom: Float = 91f): Dimensions {
        val sky = (cardHeight - LANE_HEIGHT - RULER_HEIGHT).coerceAtLeast(0f)
        val otherFoot = sky + 61f
        val scale = ((otherFoot - overlayBottom - 6f - TAG_GAP) / (88f + 28f)).coerceIn(0f, 1f)
        return Dimensions(
            skyHeight = sky,
            laneHeight = LANE_HEIGHT,
            rulerHeight = RULER_HEIGHT,
            mySize = 105f * scale,
            otherSize = 88f * scale,
            myFootY = sky + 129f,
            otherFootY = otherFoot,
            tagHeight = 28f * scale,
            runnerScale = scale
        )
    }

    fun advanceTravel(
        current: Float,
        seconds: Float,
        speedMetersPerSecond: Float,
        isMoving: Boolean,
        reduceMotion: Boolean
    ): Float = if (isMoving && !reduceMotion && speedMetersPerSecond.isFinite() && speedMetersPerSecond > 0f) {
        current + seconds.coerceAtLeast(0f) * speedMetersPerSecond
    } else {
        current
    }

    /** 22dp 점선과 26dp 공백을 하나의 주기로 되감아 끊김 없이 왼쪽으로 흘린다. */
    fun dashOffset(travelMeters: Float, width: Float): Float {
        val phase = travelMeters * width / TrackLayout.WINDOW_METERS.toFloat() % DASH_PERIOD
        return if (phase == 0f) 0f else -phase
    }

    const val LANE_HEIGHT = 138f
    const val RULER_HEIGHT = 26f
    const val DASH_PERIOD = 48f
    private const val TAG_GAP = 4f
}
