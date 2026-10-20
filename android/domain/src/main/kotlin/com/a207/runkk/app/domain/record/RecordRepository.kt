package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import java.time.LocalDate

/**
 * 기록 분석 조회 포트(5.1.2): 대시보드·기록·성장·리포트·랭킹·코치·오늘 계획·카드 값. 구현 api.record.
 * 포트 하나를 부르는 조회라 ViewModel이 직접 부른다(5.1.2 설계 규칙). 화면 모델은 이 패키지의 도메인 모델이다.
 */
interface RecordRepository {
    suspend fun dashboard(baseDate: LocalDate): Outcome<Dashboard>

    suspend fun runs(ownerId: Long, from: LocalDate, to: LocalDate): Outcome<RecordList>

    suspend fun run(recordId: String): Outcome<RecordDetail>

    suspend fun growth(period: AggregationPeriod): Outcome<Growth>

    suspend fun report(): Outcome<Report>

    suspend fun personalRanking(period: AggregationPeriod): Outcome<Ranking>

    suspend fun crewRanking(period: AggregationPeriod): Outcome<Ranking>

    suspend fun setWeeklyGoal(distance: Distance?, runs: Int?): Outcome<Unit>

    suspend fun putPlan(date: LocalDate, distance: Distance, pace: Pace?): Outcome<Unit>

    suspend fun planProgress(from: LocalDate, to: LocalDate): Outcome<PlanProgress>

    /** 오늘 계획(FR-042, FR-048). 계획이 없으면 Success(null). ui.run이 domain.run.PlanSnapshot으로 옮긴다. */
    suspend fun todayPlan(): Outcome<TodayPlan?>

    /** 공유 카드 값(GET /runs/{id}/card, FR-068). */
    suspend fun card(recordId: String): Outcome<RunCard>

    companion object {
        /** runs(ownerId, …)에 넘기면 로그인한 본인의 기록. 구현이 본인 회원 ID로 바꿔 부른다. */
        const val SELF: Long = 0L
    }
}
