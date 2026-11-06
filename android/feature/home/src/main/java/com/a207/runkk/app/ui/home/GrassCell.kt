package com.a207.runkk.app.ui.home

import java.time.LocalDate

/** 잔디 한 칸. [isToday]면 화면이 2dp 테두리를 그린다. */
data class GrassCell(val date: LocalDate, val state: GrassState, val isToday: Boolean)
