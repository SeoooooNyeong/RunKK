package com.a207.runkk.app.ui.me

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Gender
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
            SignUpUiState(consents = SignUpConsents.items.associate { it.item to it.isRequired }),
            isGuest = false,
            SignUpActions()
        )
    }

    @Test
    fun guestConsent() = compose.captureRunkkScreen("A2_guest_consent") {
        SignUpScreen(SignUpUiState(), isGuest = true, SignUpActions())
    }

    @Test
    fun guestNotice() = compose.captureRunkkScreen("A8_guest_notice") {
        SignUpScreen(
            SignUpUiState(isGuestNoticeVisible = true, guestNickname = "러너1234"),
            isGuest = true,
            SignUpActions()
        )
    }

    @Test
    fun me() = compose.captureRunkkScreen("me_profile_menu") {
        MeScreen(MeUiState(isLoading = false, profile = sampleProfile), onNavigate = {}, onRetry = {})
    }

    /** 프로필 원에 기본 캐릭터(faceKey "default:BEAR") 그림. */
    @Test
    fun meDefaultCharacter() = compose.captureRunkkScreen("me_profile_default_character") {
        MeScreen(
            MeUiState(isLoading = false, profile = sampleProfile.copy(faceKey = "default:BEAR")),
            onNavigate = {},
            onRetry = {}
        )
    }

    @Test
    fun meError() = compose.captureRunkkScreen("me_error") {
        MeScreen(MeUiState(isLoading = false, error = RunkkErrorText.of(AppError.Network)), onNavigate = {
        }, onRetry = {})
    }

    @Test
    fun profileEdit() = compose.captureRunkkScreen("me_profile_edit") {
        ProfileEditScreen(
            ProfileEditUiState(
                isLoading = false,
                nickname = "달리는곰",
                intro = "퇴근 뒤 한강",
                ageBand = "20대",
                gender = Gender.FEMALE,
                isAgePublic = true,
                isGenderPublic = false
            ),
            onBack = {},
            onNicknameChange = {},
            onIntroChange = {},
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

    /** W40: 서버가 거부한 기록이 있을 때 맨 위 기록 전송 묶음(못 올린 기록 n건, 다시 보내기). */
    @Test
    fun appSettingsRejected() = compose.captureRunkkScreen("me_settings_app_rejected") {
        SettingsScreen(
            SettingsPage.APP,
            SettingsUiState(isLoading = false, settings = settings, rejectedRunCount = 2),
            SettingsActions()
        )
    }

    @Test
    fun appSettingsGuest() = compose.captureRunkkScreen("F4_settings_guest") {
        SettingsScreen(
            SettingsPage.APP,
            SettingsUiState(isLoading = false, settings = settings, isGuest = true),
            SettingsActions()
        )
    }

    @Test
    fun accountLink() = compose.captureRunkkScreen("F9_account_link") {
        SettingsScreen(
            SettingsPage.ACCOUNT_LINK,
            SettingsUiState(
                isLoading = false,
                settings = settings,
                isGuest = true,
                nickname = "러너1234",
                linkError = UiText.Res(R.string.me_link_already_member)
            ),
            SettingsActions()
        )
    }

    @Test
    fun accountLinkLight() = compose.captureRunkkScreen("F9_account_link_light", isDarkTheme = false) {
        SettingsScreen(
            SettingsPage.ACCOUNT_LINK,
            SettingsUiState(isLoading = false, settings = settings, isGuest = true, nickname = "러너1234"),
            SettingsActions()
        )
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

    // 라이트 벌(FR-203, NFR-041 화면 10개 × 테마 2).
    @Test
    fun meLight() = compose.captureRunkkScreen("me_profile_menu_light", isDarkTheme = false) {
        MeScreen(MeUiState(isLoading = false, profile = sampleProfile), onNavigate = {}, onRetry = {})
    }

    @Test
    fun appSettingsLight() = compose.captureRunkkScreen("me_settings_app_light", isDarkTheme = false) {
        SettingsScreen(SettingsPage.APP, SettingsUiState(isLoading = false, settings = settings), SettingsActions())
    }

    @Test
    fun freeRunSettingsLight() = compose.captureRunkkScreen("me_settings_free_run_light", isDarkTheme = false) {
        SettingsScreen(
            SettingsPage.FREE_RUN,
            SettingsUiState(isLoading = false, settings = settings),
            SettingsActions()
        )
    }

    @Test
    fun profileEditLight() = compose.captureRunkkScreen("me_profile_edit_light", isDarkTheme = false) {
        CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
            ProfileEditScreen(
                ProfileEditUiState(
                    isLoading = false,
                    nickname = "달리는곰",
                    intro = "퇴근 뒤 한강",
                    ageBand = "20대",
                    gender = Gender.FEMALE,
                    isAgePublic = true
                ),
                onBack = {},
                onNicknameChange = {},
                onIntroChange = {},
                onSave = {},
                onErrorShown = {}
            )
        }
    }

    @Test
    fun guestNoticeLight() = compose.captureRunkkScreen("A8_guest_notice_light", isDarkTheme = false) {
        CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
            SignUpScreen(
                SignUpUiState(isGuestNoticeVisible = true, guestNickname = "러너1234"),
                isGuest = true,
                SignUpActions()
            )
        }
    }

    @Test
    fun signUpConsentLight() = compose.captureRunkkScreen("A2_signup_1_consent_light", isDarkTheme = false) {
        SignUpScreen(
            SignUpUiState(consents = SignUpConsents.items.associate { it.item to it.isRequired }),
            isGuest = false,
            SignUpActions()
        )
    }

    @Test
    fun otherProfile() = compose.captureRunkkScreen("F8_other_profile") {
        OtherProfileScreen(OtherProfileUiState(memberId = 9, isLoading = false, profile = sampleProfile), {}, {})
    }

    @Test
    fun otherProfileLight() = compose.captureRunkkScreen("F8_other_profile_light", isDarkTheme = false) {
        OtherProfileScreen(OtherProfileUiState(memberId = 9, isLoading = false, profile = sampleProfile), {}, {})
    }
}
