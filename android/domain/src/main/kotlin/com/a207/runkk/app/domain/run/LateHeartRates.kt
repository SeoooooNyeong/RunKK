package com.a207.runkk.app.domain.run

/**
 * 머리 정보 없는 기록(보관함이 최근 보낸 30건 밖)의 늦은 심박 항목(D-77, v0.8 검증 R37). data.store가 심박만 담아 남기고,
 * domain.sync.UploadPendingRunsUseCase가 PendingRunRepository.pendingLateHeartRates로 읽어 서버로 보낸 뒤 지운다.
 * samples는 측정 시각 순서이고 같은 시각은 한 번만 있다.
 */
data class LateHeartRates(val recordId: String, val samples: List<HeartRateSample>)
