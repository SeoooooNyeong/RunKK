package com.a207.runkk.app.domain.crew

import java.time.Instant

/**
 * 중간 참여 승인(서버 ParticipationResponse, POST /crew-runs/{id}/join, 설계 05-32 5.2). [joinedAt]은 내 측정 시작 절대시각(중간 참여자는
 * 승인 시각, 출발 후보는 출발 시각)이고 [measurementRecordId]는 내 측정 기록 ID(RunStartSpec.recordId로 쓴다, 저장 성공 증거가 아니다)다.
 * 다시 불러도 같은 값이 온다([isAlreadyJoined]).
 */
data class CrewRunJoin(
    val postId: Long,
    val crewId: Long,
    val revision: Long,
    val joinedAt: Instant,
    val measurementRecordId: String,
    val isAlreadyJoined: Boolean,
    /** 내 참여가 이미 끝났다(그만두기·종료). 다시 재지 않는다. */
    val isEnded: Boolean = false
)
