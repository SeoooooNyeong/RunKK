package com.a207.runkk.app.ui.runimal

import androidx.annotation.StringRes
import com.a207.runkk.app.domain.runimal.AxisLean

/**
 * Runimal 축(서버 RunimalAxis 이름)의 양쪽 이름. 짧은 이름(솔로/소셜)은 16유형 범례·유형 칩에, 긴 이름(솔로런/소셜런)은
 * MY Runimal 축 막대에 쓴다. A쪽은 솔로·단거리·단골·루틴, B쪽은 소셜·장거리·탐험·즉흥이다.
 */
enum class RunimalAxisLabel(
    @StringRes private val shortA: Int,
    @StringRes private val shortB: Int,
    @StringRes private val longA: Int,
    @StringRes private val longB: Int
) {
    COMPANION(
        R.string.runimal_axis_solo,
        R.string.runimal_axis_social,
        R.string.runimal_axis_solo_run,
        R.string.runimal_axis_social_run
    ),
    DISTANCE(
        R.string.runimal_axis_short_distance,
        R.string.runimal_axis_long_distance,
        R.string.runimal_axis_short_distance,
        R.string.runimal_axis_long_distance
    ),
    COURSE(
        R.string.runimal_axis_regular,
        R.string.runimal_axis_explore,
        R.string.runimal_axis_regular_path,
        R.string.runimal_axis_explore_path
    ),
    HABIT(
        R.string.runimal_axis_routine,
        R.string.runimal_axis_impromptu,
        R.string.runimal_axis_routine_run,
        R.string.runimal_axis_impromptu_run
    );

    @StringRes
    fun shortName(lean: AxisLean): Int = if (lean == AxisLean.A) shortA else shortB

    @StringRes
    fun longName(lean: AxisLean): Int = if (lean == AxisLean.A) longA else longB

    companion object {
        /** 서버 축 이름의 이름표. 모르는 축은 null. */
        fun of(axis: String): RunimalAxisLabel? = entries.firstOrNull { it.name == axis }
    }
}
