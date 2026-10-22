package com.a207.runkk.app.ui.me

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.account.Gender
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.ProviderType
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 로그인·가입·탈퇴·설정 화면: 주요 버튼 동작과 접근성 라벨(Robolectric Compose). */
@RunWith(RobolectricTestRunner::class)
class MeScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `프로필 러니멀 칸은 한국어 유형 이름을 보이고 enum 이름은 없다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                MeScreen(
                    MeUiState(isLoading = false, profile = sampleProfile.copy(runimalType = "MORNING_SPARROW")),
                    onNavigate = {},
                    onRetry = {},
                    onLogout = {}
                )
            }
        }
        compose.onNodeWithText("아침 참새").assertExists()
        compose.onNodeWithText("MORNING_SPARROW").assertDoesNotExist()
    }

    @Test
    fun `프로필 러니멀 칸 - 모르는 값은 알 수 없는 유형, 없으면 판정 전`() {
        var type by mutableStateOf<String?>("CHEETAH")
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                MeScreen(
                    MeUiState(isLoading = false, profile = sampleProfile.copy(runimalType = type)),
                    onNavigate = {},
                    onRetry = {},
                    onLogout = {}
                )
            }
        }
        compose.onNodeWithText("알 수 없는 유형").assertExists()
        compose.onNodeWithText("CHEETAH").assertDoesNotExist()
        compose.runOnIdle { type = null }
        compose.onNodeWithText("판정 전").assertExists()
    }

    @Test
    fun `마이 프로필은 입력한 나이대·성별을 보이고 밝히지 않음은 뺀다(FR-006)`() {
        val res = RuntimeEnvironment.getApplication().resources
        assertEquals(
            "30대 · ${res.getString(R.string.me_gender_female)}",
            aboutLine(res, sampleProfile)
        )
        assertEquals("30대 · 여성", aboutLine(res, sampleProfile))
        assertEquals(null, aboutLine(res, sampleProfile.copy(ageBand = null, gender = Gender.UNDISCLOSED)))
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                MeScreen(MeUiState(isLoading = false, profile = sampleProfile), onNavigate = {
                }, onRetry = {}, onLogout = {})
            }
        }
        compose.onNodeWithText("30대 · 여성").assertExists()
    }

    @Test
    fun `로그인은 로고 라벨과 두 제공자 버튼을 보인다`() {
        val tapped = mutableListOf<ProviderType>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                LoginScreen(LoginUiState(), onProvider = { tapped += it }, onErrorShown = {})
            }
        }
        compose.onNodeWithContentDescription("런크크").assertExists()
        compose.onNodeWithText("카카오로 시작하기").performClick()
        compose.onNodeWithText("Google로 시작하기").performClick()
        assertEquals(listOf(ProviderType.KAKAO, ProviderType.GOOGLE), tapped)
    }

    @Test
    fun `가입 1단계는 필수 동의 전에는 다음을 누를 수 없고 보기에 라벨이 있다`() {
        compose.setContent { RunkkTheme(reduceMotion = true) { SignUpScreen(SignUpUiState(), SignUpActions()) } }
        compose.onNodeWithText("다음").assertIsNotEnabled()
        compose.onNodeWithContentDescription("서비스 이용약관 보기").assertExists()
        compose.onNodeWithContentDescription("뒤로").assertExists()
    }

    @Test
    fun `가입 1단계 필수 동의를 채우면 다음이 켜진다`() {
        var next = 0
        val isAgreed = SignUpUiState(consents = SignUpConsents.items.associate { it.item to it.isRequired })
        compose.setContent {
            RunkkTheme(reduceMotion = true) { SignUpScreen(isAgreed, SignUpActions(onNext = { next++ })) }
        }
        compose.onNodeWithText("다음").assertIsEnabled().performClick()
        assertEquals(1, next)
    }

    @Test
    fun `가입 4단계 태그 칩은 고른 상태를 알리고 개수를 보인다`() {
        val toggled = mutableListOf<String>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SignUpScreen(
                    SignUpUiState(step = SignUpStep.TAGS, tags = setOf("SOCIAL_RUN")),
                    SignUpActions(onTagToggled = {
                        toggled +=
                            it
                    })
                )
            }
        }
        compose.onNodeWithText("함께가 좋아요").assertIsOn()
        compose.onNodeWithText("1개 선택").assertExists()
        compose.onNodeWithText("새로운 길 탐험").performScrollTo().performClick()
        assertEquals(listOf("EXPLORER_ROUTE"), toggled)
    }

    @Test
    fun `탈퇴 버튼은 확인 체크 전에는 꺼져 있다`() {
        compose.setContent { RunkkTheme(reduceMotion = true) { WithdrawScreen(WithdrawUiState(), {}, {}, {}, {}) } }
        compose.onNodeWithText("탈퇴하기").assertIsNotEnabled()
    }

    @Test
    fun `탈퇴는 확인 대화상자를 거쳐서만 보낸다`() {
        var withdrawn = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                WithdrawScreen(WithdrawUiState(isAcknowledged = true), {}, {}, { withdrawn++ }, {})
            }
        }
        compose.onNodeWithText("탈퇴하기").performClick()
        assertEquals(0, withdrawn)
        compose.onNodeWithText("탈퇴").performClick()
        assertEquals(1, withdrawn)
    }

    @Test
    fun `위치 동의 철회는 확인을 거친다`() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SettingsScreen(
                    page = SettingsPage.CONSENTS,
                    state = SettingsUiState(
                        isLoading = false,
                        settings = MemberSettings(emptyMap(), mapOf("LOCATION" to true))
                    ),
                    actions = SettingsActions(setConsent = { item, on -> changes += item to on })
                )
            }
        }
        compose.onNodeWithText("위치 정보 수집").performClick()
        assertEquals(emptyList(), changes)
        compose.onNodeWithText("철회").performClick()
        assertEquals(listOf("LOCATION" to false), changes)
    }

    @Test
    fun `가입 5단계 실패 안내와 다시 시도 버튼을 보인다`() {
        var retried = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SignUpScreen(
                    SignUpUiState(step = SignUpStep.AVATAR, submitError = UiText.Plain("네트워크에 연결할 수 없어요")),
                    SignUpActions(onRetrySubmit = { retried++ })
                )
            }
        }
        compose.onNodeWithText("네트워크에 연결할 수 없어요").assertExists()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `가입 시간이 지난 안내는 로그인으로 돌아가는 버튼을 준다`() {
        var exited = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SignUpScreen(
                    SignUpUiState(
                        step = SignUpStep.AVATAR,
                        submitError = UiText.Plain("가입 시간이 지났어요. 다시 로그인해 주세요"),
                        canRetrySubmit = false
                    ),
                    SignUpActions(onExit = { exited++ })
                )
            }
        }
        compose.onNodeWithText("가입 시간이 지났어요. 다시 로그인해 주세요").assertExists()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithText("로그인으로 돌아가기").performClick()
        assertEquals(1, exited)
    }

    @Test
    fun `가입 안내는 이전 단계로 돌아가도 문구가 보인다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SignUpScreen(
                    SignUpUiState(step = SignUpStep.TAGS, submitError = UiText.Plain("성향 태그는 분류마다 하나씩, 1~5개를 골라 주세요")),
                    SignUpActions()
                )
            }
        }
        compose.onNodeWithText("성향 태그는 분류마다 하나씩, 1~5개를 골라 주세요").assertExists()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
    }
}
