package com.a207.runkk.app.core.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** 달리기 좌우 방향을 바꾸기 전에 같은 방향이 이어져야 하는 시간(D-765, D-762의 1초를 2초로 정정). */
const val RUN_FACING_HOLD_MILLIS = 2_000L

/**
 * 진행 방향 판정값([rawFacingLeft], 측정 화면의 `rememberHeadingLeft(route)`가 칸 인자로 넘긴 값)을 흔들림 없이 거른다. 새 방향이
 * [holdMillis] 동안 이어질 때만 바꾸고, 그 전에 돌아오면 무시한다(처음부터 다시 센다). 처음 값은 들어온 값이다.
 *
 * 돌려준 값을 [RiveAvatarStage]·[RunkkRiveAvatar]의 `mirrored`로 넘긴다. 뒤집기는 달리기 동작에만 걸린다.
 */
@Composable
fun rememberRunFacingLeft(rawFacingLeft: Boolean, holdMillis: Long = RUN_FACING_HOLD_MILLIS): Boolean {
    var facingLeft by remember { mutableStateOf(rawFacingLeft) }
    LaunchedEffect(rawFacingLeft, holdMillis) {
        if (rawFacingLeft != facingLeft) {
            delay(holdMillis)
            facingLeft = rawFacingLeft
        }
    }
    return facingLeft
}
