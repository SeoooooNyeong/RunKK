package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance

/** 거리 라벨: 21,097m → "하프", 42,195m → "풀", 그 밖은 "5km". */
internal fun distanceLabel(distance: Distance): UiText = when (distance.meters) {
    in HALF_MIN..HALF_MAX -> UiText.Res(R.string.battle_distance_half)
    in FULL_MIN..FULL_MAX -> UiText.Res(R.string.battle_distance_full)
    else -> UiText.Plain(RunkkFormat.targetLabel(distance))
}

private const val HALF_MIN = 21_000L
private const val HALF_MAX = 21_200L
private const val FULL_MIN = 42_000L
private const val FULL_MAX = 42_300L

/** 하프·풀 마라톤 거리(m). */
internal const val HALF_MARATHON_METERS = 21_097L
internal const val FULL_MARATHON_METERS = 42_195L
