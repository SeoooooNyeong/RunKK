package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/** 지금 빌드의 미공개 기능 판정(D-252). RunkkNavHost가 넣고, 넣지 않으면(미리보기·화면 시험) 모두 열려 있다. */
val LocalReleaseScope = staticCompositionLocalOf { ReleaseScope.ALL_OPEN }
