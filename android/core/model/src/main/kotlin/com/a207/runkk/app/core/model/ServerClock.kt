package com.a207.runkk.app.core.model

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * 서버 시각 추정. 서버 응답의 Date 머리글(api.core.ServerDateInterceptor가 넘긴다)과 기기 시계의 차를 기억해 기기 시계에 더한다.
 * 서버가 시각으로 거부하는 일(크루런 대기실 개방 30분 전 등)을 화면이 미리 가릴 때 쓴다. 응답을 받기 전에는 기기 시계 그대로다.
 * Date 머리글은 초 단위(내림)라 지금 차와 1초 미만으로 다르면 버린다.
 * api.core(채우는 쪽)와 ui.*(읽는 쪽)가 함께 쓰므로 5.1.2 표상 둘 다 의존할 수 있는 core.model에 두고, 그래프에는
 * api.core.ApiCoreBindings가 하나만 내준다.
 */
class ServerClock(private val device: Clock) {
    @Volatile
    private var offset: Duration = Duration.ZERO

    fun now(): Instant = device.instant().plus(offset)

    fun onServerDate(serverAt: Instant) {
        val measured = Duration.between(device.instant(), serverAt)
        if (measured.minus(offset).abs() >= RESOLUTION) offset = measured
    }

    private companion object {
        val RESOLUTION: Duration = Duration.ofSeconds(1)
    }
}
