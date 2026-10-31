package com.a207.runkk.app.api.battle

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** 대결 REST(5.7.1 BattleController). 실시간 메시지는 data.live가 받는다. */
internal interface BattleApi {
    @GET("battles/match")
    suspend fun matchStatus(): MatchAcceptanceResponse

    @POST("battles/match")
    suspend fun requestMatch(@Body body: MatchRequest): MatchAcceptanceResponse

    @DELETE("battles/match")
    suspend fun cancel(): MatchAcceptanceResponse

    @GET("battles/self/candidates")
    suspend fun selfCandidates(): CandidatesResponse

    @GET("battles/celebrity/candidates")
    suspend fun celebrityCandidates(@Query("distanceMeters") distanceMeters: Long?): CandidatesResponse

    @POST("battles/self")
    suspend fun startSelfBattle(@Body body: SelfBattleRequest): RecordBattleStartResponse

    @POST("battles/celebrity")
    suspend fun startCelebrityBattle(@Body body: CelebrityBattleRequest): RecordBattleStartResponse

    @POST("battles/{id}/started")
    suspend fun markStarted(@Path("id") battleId: Long)

    @POST("battles/{id}/forfeit")
    suspend fun forfeit(@Path("id") battleId: Long)

    /** 셀프런·셀럽런 취소(204). */
    @POST("battles/{id}/cancel")
    suspend fun cancel(@Path("id") battleId: Long)

    /** 진행 중인 대결이 없으면 204(본문 없음)다. */
    @GET("battles/active")
    suspend fun active(): Response<ActiveBattleResponse>

    @GET("battles/{id}/result")
    suspend fun result(@Path("id") battleId: Long): BattleResultResponse

    /** 친구 대결 초대 만들기(201). 본문은 랜덤 매칭과 같은 {distanceMeters}(서버 BattleDtos.DistanceRequest)다. */
    @POST("battles/friend-invites")
    suspend fun createFriendInvite(@Body body: MatchRequest): InviteResponse

    /** 열린 초대가 없으면 204(본문 없음)다. */
    @GET("battles/friend-invites/mine")
    suspend fun myFriendInvite(): Response<InviteResponse>

    @DELETE("battles/friend-invites/{inviteId}")
    suspend fun cancelFriendInvite(@Path("inviteId") inviteId: Long): Response<Unit>

    @GET("battles/friend-invites/by-token/{token}")
    suspend fun friendInvite(@Path("token") token: String): InviteViewResponse

    @POST("battles/friend-invites/by-token/{token}/accept")
    suspend fun acceptFriendInvite(@Path("token") token: String): AcceptResponse

    @POST("battles/friend-invites/by-token/{token}/decline")
    suspend fun declineFriendInvite(@Path("token") token: String): Response<Unit>

    @GET("battles/history")
    suspend fun history(
        @Query("type") type: String?,
        @Query("cursor") cursor: Long?,
        @Query("size") size: Int
    ): BattleHistoryResponse
}
