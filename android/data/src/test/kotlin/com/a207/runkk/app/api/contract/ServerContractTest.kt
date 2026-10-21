package com.a207.runkk.app.api.contract

import com.a207.runkk.app.api.account.LegalDocumentResponse
import com.a207.runkk.app.api.account.LoginRequest
import com.a207.runkk.app.api.account.LoginResponse
import com.a207.runkk.app.api.account.LogoutRequest
import com.a207.runkk.app.api.account.MemberSettingsResponse
import com.a207.runkk.app.api.account.ProfileEditRequest
import com.a207.runkk.app.api.account.ProfileResponse
import com.a207.runkk.app.api.account.SettingChangeBody
import com.a207.runkk.app.api.account.SignUpRequest
import com.a207.runkk.app.api.account.TokenPairResponse
import com.a207.runkk.app.api.avatar.AvatarResponse
import com.a207.runkk.app.api.avatar.DefaultCharacterRequest
import com.a207.runkk.app.api.avatar.EquipRequest
import com.a207.runkk.app.api.avatar.PurchaseRequest
import com.a207.runkk.app.api.avatar.PurchaseResponse
import com.a207.runkk.app.api.avatar.ShopResponse
import com.a207.runkk.app.api.battle.BattleHistoryResponse
import com.a207.runkk.app.api.battle.BattleResultResponse
import com.a207.runkk.app.api.battle.CandidatesResponse
import com.a207.runkk.app.api.battle.CelebrityBattleRequest
import com.a207.runkk.app.api.battle.MatchAcceptanceResponse
import com.a207.runkk.app.api.battle.MatchRequest
import com.a207.runkk.app.api.battle.RecordBattleStartResponse
import com.a207.runkk.app.api.battle.SelfBattleRequest
import com.a207.runkk.app.api.crew.CreatedResponse
import com.a207.runkk.app.api.crew.CrewDetailResponse
import com.a207.runkk.app.api.crew.CrewFormDto
import com.a207.runkk.app.api.crew.CrewListResponse
import com.a207.runkk.app.api.crew.CrewMembersResponse
import com.a207.runkk.app.api.crew.CrewProfileRequest
import com.a207.runkk.app.api.crew.CrewRankingResponse
import com.a207.runkk.app.api.crew.CrewRunResultResponse
import com.a207.runkk.app.api.crew.InviteCodeResponse
import com.a207.runkk.app.api.crew.JoinRequestBody
import com.a207.runkk.app.api.crew.JoinResponse
import com.a207.runkk.app.api.crew.LeaderRequest
import com.a207.runkk.app.api.crew.LobbyResponse
import com.a207.runkk.app.api.crew.MyCrewResponse
import com.a207.runkk.app.api.crew.PendingRequestsResponse
import com.a207.runkk.app.api.crew.PostListResponse
import com.a207.runkk.app.api.crew.PostWriteRequest
import com.a207.runkk.app.api.crew.RaceStatusResponse
import com.a207.runkk.app.api.crew.RecommendationsResponse
import com.a207.runkk.app.api.crew.VoteRequest
import com.a207.runkk.app.api.notification.InboxResponse
import com.a207.runkk.app.api.notification.PushTokenRequest
import com.a207.runkk.app.api.notification.ROUTES
import com.a207.runkk.app.api.notification.TITLES
import com.a207.runkk.app.api.record.DashboardResponse
import com.a207.runkk.app.api.record.GrowthResponse
import com.a207.runkk.app.api.record.PlanProgressResponse
import com.a207.runkk.app.api.record.PlanRequest
import com.a207.runkk.app.api.record.RankingResponse
import com.a207.runkk.app.api.record.RecordDetailResponse
import com.a207.runkk.app.api.record.RecordListResponse
import com.a207.runkk.app.api.record.ReportResponse
import com.a207.runkk.app.api.record.RunCardResponse
import com.a207.runkk.app.api.record.TodayPlanResponse
import com.a207.runkk.app.api.record.WeeklyGoalRequest
import com.a207.runkk.app.api.run.DisposalOrderResponse
import com.a207.runkk.app.api.run.LateHeartRatesRequest
import com.a207.runkk.app.api.run.RunUploadRequest
import com.a207.runkk.app.api.run.UploadResponse
import com.a207.runkk.app.api.runimal.RunimalCardResponse
import com.a207.runkk.app.api.runimal.RunimalStatusResponse
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.model.SignalState
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.ProviderType
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.VoteAnswer
import com.a207.runkk.app.domain.sync.DisposalOrder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 서버 계약 자료(android/contract, 서버에서 뽑아 고정)를 읽는다. 경로는 Gradle 시험 작업이 넘긴다(runkk.contract.dir). */
private val contractDir = File(requireNotNull(System.getProperty("runkk.contract.dir")) { "runkk.contract.dir 속성이 없다" })

private fun enumNames(name: String): Set<String> =
    File(contractDir, "enums/$name.txt").readLines().filter { it.isNotBlank() }.map { it.substringBefore(' ') }.toSet()

private fun names(entries: Collection<Enum<*>>): Set<String> = entries.map { it.name }.toSet()

/**
 * 앱 :data가 서버와 주고받는 값을 서버 계약 자료와 대조한다(재발 방지). 대상: (1) 앱 enum 이름이 서버 enum 이름 목록 안에 있는가,
 * (2) REST 요청·응답 DTO의 항목 이름이 서버 요청·응답 record와 맞는가(요청: 앱이 보내는 항목이 모두 서버에 있고 서버 필수 항목을 다 보내는가,
 * 응답: 앱이 필수로 읽는 항목이 모두 서버가 주는 항목인가). 서버 쪽 값이 바뀌면 `python android/contract/sync_from_server.py`로
 * 자료를 갱신하고 이 시험이 알려 주는 앱 쪽을 고친다. 실시간 메시지는 data.live.RealtimeContractTest가 맡는다.
 */
class ServerContractTest {
    // ---------- enum ----------

    @Test
    fun `앱이 서버로 보내거나 서버에서 읽는 enum 이름은 서버 enum 안에 있다`() {
        val pairs: Map<String, Set<String>> = mapOf(
            "ProviderType" to names(ProviderType.entries),
            "Gender" to names(Gender.entries),
            "ItemSlot" to names(ItemSlot.entries),
            "CrewVisibility" to names(CrewVisibility.entries),
            "JoinPolicy" to names(JoinPolicy.entries),
            "VoteAnswer" to names(VoteAnswer.entries),
            "PostKind" to names(PostList.Kind.entries),
            "AggregationPeriod" to names(CrewRanking.Period.entries),
            "CrewRole" to names(CrewRole.entries),
            "CrewRunStatus" to names(CrewRunStatus.entries),
            "BattleType" to names(BattleHistory.Type.entries),
            "CardKind" to names(CardKind.entries),
            "RunMode" to names(RunMode.entries),
            "SignalState" to names(SignalState.entries),
            "DisposalKind" to names(DisposalOrder.Kind.entries)
        )
        pairs.forEach { (server, app) ->
            val unknown = app - enumNames(server)
            assertTrue(unknown.isEmpty(), "서버 $server 에 없는 앱 값 $unknown")
        }
    }

    @Test
    fun `서버와 이름이 한 쌍으로 맞아야 하는 enum은 서버와 같다`() {
        assertEquals(enumNames("CardKind"), names(CardKind.entries))
        assertEquals(enumNames("RunMode"), names(RunMode.entries))
        assertEquals(enumNames("SignalState"), names(SignalState.entries))
        assertEquals(enumNames("ItemSlot"), names(ItemSlot.entries))
        assertEquals(enumNames("CrewVisibility"), names(CrewVisibility.entries))
        assertEquals(enumNames("JoinPolicy"), names(JoinPolicy.entries))
        assertEquals(enumNames("VoteAnswer"), names(VoteAnswer.entries))
        assertEquals(enumNames("PostKind"), names(PostList.Kind.entries))
        assertEquals(enumNames("AggregationPeriod"), names(CrewRanking.Period.entries))
        assertEquals(enumNames("CrewRole"), names(CrewRole.entries))
        assertEquals(enumNames("CrewRunStatus"), names(CrewRunStatus.entries))
        assertEquals(enumNames("BattleType"), names(BattleHistory.Type.entries))
        assertEquals(enumNames("DisposalKind"), names(DisposalOrder.Kind.entries))
        assertEquals(enumNames("ProviderType"), names(ProviderType.entries))
        assertEquals(enumNames("Gender"), names(Gender.entries))
    }

    @Test
    fun `알림함 제목과 이동 경로는 서버 알림 종류와 화면 종류를 모두 다룬다`() {
        assertEquals(enumNames("NotificationType"), TITLES.keys)
        assertEquals(enumNames("Screen"), ROUTES.keys)
    }

    // ---------- DTO ----------

    private val records: JsonObject =
        Json.parseToJsonElement(File(contractDir, "dto-records.json").readText()).jsonObject

    private enum class Direction { REQUEST, RESPONSE }

    private fun check(app: KSerializer<*>, serverKey: String, direction: Direction, problems: MutableList<String>) =
        checkDescriptor(
            app.descriptor,
            serverKey,
            direction,
            problems,
            app.descriptor.serialName.substringAfterLast('.')
        )

    private fun checkDescriptor(
        descriptor: SerialDescriptor,
        serverKey: String,
        direction: Direction,
        problems: MutableList<String>,
        path: String
    ) {
        val record = records[serverKey]?.jsonObject
        if (record == null) {
            problems += "$path: 서버 record $serverKey 가 계약 자료에 없다"
            return
        }
        val components: List<JsonObject> = record.getValue("components").jsonArray.map { it.jsonObject }
        val byName = mutableMapOf<String, JsonObject>()
        components.forEach { component ->
            byName[component.getValue("name").jsonPrimitive.content] = component
            (component["aliases"] as? JsonArray)?.forEach { byName[it.jsonPrimitive.content] = component }
        }
        val appNames = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()
        for (i in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(i)
            val component = byName[name]
            if (component == null) {
                if (direction == Direction.REQUEST || !descriptor.isElementOptional(i)) {
                    problems += "$path.$name: 서버 $serverKey 에 없는 항목(${direction.name})"
                }
                continue
            }
            val child = descriptor.getElementDescriptor(i)
            val ref = component["ref"]?.jsonPrimitive?.content
            when (component["kind"]?.jsonPrimitive?.content) {
                "record" ->
                    if (child.kind == StructureKind.CLASS && ref != null) {
                        checkDescriptor(child, ref, direction, problems, "$path.$name")
                    }

                "list" -> {
                    val element = if (child.kind == StructureKind.LIST) child.getElementDescriptor(0) else null
                    if (element?.kind == StructureKind.CLASS && ref != null) {
                        checkDescriptor(element, ref, direction, problems, "$path.$name[]")
                    }
                }
            }
        }
        if (direction == Direction.REQUEST) {
            components.filter { it["required"]?.jsonPrimitive?.boolean == true }.forEach { component ->
                val name = component.getValue("name").jsonPrimitive.content
                val aliases = (component["aliases"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
                if (name !in appNames && aliases.none { it in appNames }) {
                    problems += "$path: 서버 필수 항목 $name 을 앱이 보내지 않는다"
                }
            }
        }
    }

    @Test
    fun `요청 DTO 항목은 서버 요청 record와 맞다`() {
        val problems = mutableListOf<String>()

        fun request(app: KSerializer<*>, server: String) = check(app, server, Direction.REQUEST, problems)
        request(LoginRequest.serializer(), "AuthController.LoginRequest")
        request(LogoutRequest.serializer(), "AuthController.RefreshRequest")
        request(SignUpRequest.serializer(), "SignUpController.SignUpRequest")
        request(ProfileEditRequest.serializer(), "MeController.ProfileEditRequest")
        request(SettingChangeBody.serializer(), "MeController.SettingChangeRequest")
        request(RunUploadRequest.serializer(), "RunController.RunUploadRequest")
        request(LateHeartRatesRequest.serializer(), "RunController.HeartRatesRequest")
        request(MatchRequest.serializer(), "BattleDtos.DistanceRequest")
        request(SelfBattleRequest.serializer(), "BattleDtos.DistanceRequest")
        request(CelebrityBattleRequest.serializer(), "BattleDtos.CelebrityBattleRequest")
        request(CrewFormDto.serializer(), "CrewDtos.CrewFormRequest")
        request(LeaderRequest.serializer(), "CrewDtos.LeaderRequest")
        request(JoinRequestBody.serializer(), "CrewDtos.JoinRequestBody")
        request(CrewProfileRequest.serializer(), "CrewDtos.CrewProfileRequest")
        request(PostWriteRequest.serializer(), "CrewDtos.PostWriteRequest")
        request(VoteRequest.serializer(), "CrewDtos.VoteRequest")
        request(DefaultCharacterRequest.serializer(), "AvatarDtos.DefaultCharacterRequest")
        request(EquipRequest.serializer(), "AvatarDtos.EquipRequest")
        request(PurchaseRequest.serializer(), "AvatarDtos.PurchaseRequest")
        request(PushTokenRequest.serializer(), "NotificationController.PushTokenRequest")
        request(WeeklyGoalRequest.serializer(), "CoachController.WeeklyGoalRequest")
        request(PlanRequest.serializer(), "CoachController.PlanRequest")
        assertTrue(problems.isEmpty(), problems.joinToString("\n", prefix = "\n"))
    }

    @Test
    fun `응답 DTO의 필수 항목은 서버 응답 record가 준다`() {
        val problems = mutableListOf<String>()

        fun response(app: KSerializer<*>, server: String) = check(app, server, Direction.RESPONSE, problems)
        response(LoginResponse.serializer(), "AuthController.LoginResponse")
        response(TokenPairResponse.serializer(), "AuthController.TokenResponse")
        response(TokenPairResponse.serializer(), "SignUpController.SignUpResponse")
        response(MemberSettingsResponse.serializer(), "MemberSettingsView")
        response(LegalDocumentResponse.serializer(), "LegalDocument")
        response(AvatarResponse.serializer(), "AvatarDtos.AvatarResponse")
        response(ShopResponse.serializer(), "AvatarDtos.ShopResponse")
        response(PurchaseResponse.serializer(), "AvatarDtos.PurchaseResponse")
        response(MatchAcceptanceResponse.serializer(), "BattleDtos.MatchAcceptanceResponse")
        response(CandidatesResponse.serializer(), "BattleDtos.CandidatesResponse")
        response(RecordBattleStartResponse.serializer(), "BattleDtos.RecordBattleStartResponse")
        response(BattleResultResponse.serializer(), "BattleDtos.BattleResultResponse")
        response(BattleHistoryResponse.serializer(), "BattleDtos.BattleHistoryResponse")
        response(CrewListResponse.serializer(), "CrewDtos.CrewListResponse")
        response(RecommendationsResponse.serializer(), "CrewDtos.RecommendationsResponse")
        response(CrewMembersResponse.serializer(), "CrewMembersAssembler.CrewMembersResponse")
        response(PendingRequestsResponse.serializer(), "CrewMembersAssembler.PendingRequestsResponse")
        response(CrewRankingResponse.serializer(), "CrewController.RankingResponse")
        response(RaceStatusResponse.serializer(), "CrewDtos.RaceStatusResponse")
        response(PostListResponse.serializer(), "CrewDtos.PostListResponse")
        response(LobbyResponse.serializer(), "CrewDtos.LobbyResponse")
        response(CrewRunResultResponse.serializer(), "CrewDtos.CrewRunResultResponse")
        response(CreatedResponse.serializer(), "CrewDtos.CreatedResponse")
        response(CreatedResponse.serializer(), "CrewDtos.PostResponse")
        response(InviteCodeResponse.serializer(), "CrewDtos.InviteCodeResponse")
        response(JoinResponse.serializer(), "CrewDtos.JoinResponse")
        response(CrewDetailResponse.serializer(), "CrewDtos.CrewDetailResponse")
        response(MyCrewResponse.serializer(), "CrewDtos.MyCrewResponse")
        response(InboxResponse.serializer(), "InboxView")
        response(UploadResponse.serializer(), "RunController.UploadResponse")
        response(DisposalOrderResponse.serializer(), "DisposalOrderView")
        response(RunimalStatusResponse.serializer(), "RunimalController.StatusResponse")
        response(RunimalCardResponse.serializer(), "RunimalController.CardResponse")
        response(ProfileResponse.serializer(), "MeController.ProfileResponse")
        response(DashboardResponse.serializer(), "RecordDtos.DashboardResponse")
        response(RecordListResponse.serializer(), "RecordDtos.RecordListResponse")
        response(RecordDetailResponse.serializer(), "RecordDtos.RecordDetailResponse")
        response(GrowthResponse.serializer(), "RecordDtos.GrowthResponse")
        response(ReportResponse.serializer(), "RecordDtos.ReportResponse")
        response(RankingResponse.serializer(), "RecordDtos.RankingResponse")
        response(PlanProgressResponse.serializer(), "RecordDtos.PlanProgressResponse")
        response(TodayPlanResponse.serializer(), "RecordDtos.TodayPlanResponse")
        response(RunCardResponse.serializer(), "RecordDtos.RunCardResponse")
        assertTrue(problems.isEmpty(), problems.joinToString("\n", prefix = "\n"))
    }
}
