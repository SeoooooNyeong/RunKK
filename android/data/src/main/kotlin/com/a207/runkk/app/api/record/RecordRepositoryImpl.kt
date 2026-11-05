package com.a207.runkk.app.api.record

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
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
import retrofit2.HttpException

/** 기록 분석 조회 포트를 REST로 구현한다(6.1.14 api.record). */
internal class RecordRepositoryImpl
@Inject
constructor(private val api: RecordApi, private val self: SelfMember) :
    RecordRepository {
    override suspend fun dashboard(baseDate: LocalDate): Outcome<Dashboard> =
        ApiErrorMapper.call { api.dashboard(ServerTime.formatDate(baseDate)).toDomain() }

    /** ownerId가 SELF면 접근 토큰의 회원 ID로 바꿔 부른다. 본인 ID를 모르면(토큰 없음) Unauthorized. */
    override suspend fun runs(ownerId: Long, from: LocalDate, to: LocalDate): Outcome<RecordList> {
        val id = self.resolve(ownerId, RecordRepository.SELF) ?: return Outcome.Failure(AppError.Unauthorized)
        return ApiErrorMapper.call { api.runs(id, ServerTime.formatDate(from), ServerTime.formatDate(to)).toDomain() }
    }

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

    override suspend fun personalRanking(period: AggregationPeriod): Outcome<Ranking> =
        ApiErrorMapper.call { api.personalRanking(period.name).toDomain() }

    override suspend fun crewRanking(period: AggregationPeriod): Outcome<Ranking> =
        ApiErrorMapper.call { api.crewRanking(period.name).toDomain() }

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
}
