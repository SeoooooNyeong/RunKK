package com.a207.runkk.app.api.core

import kotlin.test.Test
import kotlin.test.assertEquals
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * develop 플레이버는 develop 서버(같은 도메인 8443 포트)에 붙는다. 443은 베타(prod) 서버라 develop 앱이 베타 자료를 건드리면 안 된다
 * (infra/nginx/https, 2026-10-23 서버 분리). 빌드 속성 RUNKK_DEVELOP_API_URL·RUNKK_DEVELOP_WS_URL로 덮어 빌드하면 이 시험은 실패한다.
 */
class ServerAddressTest {
    @Test
    fun restUsesDevelopPort() {
        val url = BuildConfig.API_BASE_URL.toHttpUrl()

        assertEquals("https", url.scheme)
        assertEquals("k15a207.p.ssafy.io", url.host)
        assertEquals(8443, url.port)
        assertEquals("/api/v1/", url.encodedPath)
    }

    @Test
    fun webSocketUsesDevelopPort() {
        assertEquals("wss://k15a207.p.ssafy.io:8443/ws", BuildConfig.LIVE_SOCKET_URL)
    }
}
