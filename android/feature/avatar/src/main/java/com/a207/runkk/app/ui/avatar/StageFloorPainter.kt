package com.a207.runkk.app.ui.avatar

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/**
 * 그리지 않고 크기만 가진 배경 자리(홈 칸). 홈은 배경 사진을 화면 전체에 이미 깔아 두므로 칸이 배경을 다시 그리면 사진이 두 겹이 된다.
 * 그래서 칸 무대(RiveAvatarStage)에는 배경 규격 크기(4.3: 1080×1200)만 넘겨 바닥선·아트보드를 같은 규칙(4.4)으로 잡는다.
 */
internal class StageFloorPainter(override val intrinsicSize: Size) : Painter() {
    override fun DrawScope.onDraw() = Unit

    companion object {
        /** 배경 그림 규격(design-oo 05-30 4.3, [임시:D-성-136]). 390×420 칸이면 바닥선 342dp, 아트보드 378dp다([임시:D-성-134]). */
        val BACKDROP_SPEC = StageFloorPainter(Size(1080f, 1200f))
    }
}
