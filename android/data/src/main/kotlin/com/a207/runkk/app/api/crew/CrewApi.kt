package com.a207.runkk.app.api.crew

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** 크루·게시판·크루런 대기실 REST(5.7.1 CrewController·CrewAdminController·CrewPostController·CrewRunController). */
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
    suspend fun search(@Query("keyword") keyword: String?, @Query("tags") tags: List<String>): CrewListResponse

    @GET("crews/recommendations")
    suspend fun recommend(): CrewListResponse

    @POST("crews/join")
    suspend fun apply(@Body body: JoinRequestBody)

    @DELETE("join-requests/{id}")
    suspend fun cancelJoinRequest(@Path("id") requestId: Long)

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
    suspend fun posts(@Path("id") crewId: Long, @Query("kind") kind: String?): PostListResponse

    @POST("crews/{id}/posts")
    suspend fun writePost(@Path("id") crewId: Long, @Body body: PostWriteRequest): CreatedResponse

    @DELETE("posts/{id}")
    suspend fun deletePost(@Path("id") postId: Long)

    @PUT("posts/{id}/vote")
    suspend fun vote(@Path("id") postId: Long, @Body body: VoteRequest)

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
