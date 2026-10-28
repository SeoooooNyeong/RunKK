package com.a207.runkk.app.api.report

import retrofit2.http.Body
import retrofit2.http.POST

/** 회원의 콘텐츠 신고 REST(5.7.1 ReportController, F.2.2 신고, FR-214, FR-215, D-104). */
internal interface ReportApi {
    /** 201 ReportResponse. 거부는 409 REPORT_TARGET_NOT_FOUND·NOT_CREW_MEMBER·TARGET_INVALID·ALREADY_REPORTED, 400 BAD_REQUEST. */
    @POST("reports")
    suspend fun submit(@Body body: ReportRequest): ReportResponse
}
