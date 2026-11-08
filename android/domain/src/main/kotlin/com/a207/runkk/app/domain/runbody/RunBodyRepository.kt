package com.a207.runkk.app.domain.runbody

import com.a207.runkk.app.core.model.Outcome
import java.time.YearMonth

/**
 * 런바디 결과 계약(D-453, 2.6). 구현 api.runbody가 Spring api의 RunBodyController를 부른다(앱은 ai 서버를 직접 부르지 않는다).
 * 분석 제출은 러닝 저장 때 서버가 한다(S-31). 앱은 상태와 결과를 읽기만 한다.
 */
interface RunBodyRepository {
    /**
     * 기록 하나의 분석 상태와 결과(GET /runbody/sessions/{recordId}, FR-266). 분석 작업이 없는 기록(집계 제외 등)은
     * Rejected([NOT_FOUND]), 기능이 닫혀 있으면 Rejected([FEATURE_CLOSED]), ai를 읽지 못하면 Server(503)다.
     */
    suspend fun session(recordId: String): Outcome<RunBodySession>

    /** 월간 집계(GET /runbody/months/{yyyy-MM}, FR-267). 러닝이 없는 달도 성공(sessionCount 0)이다. */
    suspend fun month(month: YearMonth): Outcome<RunBodyMonth>

    /**
     * 러닝 선택(G-30)의 러닝 점수, 최신순 최대 [size]개(GET /runbody/sessions, 계약 v1.2 7절). 기록 목록 줄과 recordId로 맞춘다.
     * 기능이 닫혀 있으면 Rejected([FEATURE_CLOSED])다.
     */
    suspend fun scores(size: Int = MAX_SCORES): Outcome<List<RunBodyRunScore>>

    /** 요약 최근 10회 변화와 나의 러닝 유형(GET /runbody/trend, 계약 v1.2 8절, G-14). */
    suspend fun trend(): Outcome<RunBodyTrend>

    companion object {
        /** 그 기록의 분석 작업이 없다(404 RUNBODY_NOT_FOUND). */
        const val NOT_FOUND = "RUNBODY_NOT_FOUND"

        /** 운영 기준값 RUNBODY_ENABLED가 꺼져 있다(403 FEATURE_CLOSED, D-440). */
        const val FEATURE_CLOSED = "FEATURE_CLOSED"

        /** 러닝 점수 한 번에 읽는 최대 수(서버 쪽 크기 상한 100). 러닝 선택 목록(최근 90일)을 한 번에 덮는다. */
        const val MAX_SCORES = 100
    }
}
