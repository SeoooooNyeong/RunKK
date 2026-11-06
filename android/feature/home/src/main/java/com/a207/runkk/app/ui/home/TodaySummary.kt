package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.Distance
import java.time.Duration

/** 오늘 달린 거리·시간·횟수. */
data class TodaySummary(val distance: Distance, val elapsed: Duration, val runs: Int)
