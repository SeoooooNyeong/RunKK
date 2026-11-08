package com.a207.runkk.app.domain.battle

import java.time.Instant

/**
 * 남은 그만두기 한 건(S15P31A207-278). [battleId]가 null이면 대기열 취소가 ALREADY_MATCHED로 거부됐지만 대결 번호를 아직 모른다(진행 중인
 * 랜덤·고스트 대결을 찾아 끝낸다). [since]는 처음 남긴 시각이고, 오래된 것은 보내지 않고 지운다.
 */
data class PendingForfeit(val battleId: Long?, val since: Instant)
