package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 리포트(서버 analysis.ReportView, FR-064~FR-067). comment가 null이면 "코멘트 준비 중". [message]는 서버 안내 문구
 * (유효 러닝이 없을 때 등)이고 요약이 없을 때 보인다.
 */
data class Report(val summary: String?, val comment: String?, val commentSource: String?, val message: String? = null)
