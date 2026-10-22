package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.ServerClock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request

/** 응답 Date 머리글로 서버 시각 추정(ServerClock)을 맞춘다(크루런 대기실 단추의 기기 시계 차 보정). */
class ServerDateInterceptorTest {
    private val server = MockWebServer().apply { start() }
    private val device = Instant.parse("2026-10-22T05:00:00Z")
    private val clock = ServerClock(Clock.fixed(device, ZoneOffset.UTC))
    private val client = OkHttpClient.Builder().addNetworkInterceptor(ServerDateInterceptor(clock)).build()

    @AfterTest
    fun tearDown() = server.close()

    private fun call(date: String?) {
        val response = MockResponse.Builder().code(200).body("{}")
        date?.let { response.setHeader("Date", it) }
        server.enqueue(response.build())
        client.newCall(Request.Builder().url(server.url("/api/v1/crews/me")).build()).execute().close()
    }

    @Test
    fun `기기 시계가 서버보다 3분 빠르면 서버 시각으로 맞춘다`() {
        call("Thu, 22 Oct 2026 04:57:00 GMT")
        assertEquals(Instant.parse("2026-10-22T04:57:00Z"), clock.now())
    }

    @Test
    fun `Date 머리글이 없거나 읽을 수 없으면 그대로 둔다`() {
        call(null)
        call("yesterday")
        assertEquals(device, clock.now())
    }
}
