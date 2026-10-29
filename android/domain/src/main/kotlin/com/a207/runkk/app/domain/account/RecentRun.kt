package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import java.time.LocalDate

/**
 * 다른 러너 프로필의 최근 러닝 한 줄(D-287, GET /members/{id}/profile recentRuns). [kind]를 모르면 null이다.
 * 기록 공개 범위를 통과할 때만 서버가 채운다.
 */
data class RecentRun(val kind: CardKind?, val startedOn: LocalDate, val distance: Distance)
