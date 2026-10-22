package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 기록 목록(서버 analysis.RecordListView). 본인 기기의 미전송 기록은 화면이 PendingRunRepository로 합쳐 보인다. */
data class RecordList(val runs: List<RunSummary>)
