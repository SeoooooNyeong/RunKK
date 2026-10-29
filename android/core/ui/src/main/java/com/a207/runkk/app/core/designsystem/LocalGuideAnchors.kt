package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** 첫 사용 가이드 앵커 표(D-277). RunkkNavHost가 하나 만들어 넣는다. 넣지 않으면 기록할 곳이 없는 빈 표다. */
val LocalGuideAnchors = staticCompositionLocalOf { GuideAnchors() }

/** 이 요소의 경계를 가이드 [step]의 앵커로 적는다(D-277). 요소가 화면에서 빠지면 지운다. */
fun Modifier.guideAnchor(step: GuideStep): Modifier = composed {
    val anchors = LocalGuideAnchors.current
    DisposableEffect(anchors, step) { onDispose { anchors.remove(step) } }
    onGloballyPositioned { anchors.put(step, it.boundsInRoot()) }
}
