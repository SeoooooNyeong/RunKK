package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 앱 첫 화면을 스플래시 아래에서 미리 그려 두는 문(사용자 요청 2026-10-31: 기본 캐릭터가 남자로 보였다가 여자로 바뀌지 않게).
 * 첫 화면이 아직 읽는 중이면 [isHeld]가 참이고, RunkkAppContent는 그동안 스플래시를 걷지 않는다.
 */
@Stable
class FirstScreenGate {
    var isHeld by mutableStateOf(false)
        internal set
}

/** 첫 화면 문. 앱 시작 화면 밖(미리보기·시험)에는 없다. */
val LocalFirstScreenGate = staticCompositionLocalOf<FirstScreenGate?> { null }

/** [isReady]가 거짓인 동안 스플래시를 붙잡는다. 화면을 떠나면 놓는다. */
@Composable
fun HoldSplashUntil(isReady: Boolean) {
    val gate = LocalFirstScreenGate.current ?: return
    DisposableEffect(gate, isReady) {
        gate.isHeld = !isReady
        onDispose { gate.isHeld = false }
    }
}
