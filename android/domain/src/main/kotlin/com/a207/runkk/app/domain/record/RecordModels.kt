package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// 기록 분석 화면용 도메인 모델. 이름은 서버 analysis 조회 결과(…View)에서 View를 뺀 것이고(RunimalStatusView → RunimalStatus와 같은 규칙),
// 필드 이름·형식은 10/21 API 계약으로 확정해 api.record 매퍼가 맞춘다.

/** 집계 기간(서버 common.AggregationPeriod). */
enum class AggregationPeriod {
    WEEKLY,
    MONTHLY
}

/** 기록 목록·상세 공용 요약 한 줄. valid가 거짓이면 "집계 제외"로 보인다. */
data class RunSummary(
    val recordId: String,
    val startedAt: Instant,
    val distance: Distance,
    val elapsed: Duration,
    val averagePace: Pace?,
    val valid: Boolean,
    val mode: RunMode,
    val cardKind: CardKind
)

/**
 * 대시보드(서버 analysis.DashboardView, FR-050~FR-057, FR-077). [weeklyAchievement]는 이번 주 목표 거리 달성 비율(0.6 = 60%,
 * 100%를 넘을 수 있다)이고 목표가 없으면 null이다. [weekly]는 횟수·거리 목표와 실제 값, [grassLevels]는 잔디 칸의 진하기(0~4)다.
 */
data class Dashboard(
    val today: Distance,
    val total: Distance,
    val landmark: String?,
    val streakDays: Int,
    val grass: Map<LocalDate, Distance>,
    val weeklyAchievement: Double?,
    val crew: CrewArea?,
    val todayElapsed: Duration = Duration.ZERO,
    val todayRuns: Int = 0,
    val weekly: WeeklyGoalProgress? = null,
    val grassLevels: Map<LocalDate, Int> = emptyMap()
) {
    /** 크루가 있으면 크루 스트릭·잔디·랜드마크. */
    data class CrewArea(
        val crewName: String,
        val streakDays: Int,
        val grass: Map<LocalDate, Distance>,
        val landmark: String?
    )
}

/** 이번 주 목표 달성(서버 analysis.WeeklyAchievement, FR-056). 백분율은 100을 넘을 수 있다. */
data class WeeklyGoalProgress(
    val goalRuns: Int,
    val goalDistance: Distance,
    val actualRuns: Int,
    val actualDistance: Distance,
    val runsPercent: Int,
    val distancePercent: Int
)

/** 기록 목록(서버 analysis.RecordListView). 본인 기기의 미전송 기록은 화면이 PendingRunRepository로 합쳐 보인다. */
data class RecordList(val runs: List<RunSummary>)

/**
 * 기록 상세(서버 analysis.RecordDetailView, srs.md 6.3 3.2). route는 본인이거나 코스지도공유 켜짐일 때만 온다.
 * [unstableSegments]는 신호 불안정 구간(시작 기준 경과 시간, FR-033·FR-059)이다. 서버의 unstableRanges(초를 경로 포인트 번호로
 * 근사한 값)는 걸러진 위치가 빠지면 번호가 어긋나므로 읽지 않는다. [splitPaces]는 1km 구간 페이스, [fastestKm]은 가장 빠른 1km.
 */
data class RecordDetail(
    val summary: RunSummary,
    val route: List<GeoPoint>,
    val unstableSegments: List<Unstable>,
    val calories: Int?,
    val heartRateZones: Map<String, Duration>,
    val newRecords: List<String>,
    val crewContribution: Distance?,
    val rewardPoints: Int?,
    val weather: String?,
    val splitPaces: List<Pace> = emptyList(),
    val fastestKm: Pace? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null
) {
    /** 불안정 구간 하나: 시작·끝 경과 시간과 구간 거리. */
    data class Unstable(val from: Duration, val to: Duration, val distance: Distance)
}

/** 성장 분석(서버 analysis.GrowthView, FR-061~FR-063). */
data class Growth(val periods: List<PeriodTotal>, val bests: Map<String, Duration>, val longest: Distance?) {
    data class PeriodTotal(val start: LocalDate, val distance: Distance, val runs: Int, val averagePace: Pace?)
}

/**
 * 리포트(서버 analysis.ReportView, FR-064~FR-067). comment가 null이면 "코멘트 준비 중". [message]는 서버 안내 문구
 * (유효 러닝이 없을 때 등)이고 요약이 없을 때 보인다.
 */
data class Report(val summary: String?, val comment: String?, val commentSource: String?, val message: String? = null)

/** 랭킹(서버 analysis.RankingView, FR-070·FR-071). 집계 스냅숏이 아직 없으면 [aggregatedAt]이 null이다("집계 준비 중"). */
data class Ranking(
    val entries: List<Entry>,
    val mine: Entry?,
    val gapToNext: Distance?,
    val aggregatedAt: Instant?,
    val notice: String?
) {
    data class Entry(val rank: Int, val name: String, val distance: Distance)
}

/** 계획 진행(서버 analysis.PlanProgressView, FR-073·FR-187). */
data class PlanProgress(val days: List<Day>) {
    data class Day(val date: LocalDate, val planned: Distance?, val done: Distance, val supplement: Distance?)
}

/** 오늘 계획(서버 analysis.TodayPlanView). */
data class TodayPlan(val distance: Distance?, val pace: Pace?)

/**
 * 공유 카드 값(서버 running.RunCardData, D-22). 앞면은 기록, 뒷면은 지도 + 경로선(route).
 * courseMapShared가 거짓이면 뒷면 공유를 거부한다(FR-069).
 */
data class RunCard(
    val recordId: String,
    val cardKind: CardKind,
    val startedAt: Instant,
    val distance: Distance,
    val elapsed: Duration,
    val averagePace: Pace?,
    val route: List<GeoPoint>,
    val courseMapShared: Boolean
)
