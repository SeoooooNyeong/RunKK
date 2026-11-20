package com.a207.runkk.app.api.record

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.FaceImageSource
import com.a207.runkk.app.api.core.ScreenCopyStore
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FaceUse
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.Growth
import com.a207.runkk.app.domain.record.PlanProgress
import com.a207.runkk.app.domain.record.Ranking
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.domain.record.RecordList
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.record.Report
import com.a207.runkk.app.domain.record.RunCard
import com.a207.runkk.app.domain.record.TodayPlan
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import retrofit2.HttpException

/**
 * 기록 분석 조회 포트를 REST로 구현한다(6.1.14 api.record).
 *
 * 화면 첫 응답 사본(S15P31A207-345 2차, [copies]): 대시보드·내 기록 목록·개인 랭킹은 받을 때마다 기기에 남기고(내 계정 칸, 랭킹은 내 순위를
 * 뺀 공용 칸에도) 화면이 먼저 그릴 수 있게 cached…로 준다. [copies]가 없으면(시험) 남기지 않는다.
 */
internal class RecordRepositoryImpl
@Inject
constructor(
    private val api: RecordApi,
    private val self: SelfMember,
    private val faces: FaceImageSource,
    private val copies: ScreenCopyStore? = null
) : RecordRepository {
    override suspend fun dashboard(baseDate: LocalDate): Outcome<Dashboard> {
        val member = self.memberId()
        val param = ServerTime.formatDate(baseDate)
        return ApiErrorMapper.call {
            api.dashboard(param).also { keep(member, DASHBOARD, param, DashboardResponse.serializer(), it) }.toDomain()
        }
    }

    override suspend fun cachedDashboard(baseDate: LocalDate): Dashboard? =
        copy(DASHBOARD, ServerTime.formatDate(baseDate), DashboardResponse.serializer())?.toDomain()

    /** ownerId가 SELF면 접근 토큰의 회원 ID로 바꿔 부른다. 본인 ID를 모르면(토큰 없음) Unauthorized. */
    override suspend fun runs(ownerId: Long, from: LocalDate, to: LocalDate): Outcome<RecordList> {
        val id = self.resolve(ownerId, RecordRepository.SELF) ?: return Outcome.Failure(AppError.Unauthorized)
        val param = ServerTime.formatDate(from) + RANGE + ServerTime.formatDate(to)
        val isSelf = ownerId == RecordRepository.SELF
        return ApiErrorMapper.call {
            val response = api.runs(id, ServerTime.formatDate(from), ServerTime.formatDate(to))
            if (isSelf) keep(id, RUNS, param, RecordListResponse.serializer(), response)
            response.toDomain()
        }
    }

    override suspend fun cachedRuns(from: LocalDate, to: LocalDate): RecordList? = copy(
        RUNS,
        ServerTime.formatDate(from) + RANGE + ServerTime.formatDate(to),
        RecordListResponse.serializer()
    )?.toDomain()

    override suspend fun run(recordId: String): Outcome<RecordDetail> = ApiErrorMapper.call {
        api.run(recordId).toDomain()
    }

    /** 204면 성공. 거부 응답은 본문 code로 Rejected(code)가 된다(ApiErrorMapper). */
    override suspend fun delete(recordId: String): Outcome<Unit> = ApiErrorMapper.call {
        val reply = api.delete(recordId)
        if (!reply.isSuccessful) throw HttpException(reply)
    }

    override suspend fun growth(period: AggregationPeriod): Outcome<Growth> =
        ApiErrorMapper.call { api.growth(period.name).toDomain() }

    override suspend fun report(): Outcome<Report> = ApiErrorMapper.call { api.report().toDomain() }

    override suspend fun personalRanking(period: AggregationPeriod): Outcome<Ranking> {
        val member = self.memberId()
        return ApiErrorMapper.call {
            api.personalRanking(period.name).also { response ->
                val key = RANKING + period.name
                keep(member, key, period.name, RankingResponse.serializer(), response)
                // 공용 칸: 다른 계정도 첫 그림을 그릴 수 있게 내 순위·다음 순위까지 거리를 뺀다.
                val shared = response.copy(mine = null, gapToNextMeters = null)
                keep(null, key, period.name, RankingResponse.serializer(), shared, isShared = true)
            }.toDomain()
        }
    }

    override suspend fun cachedPersonalRanking(period: AggregationPeriod): Ranking? = withContext(Dispatchers.IO) {
        val store = copies ?: return@withContext null
        val member = self.memberId() ?: return@withContext null
        val key = RANKING + period.name
        (store.read(member, key, RankingResponse.serializer()) ?: store.read(null, key, RankingResponse.serializer()))
            ?.takeIf { it.first == period.name }?.second?.toDomain()
    }

    override suspend fun crewRanking(period: AggregationPeriod): Outcome<Ranking> =
        ApiErrorMapper.call { api.crewRanking(period.name).toDomain() }

    /** 랭킹 줄 얼굴 그림(S15P31A207-300). 다른 러너 셀카 머리도 프로필 얼굴 공개 규칙으로 받는다. 목록 행 얼굴 원이라 256(Jira 308). */
    override suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> =
        faces.faceLayers(faceKey, FaceUse.LIST)

    override suspend fun setWeeklyGoal(distance: Distance, runs: Int): Outcome<Unit> =
        ApiErrorMapper.call { api.setWeeklyGoal(WeeklyGoalRequest(runs, distance.meters)) }

    override suspend fun putPlan(date: LocalDate, distance: Distance, pace: Pace?): Outcome<Unit> =
        ApiErrorMapper.call {
            api.putPlan(ServerTime.formatDate(date), PlanRequest(distance.meters, pace?.secondsPerKm))
        }

    /** 204면 성공. 거부 응답은 본문 code로 Rejected(code)가 된다(ApiErrorMapper). */
    override suspend fun deletePlan(date: LocalDate): Outcome<Unit> = ApiErrorMapper.call {
        val reply = api.deletePlan(ServerTime.formatDate(date))
        if (!reply.isSuccessful) throw HttpException(reply)
    }

    override suspend fun planProgress(from: LocalDate, to: LocalDate): Outcome<PlanProgress> =
        ApiErrorMapper.call { api.planProgress(ServerTime.formatDate(from), ServerTime.formatDate(to)).toDomain() }

    /** 204 또는 빈 계획이면 Success(null). 그 밖의 오류 응답은 AppError로 바꾼다. */
    override suspend fun todayPlan(): Outcome<TodayPlan?> = ApiErrorMapper.call {
        val reply = api.todayPlan()
        if (!reply.isSuccessful) throw HttpException(reply)
        reply.body()?.toDomain()
    }

    override suspend fun card(recordId: String): Outcome<RunCard> = ApiErrorMapper.call {
        api.card(recordId).toDomain()
    }

    /** 받은 응답을 [member] 칸에 남긴다. [isShared]면 공용 칸에 남긴다. 회원을 모르면(로그아웃 중 응답) 내 칸에는 남기지 않는다. */
    private suspend fun <T> keep(
        member: Long?,
        key: String,
        param: String,
        serializer: KSerializer<T>,
        value: T,
        isShared: Boolean = false
    ) {
        val store = copies ?: return
        if (member == null && !isShared) return
        withContext(Dispatchers.IO) { store.write(member.takeUnless { isShared }, key, param, serializer, value) }
    }

    /** 내 계정 칸의 [key] 사본 중 부른 값이 [param]인 것. */
    private suspend fun <T> copy(key: String, param: String, serializer: KSerializer<T>): T? =
        withContext(Dispatchers.IO) {
            val store = copies ?: return@withContext null
            val member = self.memberId() ?: return@withContext null
            store.read(member, key, serializer)?.takeIf { it.first == param }?.second
        }

    internal companion object {
        /** 화면 사본 열쇠(시작 미리 받기 출처 RecordPreloadSource도 쓴다). */
        const val DASHBOARD = "records-dashboard"
        const val RUNS = "records-runs-self"
        const val RANKING = "ranking-personal-"
        const val RANGE = ".."
    }
}
