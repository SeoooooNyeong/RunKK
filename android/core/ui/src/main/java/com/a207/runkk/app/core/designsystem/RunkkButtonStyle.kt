package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 알약 버튼 모양(피그마 RunKK / Buttons / 기본 Style). 꺼진 버튼은 모양과 상관없이 회색([Gray]: surfaceHigh 채움 + 보조 글자)이다.
 * - [Lime] 주 행동(primary 채움), [Purple] 대화상자 확인(secondary 채움), [Gray] 비활성
 * - [White] 흰 채움·테두리 없음, [Outline] 흰 채움 + divider 1 테두리(취소·보조 행동)
 * - [Text]·[TextMuted] 바탕 없는 글자 버튼(본문색·보조색)
 */
enum class RunkkButtonStyle { Lime, Purple, Gray, White, Outline, Text, TextMuted }

@Composable
internal fun RunkkButtonStyle.colors(isEnabled: Boolean, isPressed: Boolean): PillColors {
    if (!isEnabled) {
        return when (this) {
            RunkkButtonStyle.Text, RunkkButtonStyle.TextMuted ->
                PillColors(Color.Transparent, RunkkColors.TextSecondary, null)

            else -> PillColors(RunkkColors.SurfaceHigh, RunkkColors.TextSecondary, null)
        }
    }
    return when (this) {
        RunkkButtonStyle.Lime ->
            PillColors(
                if (isPressed) RunkkColors.LimeFillPressed else RunkkColors.LimeFill,
                RunkkColors.OnLimeFill,
                null
            )

        RunkkButtonStyle.Purple -> PillColors(RunkkColors.Violet, RunkkColors.OnSecondary, null)

        RunkkButtonStyle.Gray -> PillColors(RunkkColors.SurfaceHigh, RunkkColors.TextSecondary, null)

        RunkkButtonStyle.White ->
            PillColors(
                if (isPressed) RunkkColors.SurfacePressed else RunkkColors.Surface,
                RunkkColors.TextPrimary,
                null
            )

        RunkkButtonStyle.Outline -> PillColors(
            if (isPressed) RunkkColors.SurfacePressed else RunkkColors.Surface,
            RunkkColors.TextPrimary,
            RunkkColors.Border
        )

        RunkkButtonStyle.Text -> PillColors(Color.Transparent, RunkkColors.TextPrimary, null)

        RunkkButtonStyle.TextMuted -> PillColors(Color.Transparent, RunkkColors.TextSecondary, null)
    }
}
