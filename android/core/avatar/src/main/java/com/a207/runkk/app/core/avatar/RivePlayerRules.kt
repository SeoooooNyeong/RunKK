package com.a207.runkk.app.core.avatar

import android.view.View

/** [RiveAvatarView]의 플레이어 뷰 규칙(시험할 수 있게 뷰 밖에 둔다). */
internal object RivePlayerRules {
    /** 좌우 뒤집기는 달리기 플레이어만 한다(뷰 `scaleX = -1`, [임시:D-성-130]). 서기(정면)는 늘 1이다. */
    fun scaleX(motion: AvatarMotion, mirrored: Boolean): Float = if (motion == AvatarMotion.RUN && mirrored) -1f else 1f

    /** 지금 동작이 아니면 GONE, 그 동작의 첫 텍스처가 들어가기 전에는 INVISIBLE(기본 그림 숨김), 들어가면 VISIBLE. */
    fun visibility(motion: AvatarMotion, current: AvatarMotion, applied: Set<AvatarMotion>): Int = when {
        motion != current -> View.GONE
        motion in applied -> View.VISIBLE
        else -> View.INVISIBLE
    }

    /** 보이지 않다가 보이게 된 때(`onReady`를 알릴 때). */
    fun becameVisible(before: Int, after: Int): Boolean = before != View.VISIBLE && after == View.VISIBLE
}
