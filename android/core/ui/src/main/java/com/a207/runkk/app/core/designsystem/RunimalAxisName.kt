package com.a207.runkk.app.core.designsystem

import androidx.annotation.StringRes

/**
 * 러니멀 축 이름(서버 RunimalAxis 이름, axisRatios의 키)을 한국어 이름으로 바꾸는 대응표.
 * 서버 응답에는 한국어 이름이 없어 앱이 가진다. 모르는 축이면 null이니 호출하는 쪽이 값을 그대로 보이지 않게 처리한다.
 */
enum class RunimalAxisName(@StringRes val nameRes: Int) {
    COMPANION(R.string.runimal_axis_companion),
    DISTANCE(R.string.runimal_axis_distance),
    COURSE(R.string.runimal_axis_course),
    HABIT(R.string.runimal_axis_habit);

    companion object {
        fun of(axis: String?): RunimalAxisName? = entries.firstOrNull { it.name == axis }
    }
}
