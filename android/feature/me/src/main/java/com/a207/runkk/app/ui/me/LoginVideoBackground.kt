package com.a207.runkk.app.ui.me

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.a207.runkk.app.core.designsystem.LocalReduceMotion
import com.a207.runkk.app.core.designsystem.RunkkLoopingVideo

/**
 * 로그인 배경 영상(피그마 A-1 메모: 무음, 1초부터 끝까지 반복. 그 구간만 잘라 둔 파일을 반복한다). 첫 장면 그림을 먼저 깔고 그 위에 영상을 화면에 꽉 차게(잘라서) 튼다.
 * 반복하는 곳에서 멈칫하지 않게 다음 반복을 미리 읽는 재생기(RunkkLoopingVideo)로 튼다(사용자 요청 2026-10-31).
 * 움직임 줄이기 설정이나 미리보기에서는 그림만 보인다. 장식이므로 읽기 정보가 없다.
 */
@Composable
internal fun LoginVideoBackground(@RawRes video: Int, @DrawableRes poster: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clearAndSetSemantics {}) {
        Image(
            painterResource(poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (!LocalInspectionMode.current && !LocalReduceMotion.current) {
            RunkkLoopingVideo(video, Modifier.fillMaxSize())
        }
    }
}
