package com.a207.runkk.app.api.crew

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 크루·게시판·크루런 대기실 REST(5.7.1 CrewController·CrewAdminController·CrewPostController·CrewRunController).
 * v0.11: 운영진 지정·해제, 공동 목표, 참석 응답(/vote → /attendance)을 더하고 크루 추천 조회를 지웠다(F.2.4, D-91, D-99).
 * 공지 고정·투표·게시판 글·사진·앨범 경로는 앱에서 쓰지 않아 지웠다(S15P31A207-236, 서버 API는 남아 있다).
 */
internal interface CrewApi {
    @POST("crews")
    suspend fun create(@Body body: CrewFormDto): CreatedResponse

    @PATCH("crews/{id}")
    suspend fun changeInfo(@Path("id") crewId: Long, @Body body: CrewFormDto)

    @POST("crews/{id}/invite-code")
    suspend fun reissueInviteCode(@Path("id") crewId: Long): InviteCodeResponse

    @POST("crews/{id}/leader")
    suspend fun transferLeadership(@Path("id") crewId: Long, @Body body: LeaderRequest)

    @DELETE("crews/{id}")
    suspend fun delete(@Path("id") crewId: Long)

    @GET("crews")
    suspend fun search(
        @Query("keyword") keyword: String?,
        @Query("tags") tags: List<String>,
        @Query("cursor") cursor: Long?,
        @Query("size") size: Int
    ): CrewListResponse

    @GET("crews/me")
    suspend fun myCrew(): MyCrewResponse

    @GET("crews/{id}")
    suspend fun detail(@Path("id") crewId: Long): CrewDetailResponse

    @POST("crews/join")
    suspend fun apply(@Body body: JoinRequestBody): JoinResponse

    @DELETE("join-requests/{id}")
    suspend fun cancelJoinRequest(@Path("id") requestId: Long): JoinResponse

    @POST("join-requests/{id}/approve")
    suspend fun approveJoin(@Path("id") requestId: Long)

    @POST("join-requests/{id}/reject")
    suspend fun rejectJoin(@Path("id") requestId: Long)

    @POST("crews/{id}/leave")
    suspend fun leave(@Path("id") crewId: Long)

    @POST("crews/{id}/members/{mid}/kick")
    suspend fun kick(@Path("id") crewId: Long, @Path("mid") memberId: Long)

    @PATCH("crews/{id}/me")
    suspend fun editCrewProfile(@Path("id") crewId: Long, @Body body: CrewProfileRequest)

    @GET("crews/{id}/members")
    suspend fun members(@Path("id") crewId: Long): CrewMembersResponse

    @GET("crews/{id}/join-requests")
    suspend fun pendingRequests(@Path("id") crewId: Long): PendingRequestsResponse

    @GET("crews/{id}/ranking")
    suspend fun crewRanking(@Path("id") crewId: Long, @Query("period") period: String): CrewRankingResponse

    @GET("crews/{id}/race")
    suspend fun race(@Path("id") crewId: Long, @Query("weekStart") weekStart: String?): RaceStatusResponse

    @GET("crews/{id}/posts")
    suspend fun posts(
        @Path("id") crewId: Long,
        @Query("kind") kind: String?,
        @Query("cursor") cursor: Long?,
        @Query("size") size: Int
    ): PostListResponse

    @POST("crews/{id}/posts")
    suspend fun writePost(@Path("id") crewId: Long, @Body body: PostWriteRequest): CreatedResponse

    @DELETE("posts/{id}")
    suspend fun deletePost(@Path("id") postId: Long)

    @PUT("posts/{id}/attendance")
    suspend fun answerAttendance(@Path("id") postId: Long, @Body body: AttendanceRequest): AttendanceResponse

    @PUT("crews/{id}/managers/{memberId}")
    suspend fun appointManager(@Path("id") crewId: Long, @Path("memberId") memberId: Long): ManagerResponse

    @DELETE("crews/{id}/managers/{memberId}")
    suspend fun dismissManager(@Path("id") crewId: Long, @Path("memberId") memberId: Long): ManagerResponse

    @PUT("crews/{id}/goal")
    suspend fun setGoal(@Path("id") crewId: Long, @Body body: GoalRequest): RaceStatusResponse

    @POST("crew-runs/{id}/lobby/enter")
    suspend fun enterLobby(@Path("id") postId: Long): LobbyResponse

    @POST("crew-runs/{id}/lobby/ready")
    suspend fun toggleReady(@Path("id") postId: Long): LobbyResponse

    @POST("crew-runs/{id}/lobby/leave")
    suspend fun leaveLobby(@Path("id") postId: Long): LobbyResponse

    @POST("crew-runs/{id}/pause")
    suspend fun pause(@Path("id") postId: Long, @Body body: CrewRunPauseRequest)

    @POST("crew-runs/{id}/quit")
    suspend fun quit(@Path("id") postId: Long, @Body body: CrewRunQuitRequest)

    @GET("crew-runs/{id}/result")
    suspend fun result(@Path("id") postId: Long): CrewRunResultResponse
}
