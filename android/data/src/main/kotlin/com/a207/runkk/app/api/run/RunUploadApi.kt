package com.a207.runkk.app.api.run

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 기록 전송 REST(POST /runs) 하나만 둔다(Jira 327). 업로드 전용 클라이언트(요청 본문 gzip)로 만들어지므로 서버 압축 해제 허용 목록
 * (POST /api/v1/runs)에 있는 요청만 여기에 둔다. 늦은 심박·파기 지시는 [RunApi](일반 클라이언트)다.
 */
internal interface RunUploadApi {
    @POST("runs")
    suspend fun upload(@Body body: RunUploadRequest): UploadResponse
}
