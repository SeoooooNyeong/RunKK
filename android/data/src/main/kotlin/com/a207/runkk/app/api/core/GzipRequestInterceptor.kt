package com.a207.runkk.app.api.core

import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.GzipSink
import okio.buffer

/**
 * 업로드 전용 클라이언트의 요청 본문을 gzip으로 보낸다(Jira 327). 받는 요청을 모두 압축한다(분기 없음): 이 인터셉터는 서버 압축 해제
 * 허용 목록(POST /api/v1/runs)에 있는 요청만 만드는 업로드 전용 클라이언트에만 붙인다(ApiCoreBindings.uploadRetrofit).
 *
 * - 본문을 메모리에서 압축해 길이를 아는 본문으로 보내고 `Content-Encoding: gzip`을 붙인다. 본문이 없거나 이미 인코딩한 요청은 그대로다.
 * - 옛 서버(압축 해제 필터 없음): 400·413·415면 그 요청을 무압축으로 한 번 다시 보낸다. 415면 이 실행 동안 압축을 끄고([disable]),
 *   400·413은 진짜 잘못된 요청이거나 새 서버의 해제 상한 초과(413 PAYLOAD_TOO_LARGE)일 수 있어 무압축 재전송이 성공했을 때만 끈다.
 * 여러 스레드가 함께 써도 된다.
 */
class GzipRequestInterceptor : Interceptor {
    private val enabled = AtomicBoolean(true)

    /** 이 실행 동안 압축하지 않는다. */
    fun disable() = enabled.set(false)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body
        if (!enabled.get() || body == null || request.header(CONTENT_ENCODING) != null) return chain.proceed(request)
        val compressed = request.newBuilder()
            .header(CONTENT_ENCODING, GZIP)
            .method(request.method, gzipped(body))
            .build()
        val response = chain.proceed(compressed)
        if (response.code !in LEGACY_REFUSALS) return response
        response.close()
        val plain = chain.proceed(request)
        if (response.code == HTTP_UNSUPPORTED_MEDIA || plain.isSuccessful) disable()
        return plain
    }

    /** 본문을 메모리에서 gzip으로 묶는다(길이를 아는 본문). */
    private fun gzipped(body: RequestBody): RequestBody {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use(body::writeTo)
        val bytes = buffer.readByteString()
        return object : RequestBody() {
            override fun contentType(): MediaType? = body.contentType()

            override fun contentLength(): Long = bytes.size.toLong()

            override fun writeTo(sink: BufferedSink) {
                sink.write(bytes)
            }
        }
    }

    private companion object {
        const val CONTENT_ENCODING = "Content-Encoding"
        const val GZIP = "gzip"
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNSUPPORTED_MEDIA = 415

        /** 필터 없는 옛 서버가 gzip 본문에 줄 수 있는 상태(본문 너무 큼·형식 모름·해독 실패). */
        val LEGACY_REFUSALS = setOf(HTTP_BAD_REQUEST, 413, HTTP_UNSUPPORTED_MEDIA)
    }
}
