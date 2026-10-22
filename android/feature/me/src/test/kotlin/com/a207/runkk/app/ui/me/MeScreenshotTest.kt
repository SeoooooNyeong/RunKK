package com.a207.runkk.app.ui.me

import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.MemberSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 로그인·가입·마이 화면 스크린샷(가짜 UiState, 390×844dp 다크). 기록: ./gradlew :feature:me:recordRoborazziLocalDebug */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class MeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun login() = compose.captureRunkkScreen("A1_login") {
        LoginScreen(LoginUiState(), onProvider = {}, onErrorShown = {})
    }

    @Test
    fun signUpConsent() = compose.captureRunkkScreen("A2_signup_1_consent") {
        SignUpScreen(
            SignUpUiState(
                consents = SignUpConsents.items.associate {
                    it.item to it.isRequired
                }
            ),
            SignUpActions()
        )
    }

    @Test
    fun signUpProfile() = compose.captureRunkkScreen("A3_signup_2_profile_visibility") {
        SignUpScreen(
            SignUpUiState(
                step = SignUpStep.PROFILE,
                nickname = "달리는곰",
                birthYear = 1998,
                gender = Gender.FEMALE,
                intro = "퇴근 뒤 한강"
            ),
            SignUpActions()
        )
    }

    @Test
    fun signUpTags() = compose.captureRunkkScreen("A4_signup_4_tags") {
        SignUpScreen(
            SignUpUiState(step = SignUpStep.TAGS, tags = setOf("SOCIAL_RUN", "FAMILIAR_ROUTE", "EVENING")),
            SignUpActions()
        )
    }

    @Test
    fun signUpAvatar() = compose.captureRunkkScreen("A_signup_5_avatar") {
        SignUpScreen(SignUpUiState(step = SignUpStep.AVATAR), SignUpActions())
    }

    @Test
    fun me() = compose.captureRunkkScreen("me_profile_menu") {
        MeScreen(MeUiState(isLoading = false, profile = sampleProfile), onNavigate = {}, onRetry = {}, onLogout = {})
    }

    /** 프로필 원에 기본 캐릭터(faceKey "default:BEAR") 그림. */
    @Test
    fun meDefaultCharacter() = compose.captureRunkkScreen("me_profile_default_character") {
        MeScreen(
            MeUiState(isLoading = false, profile = sampleProfile.copy(faceKey = "default:BEAR")),
            onNavigate = {},
            onRetry = {},
            onLogout = {}
        )
    }

    @Test
    fun meError() = compose.captureRunkkScreen("me_error") {
        MeScreen(MeUiState(isLoading = false, error = RunkkErrorText.of(AppError.Network)), onNavigate = {
        }, onRetry = {}, onLogout = {})
    }

    @Test
    fun profileEdit() = compose.captureRunkkScreen("me_profile_edit") {
        ProfileEditScreen(
            ProfileEditUiState(
                isLoading = false,
                nickname = "달리는곰",
                intro = "퇴근 뒤 한강",
                tags = setOf("SOCIAL_RUN", "EVENING")
            ),
            onBack = {},
            onNicknameChange = {},
            onIntroChange = {},
            onTagToggled = {},
            onSave = {},
            onErrorShown = {}
        )
    }

    private val settings = MemberSettings(
        values = mapOf(
            SettingKeys.RUN_TARGET to "KM5",
            SettingKeys.WATCH_LINKED to "true",
            SettingKeys.alert("KICKED") to "false"
        ),
        consents = mapOf("LOCATION" to true, "LLM_TRANSFER" to true)
    )

    @Test
    fun appSettings() = compose.captureRunkkScreen("me_settings_app") {
        SettingsScreen(SettingsPage.APP, SettingsUiState(isLoading = false, settings = settings), SettingsActions())
    }

    @Test
    fun freeRunSettings() = compose.captureRunkkScreen("me_settings_free_run") {
        SettingsScreen(
            SettingsPage.FREE_RUN,
            SettingsUiState(isLoading = false, settings = settings),
            SettingsActions()
        )
    }

    @Test
    fun notificationSettings() = compose.captureRunkkScreen("me_settings_notifications") {
        SettingsScreen(
            SettingsPage.NOTIFICATIONS,
            SettingsUiState(isLoading = false, settings = settings),
            SettingsActions()
        )
    }

    @Test
    fun consents() = compose.captureRunkkScreen("me_settings_consents") {
        SettingsScreen(
            SettingsPage.CONSENTS,
            SettingsUiState(isLoading = false, settings = settings),
            SettingsActions()
        )
    }

    @Test
    fun withdraw() = compose.captureRunkkScreen("me_withdraw") {
        WithdrawScreen(WithdrawUiState(isAcknowledged = true), {}, {}, {}, {})
    }
}
