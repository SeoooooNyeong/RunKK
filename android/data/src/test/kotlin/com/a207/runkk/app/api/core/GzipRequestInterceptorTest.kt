package com.a207.runkk.app.api.core

import java.util.zip.GZIPInputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 업로드 요청 압축(Jira 327): 업로드 전용 클라이언트의 인터셉터는 받는 요청 본문을 모두 gzip으로 보낸다. 400·413·415면 그 요청을
 * 무압축으로 한 번 다시 보낸다. 415(필터 없는 옛 서버)면 그 실행 동안 압축을 끄고, 400·413은 무압축 재전송이 성공할 때만 끈다.
 */
class GzipRequestInterceptorTest {
    private val server = MockWebServer().apply { start() }
    private val gzip = GzipRequestInterceptor()
    private val client = OkHttpClient.Builder().addInterceptor(gzip).build()
    private val json =
        """{"recordId":"r-1","points":[""" + (1..200).joinToString(",") { """{"lat":37.5,"lng":127.0}""" } + "]}"

    @AfterTest
    fun tearDown() = server.close()

    @Test
    fun `본문을 gzip으로 압축하고 Content-Encoding 머리글을 붙인다`() {
        server.enqueue(MockResponse.Builder().code(200).build())
        post()
        val sent = server.takeRequest()
        assertEquals("gzip", sent.headers["Content-Encoding"])
        assertEquals(json, inflate(sent))
        assertEquals(sent.bodySize, sent.headers["Content-Length"]?.toLong(), "길이를 아는 본문으로 보낸다")
        assert(sent.bodySize < json.length / 2) { "압축됨: ${sent.bodySize} / ${json.length}" }
    }

    @Test
    fun `415면 무압축으로 한 번 다시 보내고 그 뒤로는 압축하지 않는다`() {
        server.enqueue(MockResponse.Builder().code(415).build())
        server.enqueue(MockResponse.Builder().code(200).build())
        server.enqueue(MockResponse.Builder().code(200).build())

        assertEquals(200, post())
        val first = server.takeRequest()
        val retried = server.takeRequest()
        assertEquals("gzip", first.headers["Content-Encoding"])
        assertNull(retried.headers["Content-Encoding"])
        assertEquals(json, retried.body!!.utf8())

        post()
        assertNull(server.takeRequest().headers["Content-Encoding"], "이번 실행 동안 압축을 끈다")
    }

    @Test
    fun `413은 무압축으로 다시 보내고, 무압축도 413 PAYLOAD_TOO_LARGE면(해제 상한 초과) 압축을 유지한다`() {
        server.enqueue(MockResponse.Builder().code(413).body("""{"code":"PAYLOAD_TOO_LARGE"}""").build())
        server.enqueue(MockResponse.Builder().code(413).body("""{"code":"PAYLOAD_TOO_LARGE"}""").build())
        assertEquals(413, post())
        server.takeRequest()
        server.takeRequest()

        server.enqueue(MockResponse.Builder().code(413).build())
        server.enqueue(MockResponse.Builder().code(201).build())
        assertEquals(201, post(), "옛 서버(무압축 성공)")
        assertEquals("gzip", server.takeRequest().headers["Content-Encoding"], "압축 유지 확인")
        server.takeRequest()
        server.enqueue(MockResponse.Builder().code(200).build())
        post()
        assertNull(server.takeRequest().headers["Content-Encoding"], "무압축이 성공했으니 끈다")
    }

    @Test
    fun `400은 무압축 재전송이 성공하면 압축을 끄고, 무압축도 400이면(진짜 잘못된 요청) 압축을 유지한다`() {
        server.enqueue(MockResponse.Builder().code(400).build())
        server.enqueue(MockResponse.Builder().code(400).body("bad").build())
        assertEquals(400, post())
        server.takeRequest()
        server.takeRequest()

        server.enqueue(MockResponse.Builder().code(200).build())
        post()
        assertEquals("gzip", server.takeRequest().headers["Content-Encoding"], "진짜 400이면 압축 유지")

        server.enqueue(MockResponse.Builder().code(400).build())
        server.enqueue(MockResponse.Builder().code(200).build())
        assertEquals(200, post())
        server.takeRequest()
        server.takeRequest()
        server.enqueue(MockResponse.Builder().code(200).build())
        post()
        assertNull(server.takeRequest().headers["Content-Encoding"], "옛 서버(무압축 성공)면 끈다")
    }

    @Test
    fun `본문 없는 요청은 그대로 보낸다`() {
        server.enqueue(MockResponse.Builder().code(200).build())
        client.newCall(Request.Builder().url(server.url("/api/v1/runs")).build()).execute().close()
        assertNull(server.takeRequest().headers["Content-Encoding"])
    }

    private fun post(): Int = client.newCall(
        Request.Builder().url(server.url("/api/v1/runs"))
            .post(json.toRequestBody("application/json".toMediaType())).build()
    ).execute().use { it.code }

    private fun inflate(request: RecordedRequest): String =
        GZIPInputStream(request.body!!.toByteArray().inputStream()).readBytes().decodeToString()
}
