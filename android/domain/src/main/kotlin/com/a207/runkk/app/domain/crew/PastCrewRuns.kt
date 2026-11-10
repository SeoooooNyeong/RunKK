package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * 지난 크루런 한 달(서버 CrewScheduleDtos.PastCrewRunsResponse, 피그마 F-20, contract-W6). [days]는 달력 점을 찍을 날짜,
 * [runs]는 그 달(또는 고른 날) 지난 크루런을 최신순으로, [earliestMonth]는 월 이동·기간 휠의 시작(없으면 null)이다.
 */
data class PastCrewRuns(
    val month: YearMonth,
    val days: Set<LocalDate>,
    val runs: List<PastCrewRun>,
    val earliestMonth: YearMonth?
)
