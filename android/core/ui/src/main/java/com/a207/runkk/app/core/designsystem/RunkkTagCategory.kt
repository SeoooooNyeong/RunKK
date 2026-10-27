package com.a207.runkk.app.core.designsystem

import androidx.annotation.StringRes

data class RunkkTagCategory(@StringRes val nameRes: Int, val tags: List<RunkkTag>, val isCrewOnly: Boolean = false)
