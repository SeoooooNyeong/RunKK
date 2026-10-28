package com.a207.runkk.app.ui.share

import androidx.compose.ui.graphics.toArgb
import com.a207.runkk.app.core.designsystem.RunkkColors

/**
 * 카드 색(하이파이 다크 네온). 공유 카드는 앱 테마와 무관하게 다크 벌로 그리므로 core.designsystem RunkkColors.dark에서 읽는다
 * (D-140이 ui.share → core.designsystem을 표에 올렸다). 값을 따로 두지 않아 앱과 카드 색이 어긋나지 않는다(AN5-D-8).
 */
internal object CardColors {
    private val dark = RunkkColors.dark
    val BACKGROUND = dark.background.toArgb()
    val SURFACE = dark.surface.toArgb()
    val BORDER = dark.border.toArgb()
    val TEXT = dark.textPrimary.toArgb()
    val TEXT_SECONDARY = dark.textSecondary.toArgb()
    val LIME = dark.lime.toArgb()
    val ON_LIME = dark.onLime.toArgb()
    val LAVENDER = dark.lavender.toArgb()
}
