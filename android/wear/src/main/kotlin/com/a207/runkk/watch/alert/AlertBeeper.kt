package com.a207.runkk.watch.alert

/** 비프음 포트. 구현 [ToneGeneratorBeeper]는 ToneGenerator를 감싼다. 기다리지 않고 바로 돌아온다. */
interface AlertBeeper {
    fun beep(durationMillis: Int)

    /** 재생을 멈출 때 부른다. 잡고 있던 오디오 자원을 놓는다. 다음 [beep]이 다시 연다. */
    fun release()
}
