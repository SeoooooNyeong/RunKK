package com.a207.runkk.app.api.crew

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * 크루·게시판·크루런 대기실 REST(5.7.1 CrewController·CrewAdminController·CrewPostController·CrewRunController).
 * v0.11: 운영진 지정·해제, 공지 고정, 투표 결과·응답, 게시판 글(multipart), 앨범, 공동 목표, 참석 응답(/vote → /attendance)을 더하고
 * 크루 추천 조회를 지웠다(F.2.4, D-91, D-99).
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

    @PUT("posts/{id}/pin")
    suspend fun pin(@Path("id") postId: Long, @Body body: PinRequest): PinResponse

    @GET("posts/{id}/poll")
    suspend fun pollResult(@Path("id") postId: Long): PollResultResponse

    @PUT("posts/{id}/poll-response")
    suspend fun respondPoll(@Path("id") postId: Long, @Body body: PollAnswerRequest): PollResultResponse

    /** 게시판 글: 본문 파트 body와 사진 파트 photos(0~5장, 장당 1MB 이하 JPEG, 요청 전체 6MB 이하, D-100). */
    @Multipart
    @POST("crews/{id}/board-posts")
    suspend fun writeBoard(
        @Path("id") crewId: Long,
        @Part("body") body: RequestBody,
        @Part photos: List<MultipartBody.Part>
    ): CreatedResponse

    /** 게시판 사진 한 장(인증 필요, D-100). 본문을 한꺼번에 읽지 않게 스트리밍으로 받는다. */
    @Streaming
    @GET("crew-photos/{id}")
    suspend fun photo(@Path("id") photoId: Long): ResponseBody

    @GET("crews/{id}/album")
    suspend fun album(@Path("id") crewId: Long, @Query("cursor") cursor: Long?, @Query("size") size: Int): AlbumResponse

    @PUT("crews/{id}/goal")
    suspend fun setGoal(@Path("id") crewId: Long, @Body body: GoalRequest): RaceStatusResponse

    @POST("crew-runs/{id}/lobby/enter")
    suspend fun enterLobby(@Path("id") postId: Long): LobbyResponse

    @POST("crew-runs/{id}/lobby/ready")
    suspend fun toggleReady(@Path("id") postId: Long): LobbyResponse

    @POST("crew-runs/{id}/lobby/leave")
    suspend fun leaveLobby(@Path("id") postId: Long): LobbyResponse

    @POST("crew-runs/{id}/quit")
    suspend fun quit(@Path("id") postId: Long)

    @GET("crew-runs/{id}/result")
    suspend fun result(@Path("id") postId: Long): CrewRunResultResponse
}
