package com.a207.runkk.app.ui.share

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * 화면에 그린 카드를 그대로 찍는다(사용자 요청 2026-10-30: 공유 이미지는 미리보기 카드와 같은 모양). 카드에 [capturedBy]를 걸고
 * 공유할 때 [capture]를 부른다. 기록 카드(앞·뒷면)와 Runimal 카드가 함께 쓴다.
 */
@Stable
class CardCapture internal constructor(internal val layer: GraphicsLayer) {
    /** 하드웨어 비트맵은 저장·전송할 수 없어 일반 비트맵으로 복사한다. */
    suspend fun capture(isDark: Boolean): CapturedCard =
        CapturedCard(layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false), isDark)
}

@Composable
fun rememberCardCapture(): CardCapture {
    val layer = rememberGraphicsLayer()
    return remember(layer) { CardCapture(layer) }
}

/** 이 요소가 그리는 내용을 [capture]의 층에 담으면서 화면에도 그대로 그린다. */
fun Modifier.capturedBy(capture: CardCapture): Modifier = drawWithContent {
    capture.layer.record { this@drawWithContent.drawContent() }
    drawLayer(capture.layer)
}
