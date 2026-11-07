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

    companion object {
        /** 그 기록의 분석 작업이 없다(404 RUNBODY_NOT_FOUND). */
        const val NOT_FOUND = "RUNBODY_NOT_FOUND"

        /** 운영 기준값 RUNBODY_ENABLED가 꺼져 있다(403 FEATURE_CLOSED, D-440). */
        const val FEATURE_CLOSED = "FEATURE_CLOSED"
    }
}
