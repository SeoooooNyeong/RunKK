package com.a207.runkk.app.core.avatar

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
