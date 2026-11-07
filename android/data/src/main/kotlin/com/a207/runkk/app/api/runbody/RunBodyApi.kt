package com.a207.runkk.app.api.runbody

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * 런바디 결과 REST(설계 2.5 RunBodyController, 계약 runkk-work/runbody/contract.md v1.0). 결과 본문은 ai 응답을 서버가 그대로 넘긴다.
 * 오류는 404 RUNBODY_NOT_FOUND, 403 FEATURE_CLOSED, 503 RUNBODY_UNAVAILABLE(공통 오류 응답 code).
 */
internal interface RunBodyApi {
    @GET("runbody/sessions/{recordId}")
    suspend fun session(@Path("recordId") recordId: String): RunBodySessionResponse

    /** [yearMonth]는 yyyy-MM. 러닝이 없는 달도 200(sessionCount 0)이다. */
    @GET("runbody/months/{yearMonth}")
    suspend fun month(@Path("yearMonth") yearMonth: String): RunBodyMonthResponse
}
