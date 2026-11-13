package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test

val sampleBackgrounds = AvatarBackgrounds(
    selectedKey = null,
    items = listOf(
        AvatarBackgrounds.Item(
            "INCHEON",
            "인천",
            "DOMESTIC",
            Distance(60_000),
            true,
            Instant.parse("2026-10-20T00:00:00Z")
        ),
        AvatarBackgrounds.Item(
            "CHUNCHEON",
            "춘천",
            "DOMESTIC",
            Distance(150_000),
            true,
            Instant.parse("2026-10-21T00:00:00Z")
        ),
        AvatarBackgrounds.Item("BEIJING", "베이징", "EAST_ASIA", Distance(950_000), false, null, Distance(829_500))
    )
)

/** 랜드마크 배경 고르기(FR-201, FR-202): 해금·잠김 목록, 잠긴 배경은 보내지 않고 안내, 고르기 거부 문구. */
class AvatarBackgroundViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val avatars = FakeAvatars()

    @Test
    fun `배경 목록을 읽어 고른 배경과 해금 여부를 보인다`() {
        avatars.backgroundsResult = Outcome.Success(sampleBackgrounds.copy(selectedKey = "INCHEON"))
        val vm = AvatarBackgroundViewModel(avatars, storeOf(avatars))
        assertEquals("INCHEON", vm.state.value.selectedKey)
        assertEquals(listOf(true, true, false), vm.state.value.backgrounds?.items?.map { it.isUnlocked })
    }

    @Test
    fun `해금한 배경을 고르면 서버에 보내고 고른 배경으로 표시한다`() {
        val vm = AvatarBackgroundViewModel(avatars, storeOf(avatars))
        vm.onChoose(sampleBackgrounds.items[1])
        assertEquals("chooseBackground:CHUNCHEON", avatars.calls.last())
        assertEquals("CHUNCHEON", vm.state.value.selectedKey)
        assertEquals(UiText.Res(R.string.avatar_background_chosen), vm.state.value.message)
        assertNull(vm.state.value.choosingKey)
    }

    @Test
    fun `잠긴 배경은 보내지 않고 남은 거리(올림 km)를 안내한다(FR-201, W8)`() {
        val vm = AvatarBackgroundViewModel(avatars, storeOf(avatars))
        vm.onChoose(sampleBackgrounds.items[2])
        assertEquals(listOf("backgrounds"), avatars.calls)
        assertEquals(UiText.Res(R.string.avatar_background_locked_hint, listOf("베이징", 830L)), vm.state.value.message)
        assertNull(vm.state.value.selectedKey)
    }

    @Test
    fun `서버가 잠김으로 거부하면 고른 배경을 바꾸지 않고 사유를 보인다`() {
        avatars.chooseResult = Outcome.Failure(AppError.Rejected("BACKGROUND_LOCKED"))
        val vm = AvatarBackgroundViewModel(avatars, storeOf(avatars))
        vm.onChoose(sampleBackgrounds.items[0])
        assertNull(vm.state.value.selectedKey)
        assertEquals(UiText.Res(R.string.avatar_error_background_locked), vm.state.value.message)
    }

    @Test
    fun `기본 배경으로 돌리면 null 키를 보낸다`() {
        avatars.backgroundsResult = Outcome.Success(sampleBackgrounds.copy(selectedKey = "INCHEON"))
        val vm = AvatarBackgroundViewModel(avatars, storeOf(avatars))
        vm.onChooseDefault()
        assertEquals("chooseBackground:null", avatars.calls.last())
        assertNull(vm.state.value.selectedKey)
    }

    @Test
    fun `목록을 못 읽으면 오류를 보이고 다시 시도한다`() {
        avatars.backgroundsResult = Outcome.Failure(AppError.Network)
        val vm = AvatarBackgroundViewModel(avatars, storeOf(avatars))
        assertNull(vm.state.value.backgrounds)
        avatars.backgroundsResult = Outcome.Success(sampleBackgrounds)
        vm.load()
        assertEquals(3, vm.state.value.backgrounds?.items?.size)
    }
}
