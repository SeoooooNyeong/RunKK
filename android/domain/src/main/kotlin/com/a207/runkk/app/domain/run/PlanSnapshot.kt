package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.util.UUID

/** 오늘 계획(목표 거리, 목표 페이스). */
data class PlanSnapshot(val targetDistance: Distance?, val targetPace: Pace?)
