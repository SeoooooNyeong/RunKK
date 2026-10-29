package com.a207.runkk.app.api.record

import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.record.CardWeather
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.Evaluation
import com.a207.runkk.app.domain.record.Growth
import com.a207.runkk.app.domain.record.NewRecordMark
import com.a207.runkk.app.domain.record.PlanProgress
import com.a207.runkk.app.domain.record.Ranking
import com.a207.runkk.app.domain.record.RecordBattleType
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.domain.record.RecordList
import com.a207.runkk.app.domain.record.RecordVerdict
import com.a207.runkk.app.domain.record.Report
import com.a207.runkk.app.domain.record.RunCard
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.record.ShapePoint
import com.a207.runkk.app.domain.record.TodayPlan
import com.a207.runkk.app.domain.record.WeeklyGoalProgress
import java.time.Duration
import java.time.LocalDate

// DTO → 도메인 매퍼(13.2 toDomain). 앱이 모르는 모드·카드 종류는 자유런으로 읽는다(목록이 통째로 실패하지 않게).

internal fun GeoPointDto.toDomain(): GeoPoint = GeoPoint(latitude, longitude)

internal fun Int?.toPace(): Pace? = this?.takeIf { it > 0 }?.let(::Pace)

internal fun RunSummaryDto.toDomain(): RunSummary = RunSummary(
    recordId = recordId,
    startedAt = ServerTime.parse(startedAt),
    distance = Distance(distanceMeters),
    elapsed = Duration.ofSeconds(elapsedSeconds),
    averagePace = averagePaceSecondsPerKm.toPace(),
    isValid = valid,
    mode = enumByName<RunMode>(mode) ?: RunMode.FREE,
    cardKind = enumByName<CardKind>(cardKind) ?: CardKind.FREE_RUN,
    battleType = enumByName<RecordBattleType>(battleType),
    verdict = enumByName<RecordVerdict>(verdict),
    isPending = pending,
    isNewRecord = newRecord,
    newRecords = newRecords.orEmpty().mapNotNull { it.toDomain() },
    routeShape = routeShape.orEmpty().mapNotNull { it.toShapePoint() }
)

/** 모르는 신기록 종류는 버린다(태그는 "신기록"만 남는다). */
private fun RunSummaryDto.NewRecordDto.toDomain(): NewRecordMark? =
    enumByName<NewRecordMark.Kind>(kind)?.let { NewRecordMark(it, distance) }

/** [x, y] 한 점. 두 값이 아니면 버린다. */
private fun List<Double>.toShapePoint(): ShapePoint? =
    if (size == 2) ShapePoint(this[0].toFloat(), this[1].toFloat()) else null

/** 티어가 없거나 모르는 평가 종류는 평가 없음(null)으로 본다(평가 화면을 띄우지 않는다, D-289). */
private fun RecordDetailResponse.EvaluationDto.toDomain(): Evaluation? {
    val tier = tier?.takeIf { it.isNotBlank() } ?: return null
    val kind = enumByName<Evaluation.Kind>(kind) ?: return null
    return Evaluation(kind, tier, validRuns, runimalType)
}

private fun Map<String, Long>.toGrass(): Map<LocalDate, Distance> =
    entries.associate { (date, meters) -> ServerTime.parseDate(date) to Distance(meters) }

internal fun DashboardResponse.toDomain(): Dashboard = Dashboard(
    today = Distance(todayMeters),
    total = Distance(totalMeters),
    landmark = landmark,
    streakDays = streakDays,
    grass = grass.toGrass(),
    weeklyAchievement = weeklyAchievement,
    crew = crew?.let { Dashboard.CrewArea(it.crewName, it.streakDays, it.grass.toGrass(), it.landmark) },
    todayElapsed = Duration.ofSeconds(todaySeconds),
    todayRuns = todayRuns,
    weekly = weekly?.let {
        WeeklyGoalProgress(
            goalRuns = it.goalRuns,
            goalDistance = Distance(it.goalMeters),
            actualRuns = it.actualRuns,
            actualDistance = Distance(it.actualMeters),
            runsPercent = it.runsPercent,
            distancePercent = it.distancePercent
        )
    },
    grassLevels = grassLevels.entries.associate { (date, level) -> ServerTime.parseDate(date) to level },
    rewardBalance = rewardBalance,
    monthRuns = monthRuns,
    monthDistance = monthMeters?.let(::Distance)
)

internal fun RecordListResponse.toDomain(): RecordList = RecordList(runs.map { it.toDomain() })

internal fun RecordDetailResponse.toDomain(): RecordDetail = RecordDetail(
    summary = summary.toDomain(),
    route = route.map { it.toDomain() },
    unstableSegments = unstableSegments.map {
        RecordDetail.Unstable(
            Duration.ofSeconds(it.fromSeconds),
            Duration.ofSeconds(it.toSeconds),
            Distance(it.distanceMeters)
        )
    },
    calories = calories,
    heartRateZones = heartRateZones.mapValues { Duration.ofSeconds(it.value) },
    newRecords = newRecords,
    crewContribution = crewContributionMeters?.let(::Distance),
    rewardPoints = rewardPoints,
    weather = weather,
    splitPaces = splitPaces.mapNotNull { it.toPace() },
    fastestKm = fastestKmSecondsPerKm.toPace(),
    averageHeartRate = averageHeartRate,
    maxHeartRate = maxHeartRate,
    // 옛 서버(deletable 없음)는 본인 기록이고 집계 제외일 때만 삭제할 수 있다고 본다(D-280, D-289).
    isDeletable = deletable ?: (owner != false && !summary.valid),
    evaluation = evaluation?.toDomain(),
    temperatureC = temperatureC,
    sky = sky,
    pauseCount = pauses.size,
    paused = Duration.ofSeconds(pausedSeconds),
    recordVisibility = recordVisibility
)

internal fun GrowthResponse.toDomain(): Growth = Growth(
    periods =
        periods.map {
            Growth.PeriodTotal(
                ServerTime.parseDate(it.start),
                Distance(it.distanceMeters),
                it.runs,
                it.averagePaceSecondsPerKm.toPace()
            )
        },
    bests = bests.mapValues { Duration.ofSeconds(it.value) },
    longest = longestMeters?.let(::Distance)
)

internal fun ReportResponse.toDomain(): Report = Report(summary, comment, commentSource, message)

internal fun RankingResponse.Entry.toDomain(): Ranking.Entry = Ranking.Entry(rank, name, Distance(distanceMeters))

internal fun RankingResponse.toDomain(): Ranking = Ranking(
    entries = entries.map { it.toDomain() },
    mine = mine?.toDomain(),
    gapToNext = gapToNextMeters?.let(::Distance),
    aggregatedAt = aggregatedAt?.let(ServerTime::parse),
    notice = notice
)

internal fun PlanProgressResponse.toDomain(): PlanProgress = PlanProgress(
    days.map {
        PlanProgress.Day(
            date = ServerTime.parseDate(it.date),
            planned = it.plannedMeters?.let(::Distance),
            done = Distance(it.doneMeters),
            supplement = it.supplementMeters?.let(::Distance)
        )
    }
)

/** 거리·페이스가 모두 없으면 계획 없음(null). */
internal fun TodayPlanResponse.toDomain(): TodayPlan? {
    val distance = distanceMeters?.takeIf { it > 0 }?.let(::Distance)
    val pace = paceSecondsPerKm.toPace()
    return if (distance == null && pace == null) null else TodayPlan(distance, pace)
}

internal fun RunCardResponse.toDomain(): RunCard = RunCard(
    recordId = recordId,
    cardKind = enumByName<CardKind>(cardKind) ?: CardKind.FREE_RUN,
    startedAt = ServerTime.parse(startedAt),
    distance = Distance(distanceMeters),
    elapsed = Duration.ofSeconds(elapsedSeconds),
    averagePace = averagePaceSecondsPerKm.toPace(),
    route = route.map { it.toDomain() },
    isCourseMapShared = courseMapShared,
    averageHeartRate = averageHeartRate,
    newRecords = newRecords,
    weather = if (temperatureC == null && sky == null) {
        null
    } else {
        CardWeather(temperatureC, sky?.let { enumByName<CardWeather.Sky>(it) })
    },
    ownerName = nickname?.takeIf { it.isNotBlank() }
)
