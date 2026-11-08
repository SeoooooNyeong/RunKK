package com.a207.runkk.app.core.designsystem

import androidx.annotation.StringRes

/** 크루 태그 한 줄(평일, 주말)과 그 줄의 시간대 태그(피그마 F-01b·F-04). */
data class RunkkTagCategory(@StringRes val nameRes: Int, val tags: List<RunkkTag>)
