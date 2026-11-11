package com.a207.runkk.app.ui.me

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.DEFAULT_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RUNKK_AVATAR_FACE_TAG
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_RISE
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.runkkAvatarDefaultHeadTag
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.ProviderType
import com.a207.runkk.app.domain.account.RecentRun
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    fun `프로필 원은 기본 캐릭터 값이면 어느 값이든 내 성별의 기본 머리다(동물 그림 없음, D-성-180)`() {
        var faceKey by mutableStateOf<String?>("default:BEAR")
        compose.setContent {
            CompositionLocalProvider(LocalAvatarCharacter provides AvatarCharacter.FEMALE) {
                RunkkTheme(reduceMotion = true) {
                    MeScreen(
                        MeUiState(isLoading = false, profile = sampleProfile.copy(faceKey = faceKey)),
                        onNavigate = {},
                        onRetry = {}
                    )
                }
            }
        }
        compose.onNodeWithTag(runkkAvatarDefaultHeadTag(AvatarCharacter.FEMALE), useUnmergedTree = true).assertExists()

        faceKey = "default:DRAGON"
        compose.waitForIdle()
        compose.onNodeWithTag(runkkAvatarDefaultHeadTag(AvatarCharacter.FEMALE), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(RUNKK_AVATAR_FACE_TAG, useUnmergedTree = true).assertDoesNotExist()

        faceKey = null
        compose.waitForIdle()
        compose.onNodeWithTag(runkkAvatarDefaultHeadTag(AvatarCharacter.FEMALE), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `프로필 러니멀 칸은 한국어 유형 이름을 보이고 enum 이름은 없다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                MeScreen(
                    MeUiState(isLoading = false, profile = sampleProfile.copy(runimalType = "MORNING_SPARROW")),
                    onNavigate = {},
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("아침 참새").assertExists()
        compose.onNodeWithText("MORNING_SPARROW").assertDoesNotExist()
    }

    @Test
    fun `프로필 러니멀 칸 - 모르는 값은 알 수 없는 유형, 없으면 아직 유형이 없어요`() {
        var type by mutableStateOf<String?>("CHEETAH")
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                MeScreen(
                    MeUiState(isLoading = false, profile = sampleProfile.copy(runimalType = type)),
                    onNavigate = {},
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("알 수 없는 유형").assertExists()
        compose.onNodeWithText("CHEETAH").assertDoesNotExist()
        compose.runOnIdle { type = null }
        compose.onNodeWithText("아직 유형이 없어요").assertExists()
    }

    @Test
    fun `TC-9741 마이는 레벨·티어·Runimal·누적 거리·최장 거리·러닝 횟수와 메뉴 넷을 보이고 소개·나이대·성별은 숨긴다`() {
        val res = RuntimeEnvironment.getApplication().resources
        assertEquals("30대, ${res.getString(R.string.me_gender_female)}", aboutLine(res, sampleProfile))
        assertEquals(null, aboutLine(res, sampleProfile.copy(ageBand = null, gender = Gender.UNDISCLOSED)))
        val routes = mutableListOf<String>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                MeScreen(
                    MeUiState(
                        isLoading = false,
                        profile = sampleProfile.copy(longestDistance = Distance(21_097)),
                        tierDisplay = com.a207.runkk.app.core.model.TierDisplay.Confirmed("GOLD")
                    ),
                    onNavigate = { routes += it },
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("Lv.3").assertExists()
        compose.onNodeWithText("골드", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("128.4km").assertExists()
        compose.onNodeWithText("21.1km").assertExists()
        compose.onNodeWithText("42회").assertExists()
        compose.onNodeWithText("30대, 여성").assertDoesNotExist()
        compose.onNodeWithText("퇴근 뒤 한강").assertDoesNotExist()
        listOf("러닝 기록", "성장 분석", "런바디", "목표와 러닝 코치").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("랭킹").assertDoesNotExist()
        compose.onNodeWithText("아바타 꾸미기").assertDoesNotExist()
        compose.onNodeWithContentDescription("프로필 편집").performClick()
        assertEquals(
            listOf(Routes.RECORDS, Routes.GROWTH, Routes.REPORT, Routes.COACH, Routes.PROFILE_EDIT),
            routes
        )
    }

    @Test
    fun `TC-9741 TC-9698 다른 러너 프로필은 최근 러닝 3건만 보이고 1차 배포에서는 크루명을 숨긴다`() {
        val runs = (1..4).map {
            RecentRun(CardKind.FREE_RUN, LocalDate.of(2026, 10, 20 - it), Distance(5_020))
        }
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                RunkkTheme(reduceMotion = true) {
                    OtherProfileScreen(
                        OtherProfileUiState(isLoading = false, profile = sampleProfile.copy(recentRuns = runs)),
                        onBack = {},
                        onRetry = {}
                    )
                }
            }
        }
        compose.onNodeWithText("최근 러닝").assertExists()
        compose.onAllNodesWithText("5.02km").assertCountEquals(3)
        // AN8-10: 피그마의 [ ]는 변수 자리 표시라 글자로 그리지 않는다.
        compose.onNodeWithText("10.19").assertExists()
        compose.onNodeWithText("[10.19]").assertDoesNotExist()
        compose.onNodeWithText("10.16").assertDoesNotExist()
        compose.onNodeWithText("새벽크루").assertDoesNotExist()
        compose.onNodeWithText("30대, 여성", substring = true).assertExists()
    }

    @Test
    fun `TC-9741 TC-9698 최근 러닝이 없으면 영역이 없고 크루가 열리면 크루명을 보인다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                OtherProfileScreen(
                    OtherProfileUiState(isLoading = false, profile = sampleProfile),
                    onBack = {},
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("최근 러닝").assertDoesNotExist()
        compose.onNodeWithText("새벽크루").assertExists()
    }

    @Test
    fun `TC-9740 프로필 편집은 사진 칸 없이 나이·성별 공개 스위치를 보인다`() {
        val toggled = mutableListOf<String>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                ProfileEditScreen(
                    ProfileEditUiState(
                        isLoading = false,
                        nickname = "달리는곰",
                        ageBand = "20대",
                        gender = Gender.FEMALE,
                        isAgePublic = true
                    ),
                    onBack = {},
                    onNicknameChange = {},
                    onIntroChange = {},
                    onSave = {},
                    onErrorShown = {},
                    onAgePublicChange = { toggled += "age:$it" },
                    onGenderPublicChange = { toggled += "gender:$it" }
                )
            }
        }
        compose.onNodeWithText("2~20자").assertExists()
        compose.onNodeWithText("20대, 공개").assertExists()
        compose.onNodeWithText("여성, 비공개").performClick()
        compose.onNodeWithText("20대, 공개").performClick()
        compose.onNodeWithText("사진").assertDoesNotExist()
        assertEquals(listOf("gender:true", "age:false"), toggled)
    }

    @Test
    fun `R2-04 프로필 편집 맨 위에 내 아바타 얼굴을 보이고 눌러도 아무 일이 없다(G-2)`() {
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                RunkkTheme(reduceMotion = true) {
                    ProfileEditScreen(
                        ProfileEditUiState(isLoading = false, nickname = "달리는곰", faceKey = "default:BEAR"),
                        onBack = {},
                        onNicknameChange = {},
                        onIntroChange = {},
                        onSave = {},
                        onErrorShown = {}
                    )
                }
            }
        }
        compose.onNodeWithTag(DEFAULT_AVATAR_TAG + AvatarExpression.FACE.name, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(PROFILE_EDIT_AVATAR_TAG).assertHasNoClickAction()
        val avatar = compose.onNodeWithTag(PROFILE_EDIT_AVATAR_TAG).getUnclippedBoundsInRoot()
        val nickname = compose.onNodeWithText("달리는곰").getUnclippedBoundsInRoot()
        assertTrue(avatar.bottom <= nickname.top, "아바타가 닉네임 칸보다 위에 있다")
    }

    @Test
    fun `D100 마이 목록 끝은 솟은 탭 원에 가리지 않게 원 높이만큼 더 비운다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Box(Modifier.height(400.dp)) {
                    MeScreen(MeUiState(isLoading = false, profile = sampleProfile), onNavigate = {}, onRetry = {})
                }
            }
        }
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val last = compose.onNodeWithText("목표와 러닝 코치").getUnclippedBoundsInRoot()
        assertTrue(screen.bottom - last.bottom >= 16.dp + RUNKK_BOTTOM_BAR_RISE, "끝 여백 16 + 솟은 원 20")
    }

    @Test
    fun `D101 다른 러너 프로필 제목은 바 가운데에 있다(G-3)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                OtherProfileScreen(OtherProfileUiState(isLoading = false, profile = sampleProfile), onBack = {
                }, onRetry = {})
            }
        }
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val title = compose.onNode(hasText("프로필") and isHeading()).getUnclippedBoundsInRoot()
        assertEquals((screen.left + screen.right).value / 2f, (title.left + title.right).value / 2f, 1f)
    }

    @Test
    fun `G-2 프로필 편집 제목은 바 가운데에 있다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                ProfileEditScreen(
                    ProfileEditUiState(isLoading = false, nickname = "달리는곰"),
                    onBack = {},
                    onNicknameChange = {},
                    onIntroChange = {},
                    onSave = {},
                    onErrorShown = {}
                )
            }
        }
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val title = compose.onNode(hasText("프로필 편집") and isHeading()).getUnclippedBoundsInRoot()
        assertEquals((screen.left + screen.right).value / 2f, (title.left + title.right).value / 2f, 1f)
    }

    @Test
    fun `R2-07 동의 항목은 카드 없이 필수 넷, 구분선, 선택 둘 순서이고 제목은 가운데다(A-2)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) { SignUpScreen(SignUpUiState(), isGuest = false, SignUpActions()) }
        }
        val divider = compose.onNodeWithTag(CONSENT_DIVIDER_TAG).getUnclippedBoundsInRoot()
        val lastRequired = compose.onNodeWithText("위치 정보 수집", substring = true).getUnclippedBoundsInRoot()
        val firstOptional = compose.onNodeWithText("마케팅 정보 수신", substring = true).getUnclippedBoundsInRoot()
        assertTrue(lastRequired.bottom <= divider.top && divider.bottom <= firstOptional.top, "필수, 구분선, 선택 순서")
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val title = compose.onNode(hasText("시작하기") and isHeading()).getUnclippedBoundsInRoot()
        assertEquals((screen.left + screen.right).value / 2f, (title.left + title.right).value / 2f, 1f)
    }

    @Test
    fun `R2-08 게스트 안내는 양옆을 띄운 가운데 대화상자이고 닉네임 앞에 얼굴 아바타가 있다(A-3)`() {
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                RunkkTheme(reduceMotion = true) {
                    SignUpScreen(
                        SignUpUiState(isGuestNoticeVisible = true, guestNickname = "러너1234"),
                        isGuest = true,
                        SignUpActions()
                    )
                }
            }
        }
        compose.onNodeWithTag(DEFAULT_AVATAR_TAG + AvatarExpression.FACE.name, useUnmergedTree = true).assertExists()
        // 카드 양옆 28dp + 안쪽 20dp
        val title = compose.onNodeWithText("게스트로 시작했어요").getUnclippedBoundsInRoot()
        assertEquals(48f, title.left.value, 1f)
    }

    @Test
    fun `R2-09 자유런 목표 거리 첫 칸은 자유이고 제목은 가운데다(A-5)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SettingsScreen(
                    page = SettingsPage.FREE_RUN,
                    state = SettingsUiState(isLoading = false, settings = MemberSettings(emptyMap(), emptyMap())),
                    actions = SettingsActions()
                )
            }
        }
        compose.onNodeWithText("자유").assertExists()
        compose.onNodeWithText("목표 없음").assertDoesNotExist()
        // 제목은 바 가운데(A-5)
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val title = compose.onNode(hasText("러닝 설정") and isHeading()).getUnclippedBoundsInRoot()
        assertEquals((screen.left + screen.right).value / 2f, (title.left + title.right).value / 2f, 1f)
    }

    @Test
    fun `로그인은 로고 없이 두 브랜드 버튼을 보인다(피그마 A-1)`() {
        val tapped = mutableListOf<ProviderType>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                LoginScreen(LoginUiState(), onProvider = { tapped += it }, onErrorShown = {})
            }
        }
        compose.onNodeWithContentDescription("런크크").assertDoesNotExist()
        compose.onNodeWithContentDescription("카카오톡으로 시작하기").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("구글 계정으로 시작하기").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf(ProviderType.KAKAO, ProviderType.GOOGLE), tapped)
    }

    @Test
    fun `로그인 화면에 게스트로 시작이 있다(FR-001, FR-197)`() {
        var guest = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                LoginScreen(LoginUiState(), onProvider = {}, onErrorShown = {}, onGuest = { guest++ })
            }
        }
        compose.onNodeWithText("또는").assertExists()
        compose.onNodeWithText("게스트로 시작하기").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, guest)
    }

    @Test
    fun `동의 화면은 필수 동의 전에는 시작할 수 없고 보기 라벨이 있고 공개 범위 설명 줄은 없다(A2, FR-003)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) { SignUpScreen(SignUpUiState(), isGuest = false, SignUpActions()) }
        }
        compose.onNodeWithText("서비스 이용에 동의해 주세요").assertExists()
        startButton().assertIsNotEnabled()
        compose.onNodeWithContentDescription("서비스 이용약관 보기").assertExists()
        compose.onNodeWithContentDescription("뒤로").assertExists()
        compose.onNodeWithText("기록은 전체 공개로 시작해요. 설정에서 바꿀 수 있어요").assertDoesNotExist()
        compose.onNodeWithText("게스트로 시작하기").assertDoesNotExist()
    }

    @Test
    fun `동의를 채우면 시작하기 하나로 가입하거나 게스트로 시작한다(A2)`() {
        val submitted = mutableListOf<Boolean>()
        var isGuest by mutableStateOf(false)
        val agreed = SignUpUiState(consents = SignUpConsents.items.associate { it.item to it.isRequired })
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SignUpScreen(agreed, isGuest = isGuest, SignUpActions(onSubmit = { submitted += it }))
            }
        }
        startButton().assertIsEnabled().performClick()
        isGuest = true
        startButton().performClick()
        assertEquals(listOf(false, true), submitted)
    }

    /** 상단 제목과 아래 버튼이 모두 "시작하기"다. 누를 수 있는 쪽이 버튼이다. */
    private fun startButton() = compose.onNode(hasText("시작하기") and hasClickAction())

    @Test
    fun `TC-9717 게스트 시작 직후 안내 시트는 닉네임과 고지를 보이고 나중에, 지금 연동하기를 고른다(A-3, FR-199)`() {
        val picked = mutableListOf<String>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SignUpScreen(
                    SignUpUiState(isGuestNoticeVisible = true, guestNickname = "러너1234"),
                    isGuest = true,
                    SignUpActions(onGuestNoticeConfirmed = {
                        picked += "later"
                    }, onGuestNoticeLink = { picked += "link" })
                )
            }
        }
        compose.onNodeWithText("게스트로 시작했어요").assertExists()
        compose.onNodeWithText("러너1234").assertExists()
        compose.onNodeWithText("소셜 계정을 연동하지 않으면 앱 삭제, 기기 변경, 로그아웃 때 기록을 되살릴 수 없어요.").assertExists()
        compose.onNodeWithText("지금 연동하기").performClick()
        compose.onNodeWithText("나중에").performClick()
        assertEquals(listOf("link", "later"), picked)
    }

    @Test
    fun `탈퇴 버튼은 확인 체크 전에는 꺼져 있다`() {
        compose.setContent { RunkkTheme(reduceMotion = true) { WithdrawScreen(WithdrawUiState(), {}, {}, {}, {}) } }
        compose.onNodeWithText("탈퇴하기").assertIsNotEnabled()
    }

    @Test
    fun `탈퇴는 확인 체크 뒤 탈퇴하기로 보내고 취소는 돌아간다(F6)`() {
        var withdrawn = 0
        var back = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                WithdrawScreen(WithdrawUiState(isAcknowledged = true), { back++ }, {}, { withdrawn++ }, {})
            }
        }
        compose.onNodeWithText("탈퇴하기 전에 확인해 주세요").assertExists()
        compose.onNodeWithText("탈퇴하기").performClick()
        assertEquals(1, withdrawn)
        compose.onNodeWithText("취소").performClick()
        assertEquals(1, back)
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
    fun `가입 시간이 지난 안내는 로그인으로 돌아가는 버튼을 준다`() {
        var exited = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SignUpScreen(
                    SignUpUiState(
                        submitError = UiText.Plain("가입 시간이 지났어요. 다시 로그인해 주세요"),
                        canRetrySubmit = false
                    ),
                    isGuest = false,
                    SignUpActions(onBack = { exited++ })
                )
            }
        }
        compose.onNodeWithText("가입 시간이 지났어요. 다시 로그인해 주세요").assertExists()
        compose.onNodeWithText("로그인으로 돌아가기").performClick()
        assertEquals(1, exited)
    }

    @Test
    fun `게스트의 로그아웃 확인은 기기 기록 고지와 먼저 연동하기를 보인다(F10, FR-199)`() {
        val routes = mutableListOf<String>()
        var loggedOut = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SettingsScreen(
                    page = SettingsPage.APP,
                    state = SettingsUiState(
                        isLoading = false,
                        settings = MemberSettings(emptyMap(), emptyMap()),
                        isGuest = true
                    ),
                    actions = SettingsActions(onNavigate = { routes += it }, onLogout = { loggedOut++ })
                )
            }
        }
        compose.onNodeWithText("로그아웃").performScrollTo().performClick()
        compose.onNodeWithText("로그아웃할까요?").assertExists()
        compose.onNodeWithText("로그아웃하면 이 기기의 기록이 지워져요.").assertExists()
        compose.onNodeWithText("먼저 연동하기").performClick()
        assertEquals(listOf(MeRoutes.ACCOUNT_LINK), routes)
        assertEquals(0, loggedOut)
    }

    @Test
    fun `W40 못 올린 기록이 있을 때만 건수와 다시 보내기를 보이고 누르면 다시 보낸다`() {
        var resent = 0
        var count by mutableStateOf(0)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SettingsScreen(
                    page = SettingsPage.APP,
                    state = SettingsUiState(
                        isLoading = false,
                        settings = MemberSettings(emptyMap(), emptyMap()),
                        rejectedRunCount = count
                    ),
                    actions = SettingsActions(onResendRejected = { resent++ })
                )
            }
        }
        compose.onNodeWithText("다시 보내기").assertDoesNotExist()
        compose.onNodeWithText("기록 전송").assertDoesNotExist()

        count = 3
        compose.onNodeWithText("못 올린 기록 3건").assertExists()
        compose.onNodeWithText("다시 보내기").performClick()

        assertEquals(1, resent)
    }

    @Test
    fun `연동 회원의 로그아웃 확인은 제목과 취소, 로그아웃뿐이다`() {
        var loggedOut = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SettingsScreen(
                    page = SettingsPage.APP,
                    state = SettingsUiState(isLoading = false, settings = MemberSettings(emptyMap(), emptyMap())),
                    actions = SettingsActions(onLogout = { loggedOut++ })
                )
            }
        }
        compose.onNodeWithText("소셜 계정 연동").assertDoesNotExist()
        compose.onNodeWithText("로그아웃").performScrollTo().performClick()
        compose.onNodeWithText("먼저 연동하기").assertDoesNotExist()
        compose.onAllNodesWithText("로그아웃")[1].performClick()
        assertEquals(1, loggedOut)
    }

    @Test
    fun `설정은 계정 유형, 소셜 계정 연동 줄, 테마 선택을 보이고 탈퇴로 간다(F4, FR-198, FR-203)`() {
        val routes = mutableListOf<String>()
        val values = mutableListOf<Pair<String, String>>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SettingsScreen(
                    page = SettingsPage.APP,
                    state = SettingsUiState(
                        isLoading = false,
                        settings = MemberSettings(emptyMap(), emptyMap()),
                        isGuest = true
                    ),
                    actions = SettingsActions(onNavigate = { routes += it }, setValue = { k, v -> values += k to v })
                )
            }
        }
        compose.onNodeWithText("게스트").assertExists()
        compose.onNodeWithText("소셜 계정 연동").performClick()
        compose.onNodeWithText("다크").performScrollTo().performClick()
        compose.onNodeWithText("회원 탈퇴").performScrollTo().performClick()
        assertEquals(listOf(MeRoutes.ACCOUNT_LINK, Routes.WITHDRAW), routes)
        assertEquals(listOf(SettingKeys.THEME to "DARK"), values)
    }

    @Test
    fun `소셜 계정 연동은 닉네임과 상태, 두 제공자 버튼, 거부 한 줄을 보인다(F9, FR-198)`() {
        val linked = mutableListOf<ProviderType>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SettingsScreen(
                    page = SettingsPage.ACCOUNT_LINK,
                    state = SettingsUiState(
                        isLoading = false,
                        settings = MemberSettings(emptyMap(), emptyMap()),
                        isGuest = true,
                        nickname = "러너1234",
                        linkError = UiText.Res(R.string.me_link_already_member)
                    ),
                    actions = SettingsActions(onLink = { linked += it })
                )
            }
        }
        compose.onNodeWithText("러너1234").assertExists()
        compose.onNodeWithText("게스트, 연동 안 됨").assertExists()
        compose.onNodeWithText("이미 가입된 계정이에요").assertExists()
        compose.onNodeWithText("카카오 계정 연동").performClick()
        compose.onNodeWithText("Google 계정 연동").performClick()
        assertEquals(listOf(ProviderType.KAKAO, ProviderType.GOOGLE), linked)
    }

    @Test
    fun `알림 설정 스위치 하나는 묶인 알림 종류를 함께 끈다(F5)`() {
        val values = mutableListOf<Pair<String, String>>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SettingsScreen(
                    page = SettingsPage.NOTIFICATIONS,
                    state = SettingsUiState(isLoading = false, settings = MemberSettings(emptyMap(), emptyMap())),
                    actions = SettingsActions(setValue = { k, v -> values += k to v })
                )
            }
        }
        compose.onNodeWithText("크루 투표").performScrollTo().performClick()
        assertEquals(
            listOf(SettingKeys.alert("CREW_POLL") to "false", SettingKeys.alert("POLL_CLOSING_SOON") to "false"),
            values
        )
    }

    @Test
    fun `대결 설정에 근접 알림 켬, 끔이 있다(FR-109, FR-196)`() {
        val values = mutableListOf<Pair<String, String>>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                SettingsScreen(
                    page = SettingsPage.FREE_RUN,
                    state = SettingsUiState(isLoading = false, settings = MemberSettings(emptyMap(), emptyMap())),
                    actions = SettingsActions(setValue = { k, v -> values += k to v })
                )
            }
        }
        compose.onNodeWithText("근접 알림 (워치)").performScrollTo().performClick()
        assertEquals(listOf(SettingKeys.BATTLE_PROXIMITY_ALERT to "false"), values)
    }
}
