package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.runbody.RunBodyAxis
import com.a207.runkk.app.domain.runbody.RunBodyMonth
import com.a207.runkk.app.domain.runbody.RunBodyRepository
import com.a207.runkk.app.domain.runbody.RunBodyResult
import com.a207.runkk.app.domain.runbody.RunBodySession
import com.a207.runkk.app.domain.runbody.RunBodyStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** 런바디 가짜 저장소: 기록마다 응답 순서(마지막 응답은 계속 돌려준다)와 부른 기록·달을 남긴다. */
class FakeRunBodyRepository : RunBodyRepository {
    val sessionReplies = mutableMapOf<String, MutableList<Outcome<RunBodySession>>>()
    val sessionCalls = mutableListOf<String>()
    var monthOf: (YearMonth) -> Outcome<RunBodyMonth> = { Outcome.Success(sampleMonth(it)) }
    val monthCalls = mutableListOf<YearMonth>()

    fun reply(recordId: String, vararg outcomes: Outcome<RunBodySession>) {
        sessionReplies[recordId] = outcomes.toMutableList()
    }

    override suspend fun session(recordId: String): Outcome<RunBodySession> {
        sessionCalls += recordId
        val queue = sessionReplies[recordId] ?: return Outcome.Success(done(recordId))
        return if (queue.size > 1) queue.removeAt(0) else queue.first()
    }

    override suspend fun month(month: YearMonth): Outcome<RunBodyMonth> {
        monthCalls += month
        return monthOf(month)
    }
}

fun waiting(recordId: String, status: RunBodyStatus): Outcome<RunBodySession> =
    Outcome.Success(RunBodySession(recordId, status, null))

fun done(recordId: String, result: RunBodyResult = sampleResult()): RunBodySession =
    RunBodySession(recordId, RunBodyStatus.DONE, result)

/** G-13·G-20 견본: 러닝 폼 미산출, 후반 처짐 코칭, 직전 러닝 있음. */
fun sampleResult(scores: Map<RunBodyAxis, Int?> = SAMPLE_SCORES): RunBodyResult = RunBodyResult(
    summary = RunBodyResult.Summary(
        distance = Distance(5_200),
        active = Duration.ofSeconds(30 * 60 + 10),
        pace = Pace(348),
        averageHeartRate = 149,
        maxHeartRate = 168,
        startedAt = Instant.parse("2026-11-01T22:10:00Z")
    ),
    axes = RunBodyAxis.entries.associateWith { axis ->
        when (val score = scores[axis]) {
            null -> RunBodyResult.AxisScore(null, "데이터 부족", RunBodyResult.AxisScore.Level.NONE)
            else -> RunBodyResult.AxisScore(score, "보통", RunBodyResult.AxisScore.Level.MID)
        }
    },
    laps = listOf(
        RunBodyResult.Lap(1.0, Pace(342), 148, isPartial = false),
        RunBodyResult.Lap(2.0, Pace(346), 150, isPartial = false),
        RunBodyResult.Lap(0.2, Pace(358), 154, isPartial = true)
    ),
    previous = RunBodyResult.Previous(Distance(5_000), Pace(362)),
    feedback = RunBodyResult.Feedback(
        headline = "후반까지 페이스를 잘 유지했어요.",
        good = "전반과 후반 페이스 차이가 작아요.",
        fix = "초반에는 여유 있게 출발해 보세요.",
        next = "편안한 페이스로 30분 달려 보세요."
    ),
    coaching = listOf(RunBodyResult.Coaching.POSITIVE_SPLIT),
    split = RunBodyResult.Split(Pace(344), Pace(352))
)

val SAMPLE_SCORES: Map<RunBodyAxis, Int?> = mapOf(
    RunBodyAxis.INTENSITY to 78,
    RunBodyAxis.ENDURANCE to 84,
    RunBodyAxis.STABILITY to 72,
    RunBodyAxis.FORM to null,
    RunBodyAxis.FITNESS to 80
)

/** G-22 견본(2026년 10월): 축 넷, 기준선 20회, 부하 1.14 적정, 개인 최고, 주간 거리 넷, 밤 30%. */
fun sampleMonth(month: YearMonth = YearMonth.of(2026, 10)): RunBodyMonth = RunBodyMonth(
    month = month,
    sessionCount = 12,
    averages = mapOf(
        RunBodyAxis.INTENSITY to 76,
        RunBodyAxis.ENDURANCE to 82,
        RunBodyAxis.STABILITY to 70,
        RunBodyAxis.FITNESS to 78
    ),
    bests = RunBodyMonth.Bests(Duration.ofSeconds(292), Duration.ofSeconds(605), Distance(21_300), -24),
    weeks = listOf(12_400L, 18_000L, 9_600L, 20_200L).mapIndexed { i, m ->
        RunBodyMonth.Week(LocalDate.of(2026, 9, 29).plusWeeks(i.toLong()), Distance(m))
    },
    nightRuns = 3,
    dayRuns = 7,
    baseline = RunBodyMonth.Baseline(
        isAvailable = true,
        sessionCount = 20,
        needs = 0,
        pace = Pace(348),
        averageHeartRate = 149
    ),
    load = RunBodyMonth.Load(
        isAvailable = true,
        ratio = 1.14,
        level = RunBodyMonth.Load.Level.NORMAL,
        acute = Distance(18_400),
        chronicWeekly = Distance(16_200),
        availableFrom = null
    )
)
