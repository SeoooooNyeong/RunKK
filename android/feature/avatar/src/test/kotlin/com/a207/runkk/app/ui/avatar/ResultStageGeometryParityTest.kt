package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.avatar.RiveAvatarGeometry
import com.a207.runkk.app.core.designsystem.ResultStageLayout
import kotlin.test.assertEquals
import org.junit.Test

/**
 * 결과 무대 상수와 홈 무대 상수의 일치([임시:D-성-288]). 디자인 시스템(core.designsystem)은 core.avatar를 import할 수 없어 바닥선과
 * 그림자 상수를 복제해 두므로, 두 쪽을 다 볼 수 있는 이 모듈에서 어긋남을 막는다.
 */
class ResultStageGeometryParityTest {
    @Test
    fun `TRACK_FROM_BOTTOM은 RiveAvatarGeometry·FLOOR_FROM_BOTTOM과 같다`() {
        assertEquals(RiveAvatarGeometry.FLOOR_FROM_BOTTOM, ResultStageLayout.TRACK_FROM_BOTTOM)
    }

    @Test
    fun `그림자 폭·높이·알파·흐림이 홈과 같은 비율이다`() {
        assertEquals(RiveAvatarGeometry.SHADOW_WIDTH, ResultStageLayout.SHADOW_WIDTH)
        assertEquals(RiveAvatarGeometry.SHADOW_HEIGHT, ResultStageLayout.SHADOW_HEIGHT)
        assertEquals(RiveAvatarGeometry.shadowAlpha(darkTheme = false), ResultStageLayout.SHADOW_ALPHA_LIGHT)
        assertEquals(RiveAvatarGeometry.shadowAlpha(darkTheme = true), ResultStageLayout.SHADOW_ALPHA_DARK)
        assertEquals(RiveAvatarGeometry.SHADOW_BLUR_DP, ResultStageLayout.SHADOW_BLUR_DP)
    }

    @Test
    fun `아바타 칸 비율은 아트보드 비율이고 결과 그림자는 폭 67·5 높이 13·5다`() {
        assertEquals(RiveAvatarGeometry.ASPECT, ResultStageLayout.AVATAR_W / ResultStageLayout.AVATAR_H)
        val layout = ResultStageLayout.forResult(360f, ResultStageLayout.STAGE_ASPECT)
        assertEquals(67.5f, layout.shadowWidth, 0.01f)
        assertEquals(13.5f, layout.shadowHeight, 0.01f)
    }
}
