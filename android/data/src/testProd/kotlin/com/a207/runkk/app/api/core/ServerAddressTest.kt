package com.a207.runkk.app.api.core

import kotlin.test.Test
import kotlin.test.assertEquals
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * prod 플레이버(Play 비공개 테스트 com.a207.runkk)는 베타 서버(같은 도메인 기본 443 포트)에 붙는다(infra/nginx/https, 2026-10-23 서버 분리).
 * 빌드 속성 RUNKK_PROD_API_URL·RUNKK_PROD_WS_URL로 덮어 빌드하면 이 시험은 실패한다.
 */
class ServerAddressTest {
    @Test
    fun restUsesDefaultHttpsPort() {
        val url = BuildConfig.API_BASE_URL.toHttpUrl()

        assertEquals("https", url.scheme)
        assertEquals("k15a207.p.ssafy.io", url.host)
        assertEquals(443, url.port)
        assertEquals("https://k15a207.p.ssafy.io/api/v1/", BuildConfig.API_BASE_URL)
    }

    @Test
    fun webSocketUsesDefaultHttpsPort() {
        assertEquals("wss://k15a207.p.ssafy.io/ws", BuildConfig.LIVE_SOCKET_URL)
    }
}
