package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.model.Distance

/**
 * 배경 칸 하나의 값 묶음(Composable 매개변수 4개 기준, AN5-D-3). [key]·[stage]가 null이면 기본 배경, [lockedDistance]가 있으면
 * 그만큼 더 달려야 열리는 잠긴 배경, [isChoosing]이면 이 칸을 고르는 중이다.
 */
internal data class BackgroundTileState(
    val key: String?,
    val stage: String?,
    val name: String,
    val isSelected: Boolean,
    val lockedDistance: Distance?,
    val isChoosing: Boolean = false
)
