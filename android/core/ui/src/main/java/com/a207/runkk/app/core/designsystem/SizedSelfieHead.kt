package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.a207.runkk.app.core.model.FaceLayer
import kotlin.math.roundToInt

/** 한 칸이 쓰는 셀카 머리: 마지막으로 그린 크기의 머리를 들고 있다가 크기가 바뀔 때만 [SelfieHeadArt.of]로 다시 찾는다. */
internal class SizedSelfieHead(private val layers: List<FaceLayer>) {
    private var side = -1
    private var art: SelfieHeadArt? = null

    fun at(side: Int): SelfieHeadArt? {
        if (side != this.side) {
            art = SelfieHeadArt.of(layers, side)
            this.side = side
        }
        return art
    }
}
