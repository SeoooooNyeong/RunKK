package com.a207.runkk.app.core.designsystem.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * 화면 스크린샷 기준 기기: 하이파이 시안 390×844dp, xhdpi(780×1688px), 기기 다크(라이트 벌은 captureRunkkScreen의 isDarkTheme으로 고른다).
 * 시험 클래스에 `@Config(qualifiers = RUNKK_PHONE)`와 `@RunWith(RobolectricTestRunner::class)`를 붙인다.
 */
const val RUNKK_PHONE = "w390dp-h844dp-night-xhdpi"

/**
 * [content]를 RunkkTheme(동작 줄이기 켬, [isDarkTheme]이면 다크 벌·아니면 라이트 벌, FR-203)로 그리고 모듈의 src/test/snapshots/[name].png로 찍는다.
 * `./gradlew recordRoborazziLocalDebug`로 기록, `verifyRoborazziLocalDebug`로 기록과 비교한다.
 * 화면은 가짜 UiState를 넘긴 상태 없는 Screen 컴포저블을 쓴다(ViewModel·Hilt 없이).
 */
fun ComposeContentTestRule.captureRunkkScreen(
    name: String,
    isDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    setContent { RunkkTheme(darkTheme = isDarkTheme, reduceMotion = true, content = content) }
    onRoot().captureRoboImage("src/test/snapshots/$name.png")
}
