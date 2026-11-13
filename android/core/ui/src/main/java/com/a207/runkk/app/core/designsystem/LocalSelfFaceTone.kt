package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.compositionLocalOf

/** 내 얼굴 그을림([SelfFaceTone]). 아바타 기능이 앱 맨 위에서 내려 주고, 없으면 그을리지 않는다. */
val LocalSelfFaceTone = compositionLocalOf { SelfFaceTone() }
