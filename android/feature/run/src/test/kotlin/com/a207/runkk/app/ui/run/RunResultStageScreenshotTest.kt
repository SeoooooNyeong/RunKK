package com.a207.runkk.app.ui.run

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.a207.runkk.app.core.designsystem.AchievementIcon
import com.a207.runkk.app.core.designsystem.AchievementRow
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RESULT_STAGE_DEFAULT_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.account.MyAvatar
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 러닝 결과 무대 스크린숏([임시:NFR-성-8], [임시:D-성-304]): 화면 폭 320·360·600 × 라이트·다크 × 무대 그림(기본, 도시, 트랙이 낮게
 * 그려진 도시)에서 아바타 발끝(위에서 266)과 높이(180)가 같다. 찍으면서 숫자로도 잰다(차이 1 이하). 무대 그림 전수는
 * ResultStageBackdropFilesTest가 파일로 본다. 전신 정지 그림 칸은 시험에서 비어 있어 성별 기본 서기 그림이 선다.
 */
@RunWith(RobolectricTestRunner::class)
class RunResultStageScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val result = RunResultUiState(
        isLoading = false,
        recordId = "rec-1",
        cardKind = CardKind.FREE_RUN,
        startedAt = Instant.parse("2026-10-15T22:12:00Z"),
        distance = Distance(5_210),
        elapsed = Duration.ofSeconds(1_668),
        averagePace = Pace(332),
        averageHeartRate = 152,
        splitPaces = listOf(Pace(380), Pace(368), Pace(362), Pace(371), Pace(341)),
        pausedTotal = Duration.ofSeconds(185),
        isUploaded = true,
        achievements = listOf(AchievementRow(AchievementIcon.REWARD, UiText.Res(R.string.run_result_reward), "+52"))
    )

    private fun capture(name: String, backgroundKey: String?, isDark: Boolean) {
        compose.captureRunkkScreen(name, isDarkTheme = isDark) {
            Stage(backgroundKey)
        }
        val avatar = compose.onNodeWithTag(RESULT_STAGE_DEFAULT_AVATAR_TAG).getUnclippedBoundsInRoot()
        assertTrue(abs(avatar.bottom.value - 266f) <= 1f, "$name: 발끝 ${avatar.bottom}")
        assertTrue(abs((avatar.bottom - avatar.top).value - 180f) <= 1f, "$name: 높이 ${avatar.bottom - avatar.top}")
    }

    @Composable
    private fun Stage(backgroundKey: String?) {
        CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.ALL_OPEN) {
            RunResultScreen(
                result.copy(myAvatar = MyAvatar(null, emptyList(), backgroundKey)),
                onCard = {},
                onHome = {},
                onRetry = {}
            )
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun width320Light() = capture("B4_result_stage_w320_default_light", backgroundKey = null, isDark = false)

    @Test
    @Config(qualifiers = "w320dp-h640dp-night-xhdpi")
    fun width320Dark() = capture("B4_result_stage_w320_paris_dark", backgroundKey = "PARIS", isDark = true)

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun width360Light() = capture("B4_result_stage_w360_paris_light", backgroundKey = "PARIS", isDark = false)

    @Test
    @Config(qualifiers = "w360dp-h780dp-night-xhdpi")
    fun width360Dark() = capture("B4_result_stage_w360_default_dark", backgroundKey = null, isDark = true)

    @Test
    @Config(qualifiers = "w600dp-h960dp-xhdpi")
    fun width600Light() = capture("B4_result_stage_w600_earth_lap_light", backgroundKey = "EARTH_LAP", isDark = false)

    @Test
    @Config(qualifiers = "w600dp-h960dp-night-xhdpi")
    fun width600Dark() = capture("B4_result_stage_w600_earth_lap_dark", backgroundKey = "EARTH_LAP", isDark = true)
}
