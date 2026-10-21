package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.Outcome
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** api.<기능> 시험용: MockWebServer에 운영과 같은 Json·변환기로 Retrofit을 붙인다. */
class ApiTestServer : AutoCloseable {
    val server = MockWebServer().apply { start() }
    val json = ApiCoreBindings.json()
    val retrofit: Retrofit =
        Retrofit
            .Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    fun reply(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse
                .Builder()
                .code(code)
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build()
        )
    }

    fun replyEmpty(code: Int = 204) {
        server.enqueue(MockResponse.Builder().code(code).build())
    }

    fun take(): RecordedRequest = server.takeRequest()

    override fun close() {
        server.close()
    }
}

/** Outcome.Success의 값을 꺼낸다. 실패면 시험이 실패한다. */
fun <T> Outcome<T>.value(): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> throw AssertionError("성공이어야 하는데 실패: $error")
}
