package com.a207.runkk.app.ui.share

import com.a207.runkk.app.core.designsystem.RunResultDetailState
import java.time.Instant

/**
 * 기록 한 건의 결과 상세 한 장(RunResultDetail)과 시작 시각. 날짜 줄은 부르는 화면이 [startedAt]으로 채운다(크루런 결과 상세 F-16b처럼
 * domain.record를 볼 수 없는 화면 모듈이 쓴다, 5.1.2).
 */
data class RecordResultDetail(val startedAt: Instant, val state: RunResultDetailState)
