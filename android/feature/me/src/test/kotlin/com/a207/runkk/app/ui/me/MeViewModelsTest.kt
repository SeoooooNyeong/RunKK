package com.a207.runkk.app.ui.me

import android.content.Context
import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.ChangeSettingUseCase
import com.a207.runkk.app.domain.account.LinkSocialAccountUseCase
import com.a207.runkk.app.domain.account.LogoutUseCase
import com.a207.runkk.app.domain.account.ProfileEdit
import com.a207.runkk.app.domain.account.ProviderType
import com.a207.runkk.app.domain.account.WithdrawUseCase
import java.time.Year
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 마이·프로필 편집·설정·탈퇴 ViewModel: 상태 전이와 오류 표시. */
@RunWith(RobolectricTestRunner::class)
class MeViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private val session = FakeSessionRepository()
    private val settingsCopy = FakeSettingsRepository()
    private val kakao = FakeSocialLoginClient(ProviderType.KAKAO)
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun settingsViewModel() = SettingsViewModel(
        account,
        ChangeSettingUseCase(account, settingsCopy),
        session,
        LinkSocialAccountUseCase(account, session, settingsCopy),
        setOf(kakao, FakeSocialLoginClient(ProviderType.GOOGLE)),
        LogoutUseCase(account, session, FakePendingRunRepository(), settingsCopy),
        main.dispatcher
    )

    /** 화면(Route)이 하는 연동 순서: 시작 → 화면 코루틴에서 SDK 로그인 → 토큰 전달(ViewModel은 Context를 받지 않는다). */
    private suspend fun SettingsViewModel.link(provider: ProviderType) {
        onLinkStart(provider)?.let { client -> onLinkToken(client.signIn(context)) }
    }

    @Test
    fun `마이는 본인 프로필을 읽는다`() {
        val vm =
            MeViewModel(account)
        assertEquals(sampleProfile, vm.state.value.profile)
        assertEquals(listOf("profile:${AccountRepository.SELF}"), account.calls)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `AN8-3 프로필 편집을 저장하고 돌아와 화면이 재개되면 다시 읽는다(첫 재개는 건너뛴다)`() {
        val vm = MeViewModel(account)
        vm.onResumed()
        assertEquals(listOf("profile:${AccountRepository.SELF}"), account.calls, "처음 표시는 init이 읽었다")
        account.profile = sampleProfile.copy(displayName = "새이름")
        vm.onResumed()
        assertEquals("새이름", vm.state.value.profile?.displayName)
        assertEquals(2, account.calls.count { it == "profile:${AccountRepository.SELF}" })
    }

    @Test
    fun `프로필이 셀카 얼굴이면 부위 그림을 받아 원 안에 그린다`() {
        val layer = com.a207.runkk.app.core.model.FaceLayer(com.a207.runkk.app.core.model.FacePart.EYES, byteArrayOf(1))
        account.profile = sampleProfile.copy(faceKey = "selfie:face/7/s1")
        account.faceResult = com.a207.runkk.app.core.model.Outcome.Success(listOf(layer))
        val vm =
            MeViewModel(account)
        assertEquals(listOf(layer), vm.state.value.faceLayers)
        assertEquals(listOf("profile:${AccountRepository.SELF}", "face:selfie:face/7/s1"), account.calls)
    }

    @Test
    fun `프로필을 못 읽으면 오류를 보이고 다시 시도한다`() {
        account.failures["profile:0"] = AppError.Network
        val vm =
            MeViewModel(account)
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.error)
        account.failures.clear()
        vm.refresh()
        assertNull(vm.state.value.error)
        assertEquals(sampleProfile, vm.state.value.profile)
    }

    @Test
    fun `설정에서 로그아웃하면 로그인 화면으로 간다(FR-002)`() = runTest {
        val vm = settingsViewModel()
        vm.events.test {
            vm.onLogoutConfirmed()
            assertEquals(SettingsEvent.LoggedOut, awaitItem())
        }
        assertTrue("logout" in account.calls)
    }

    @Test
    fun `R2-04 프로필 편집은 내 아바타 얼굴 키를 읽어 둔다`() = runTest {
        account.profile = sampleProfile.copy(faceKey = "default:BEAR")
        val vm = ProfileEditViewModel(account, settingsCopy)
        assertEquals("default:BEAR", vm.state.value.faceKey)
    }

    @Test
    fun `닉네임 2~20자는 코드 포인트로 센다 - 이모지 1자는 저장하지 않고 20자는 자르지 않는다(D-143)`() = runTest {
        val vm = ProfileEditViewModel(account, settingsCopy)
        val emoji = "\uD83C\uDFC3"

        vm.onNicknameChange(emoji)
        assertFalse(vm.state.value.canSave, "이모지 1자는 2자 미만")
        vm.onNicknameChange(emoji.repeat(21))
        assertEquals(emoji.repeat(20), vm.state.value.nickname, "서로게이트를 가르지 않고 20자에서 자른다")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `AN5-D-15 설정의 고지 문서는 https 주소만 밖에서 열고 열기 실패는 안내한다`() = runTest {
        val vm = settingsViewModel()
        account.legal = "http://runkk.example/privacy"
        vm.onViewLegal("PRIVACY", UiText.Plain("개인정보"))
        assertEquals(null, vm.state.value.urlToOpen)
        assertEquals("http://runkk.example/privacy", vm.state.value.legal?.body)

        account.legal = "https://runkk.example/privacy"
        vm.onViewLegal("PRIVACY", UiText.Plain("개인정보"))
        assertEquals("https://runkk.example/privacy", vm.state.value.urlToOpen)
        vm.onUrlOpenFailed()
        assertEquals(null, vm.state.value.urlToOpen)
        assertEquals(UiText.Res(R.string.me_legal_open_failed), vm.state.value.message)
    }

    @Test
    fun `TC-9740 프로필 편집은 현재 값을 채우고 닉네임·소개·나이대·성별 공개를 함께 저장한다(FR-007, D-287)`() = runTest {
        val vm = ProfileEditViewModel(account, settingsCopy)
        assertEquals("달리는곰", vm.state.value.nickname)
        assertEquals(true, vm.state.value.isAgePublic)
        assertEquals(false, vm.state.value.isGenderPublic)
        assertEquals("30대", vm.state.value.ageBand)
        assertEquals(Gender.FEMALE, vm.state.value.gender)
        vm.onNicknameChange("빠른곰")
        vm.onAgePublicChange(false)
        vm.onGenderPublicChange(true)
        vm.events.test {
            vm.onSave()
            assertEquals(ProfileEditEvent.Saved, awaitItem())
        }
        assertEquals(
            ProfileEdit("빠른곰", "퇴근 뒤 한강", isAgePublic = false, isGenderPublic = true, gender = Gender.FEMALE),
            account.lastEdit
        )
        assertFalse(account.calls.any { it.startsWith("changeSetting") })
    }

    @Test
    fun `프로필 편집은 설정 사본의 출생연도를 채우고 고른 출생연도를 함께 저장한다`() = runTest {
        settingsCopy.birthYearValue = 1995
        val vm = ProfileEditViewModel(account, settingsCopy)
        assertEquals(1995, vm.state.value.birthYear)

        vm.onBirthYearChange(1998)
        assertEquals("${(Year.now().value - 1998) / 10 * 10}대", vm.state.value.ageBand, "나이대 표시를 바로 바꾼다")
        vm.events.test {
            vm.onSave()
            assertEquals(ProfileEditEvent.Saved, awaitItem())
        }
        assertEquals(1998, account.lastEdit?.birthYear)
    }

    @Test
    fun `출생연도가 없으면 바꾸지 않는다`() {
        val vm = ProfileEditViewModel(account, settingsCopy)
        assertNull(vm.state.value.birthYear)
        vm.onSave()
        assertNull(account.lastEdit?.birthYear)
    }

    @Test
    fun `TC-9740 한줄 소개는 40자까지 받고 41자째는 막는다`() {
        val vm = ProfileEditViewModel(account, settingsCopy)
        vm.onIntroChange("가".repeat(41))
        assertEquals(40, vm.state.value.intro.length)
    }

    @Test
    fun `옛 서버라 공개 여부가 없으면 비공개로 둔다(가정, D-289)`() {
        account.profile = sampleProfile.copy(isAgePublic = null, isGenderPublic = null)
        val vm = ProfileEditViewModel(account, settingsCopy)
        assertEquals(false, vm.state.value.isAgePublic)
        assertEquals(false, vm.state.value.isGenderPublic)
    }

    @Test
    fun `프로필 저장이 거부되면 사유를 보이고 머문다`() {
        account.failures["editProfile"] = AppError.Rejected("NICKNAME_INVALID")
        val vm = ProfileEditViewModel(account, settingsCopy)
        vm.onSave()
        assertEquals(RunkkErrorText.rejected("NICKNAME_INVALID"), vm.state.value.error)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun `설정 값을 바꾸면 변경 흐름을 부르고 화면에 바로 반영한다`() {
        val vm = settingsViewModel()
        assertEquals("true", vm.state.value.value(SettingKeys.RUN_VOICE))
        vm.setValue(SettingKeys.RUN_VOICE, "false")
        assertEquals("false", vm.state.value.value(SettingKeys.RUN_VOICE))
        assertTrue("changeSetting:RUN_SETTINGS.voice=false" in account.calls)
        assertEquals(1, settingsCopy.refreshes)
    }

    @Test
    fun `설정 변경이 실패하면 이전 값으로 되돌리고 알린다`() {
        account.failures["changeSetting:UNIT=MI"] = AppError.Network
        val vm = settingsViewModel()
        vm.setValue(SettingKeys.UNIT, "MI")
        assertEquals("KM", vm.state.value.value(SettingKeys.UNIT))
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.message)
    }

    @Test
    fun `동의 주기·철회는 동의 연산으로 보낸다(D-36)`() {
        val vm = settingsViewModel()
        vm.setConsent("HEART_RATE", true)
        vm.setConsent("LOCATION", false)
        assertTrue("giveConsent:HEART_RATE" in account.calls)
        assertTrue("withdrawConsent:LOCATION" in account.calls)
        assertEquals(true, vm.state.value.consent("HEART_RATE"))
        assertEquals(false, vm.state.value.consent("LOCATION"))
    }

    @Test
    fun `탈퇴는 확인한 뒤에만 보내고 끝나면 로그인 화면으로 간다(FR-020)`() = runTest {
        val pending = FakePendingRunRepository()
        val vm = WithdrawViewModel(WithdrawUseCase(account, pending, settingsCopy, session))
        vm.onWithdrawConfirmed()
        assertFalse("withdraw" in account.calls)
        vm.onAcknowledgedChange(true)
        vm.events.test {
            vm.onWithdrawConfirmed()
            assertEquals(WithdrawEvent.Withdrawn, awaitItem())
        }
        assertTrue(pending.cleared)
    }

    @Test
    fun `크루장 탈퇴 거부는 양도 안내를 보인다(FR-024)`() {
        account.failures["withdraw"] = AppError.Rejected("LEADER_MUST_TRANSFER")
        val vm = WithdrawViewModel(WithdrawUseCase(account, FakePendingRunRepository(), settingsCopy, session))
        vm.onAcknowledgedChange(true)
        vm.onWithdrawConfirmed()
        assertEquals(RunkkErrorText.rejected("LEADER_MUST_TRANSFER"), vm.state.value.error)
        assertFalse(vm.state.value.isWithdrawing)
    }

    @Test
    fun `한 설정이 실패해도 그사이 바꾼 다른 설정은 남는다`() {
        account.failures["changeSetting:UNIT=MI"] = AppError.Network
        val vm = settingsViewModel()
        vm.setValue(SettingKeys.RUN_VOICE, "false")
        vm.setValue(SettingKeys.UNIT, "MI")
        assertEquals("KM", vm.state.value.value(SettingKeys.UNIT))
        assertEquals("false", vm.state.value.value(SettingKeys.RUN_VOICE))
    }

    // ---------- v0.11: 게스트·연동·테마·근접 알림(FR-197~FR-199, FR-203, FR-109) ----------

    @Test
    fun `설정은 연동하지 않은 게스트인지 읽어 계정 유형과 로그아웃 확인을 고른다(FR-199)`() {
        session.credential = "g1.1.c"
        assertTrue(settingsViewModel().state.value.isGuest)
    }

    @Test
    fun `소셜 계정 연동 화면은 내 닉네임을 보인다(F9)`() {
        assertEquals(sampleProfile.displayName, settingsViewModel().state.value.nickname)
    }

    @Test
    fun `설정은 게스트면 고지를 보이고 연동에 성공하면 연동 회원으로 바뀐다(FR-198)`() = runTest {
        session.credential = "g1.1.c"
        val vm = settingsViewModel()
        assertTrue(vm.state.value.isGuest)
        vm.link(ProviderType.KAKAO)
        assertEquals(1, kakao.calls)
        assertTrue("linkSocialAccount" in account.calls)
        assertFalse(vm.state.value.isGuest)
        assertEquals(UiText.Res(R.string.me_link_done), vm.state.value.message)
        assertNull(vm.state.value.linkingProvider)
    }

    @Test
    fun `다른 회원과 연동된 소셜 계정이면 거부를 알리고 게스트로 남는다(FR-198)`() = runTest {
        session.credential = "g1.1.c"
        account.failures["linkSocialAccount"] = AppError.Rejected("ALREADY_MEMBER")
        val vm = settingsViewModel()
        vm.link(ProviderType.KAKAO)
        assertTrue(vm.state.value.isGuest)
        assertEquals(UiText.Res(R.string.me_link_already_member), vm.state.value.linkError)
    }

    @Test
    fun `연동 중에는 다시 시작하지 않고 화면이 사라져 SDK 로그인이 끊기면 진행 표시를 푼다(A4)`() {
        session.credential = "g1.1.c"
        val vm = settingsViewModel()
        assertEquals(kakao, vm.onLinkStart(ProviderType.KAKAO))
        assertEquals(ProviderType.KAKAO, vm.state.value.linkingProvider)
        assertNull(vm.onLinkStart(ProviderType.GOOGLE), "연동 중에는 다시 시작하지 않는다")
        vm.onLinkCancelled()
        assertNull(vm.state.value.linkingProvider)
        assertTrue(vm.state.value.isGuest)
    }

    @Test
    fun `게스트 자격 증명은 IO 디스패처에서 읽는다 - 메인에서 암호화 저장소를 열지 않는다(A4)`() {
        session.credential = "g1.1.c"
        val io = StandardTestDispatcher()
        val settings = SettingsViewModel(
            account,
            ChangeSettingUseCase(account, settingsCopy),
            session,
            LinkSocialAccountUseCase(account, session, settingsCopy),
            setOf(kakao),
            LogoutUseCase(account, session, FakePendingRunRepository(), settingsCopy),
            io
        )
        assertEquals(0, session.guestReads, "메인에서 읽었다")
        assertFalse(settings.state.value.isGuest)
        io.scheduler.advanceUntilIdle()
        assertEquals(1, session.guestReads)
        assertTrue(settings.state.value.isGuest)
    }

    @Test
    fun `제공자 로그인이 실패하면 연동을 부르지 않는다`() = runTest {
        session.credential = "g1.1.c"
        kakao.result = Outcome.Failure(AppError.Rejected("LOGIN_CANCELLED"))
        val vm = settingsViewModel()
        vm.link(ProviderType.KAKAO)
        assertFalse("linkSocialAccount" in account.calls)
        assertEquals(RunkkErrorText.rejected("LOGIN_CANCELLED"), vm.state.value.linkError)
    }

    @Test
    fun `테마는 기본 SYSTEM이고 바꾸면 THEME 설정으로 보내 설정 사본을 갱신한다(FR-203, D-92)`() {
        val vm = settingsViewModel()
        assertEquals("SYSTEM", vm.state.value.value(SettingKeys.THEME))
        vm.setValue(SettingKeys.THEME, "DARK")
        assertEquals("DARK", vm.state.value.value(SettingKeys.THEME))
        assertTrue("changeSetting:THEME=DARK" in account.calls)
        assertEquals(1, settingsCopy.refreshes)
    }

    @Test
    fun `근접 알림은 기본 켬이고 끄면 대결 설정의 proximityAlert로 보낸다(FR-109, D-162)`() {
        val vm = settingsViewModel()
        assertTrue(vm.state.value.flag(SettingKeys.BATTLE_PROXIMITY_ALERT))
        vm.setValue(SettingKeys.BATTLE_PROXIMITY_ALERT, "false")
        assertTrue("changeSetting:BATTLE_SETTINGS.proximityAlert=false" in account.calls)
    }

    @Test
    fun `다른 러너 프로필은 그 회원 ID로 읽고 실패하면 다시 시도한다(F8)`() {
        account.failures["profile:9"] = AppError.Network
        val vm = OtherProfileViewModel(account)
        vm.load(9)
        assertEquals(RunkkErrorText.of(AppError.Network), vm.state.value.error)
        account.failures.clear()
        vm.retry()
        assertEquals(sampleProfile, vm.state.value.profile)
        assertEquals(listOf("profile:9", "profile:9"), account.calls)
    }
}
