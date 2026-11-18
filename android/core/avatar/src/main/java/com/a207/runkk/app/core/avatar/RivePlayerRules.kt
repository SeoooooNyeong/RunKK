package com.a207.runkk.app.core.avatar

import android.view.View

/** [RiveAvatarView]의 플레이어 뷰 규칙(시험할 수 있게 뷰 밖에 둔다). */
internal object RivePlayerRules {
    /** 좌우 뒤집기는 달리기 플레이어만 한다(뷰 `scaleX = -1`, D-762). 서기(정면)는 늘 1이다. */
    fun scaleX(motion: AvatarMotion, mirrored: Boolean): Float = if (motion == AvatarMotion.RUN && mirrored) -1f else 1f

    /**
     * 지금 동작이고 첫 텍스처가 들어갔으면 VISIBLE, 들어가기 전이면 INVISIBLE(기본 그림 숨김)이다. 지금 동작이 아니면 GONE이되, 지금 동작이
     * 아직 준비 전이고 이 동작은 준비됐으면 VISIBLE로 남겨 자세가 바뀌는 동안 몸이 비지 않게 한다(S15P31A207-335).
     */
    fun visibility(motion: AvatarMotion, current: AvatarMotion, applied: Set<AvatarMotion>): Int = when {
        motion == current && motion in applied -> View.VISIBLE
        motion == current -> View.INVISIBLE
        current !in applied && motion in applied -> View.VISIBLE
        else -> View.GONE
    }

    /**
     * 처음부터 만들 플레이어. [warmAll](트랙 칸)이면 서기·달리기를 함께 만들어 한 번에 합성해 두고, 자세가 바뀔 때 새 플레이어를 만들며
     * 생기는 빈 몸·깜빡임을 없앤다(S15P31A207-335). 그 밖(홈 등)은 지금 동작만 만든다.
     */
    fun motionsToCreate(current: AvatarMotion, warmAll: Boolean): Set<AvatarMotion> =
        if (warmAll) AvatarMotion.entries.toSet() else setOf(current)

    /** 보이지 않다가 보이게 된 때(`onReady`를 알릴 때). */
    fun becameVisible(before: Int, after: Int): Boolean = before != View.VISIBLE && after == View.VISIBLE
}

/**
 * 플레이어가 보이게 된 뒤 첫 그림이 실제로 화면 판에 그려진 때 한 번만 [notify](`onReady`)한다(S15P31A207-335 2차). 보이게 된 것만으로
 * 알리면 Rive가 첫 판을 그리기 전(에뮬레이터에서 수백 ms)에 트랙 카드가 그림자·이름표를 먼저 보인다. 판이 끝내 오지 않으면
 * [timedOut]에 알려 숨은 채 남지 않게 한다.
 */
internal class FirstFrameNotice(private val notify: () -> Unit) {
    private var isWaiting = false

    /** 지금 동작 플레이어가 보이게 됐다. 다음 판을 기다린다. */
    fun shown() {
        isWaiting = true
    }

    /** 플레이어가 판을 그렸다. 기다리던 중이면 알린다. */
    fun frameDrawn() {
        if (!isWaiting) return
        isWaiting = false
        notify()
    }

    /** 기다림이 끝났다(판이 오지 않았다). 기다리던 중이면 알린다. */
    fun timedOut() = frameDrawn()

    /** 기다리던 알림을 버린다(정체가 바뀌거나 놓였다). */
    fun cancel() {
        isWaiting = false
    }
}
