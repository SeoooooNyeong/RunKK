package com.a207.runkk.app.api.record

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** 기록 분석 조회 REST(5.7.1 DashboardController·RecordController·RankingController·CoachController). 날짜는 yyyy-MM-dd. */
internal interface RecordApi {
    @GET("dashboard")
    suspend fun dashboard(@Query("baseDate") baseDate: String): DashboardResponse

    @GET("members/{id}/runs")
    suspend fun runs(
        @Path("id") ownerId: Long,
        @Query("from") from: String,
        @Query("to") to: String
    ): RecordListResponse

    @GET("runs/{id}")
    suspend fun run(@Path("id") recordId: String): RecordDetailResponse

    /** 집계 제외 기록 삭제(FR-225). 204(본문 없음)다. 거부는 409·403 + 본문 code(D-290). */
    @DELETE("runs/{id}")
    suspend fun delete(@Path("id") recordId: String): Response<Unit>

    @GET("runs/{id}/card")
    suspend fun card(@Path("id") recordId: String): RunCardResponse

    @GET("growth")
    suspend fun growth(@Query("period") period: String): GrowthResponse

    @GET("report")
    suspend fun report(): ReportResponse

    @GET("rankings/personal")
    suspend fun personalRanking(@Query("period") period: String): RankingResponse

    @GET("rankings/crew")
    suspend fun crewRanking(@Query("period") period: String): RankingResponse

    @PUT("goals/weekly")
    suspend fun setWeeklyGoal(@Body body: WeeklyGoalRequest)

    @PUT("plans/{date}")
    suspend fun putPlan(@Path("date") date: String, @Body body: PlanRequest)

    @GET("plans/progress")
    suspend fun planProgress(@Query("from") from: String, @Query("to") to: String): PlanProgressResponse

    /** 계획이 없으면 204(본문 없음)다. */
    @GET("plans/today")
    suspend fun todayPlan(): Response<TodayPlanResponse>
}
