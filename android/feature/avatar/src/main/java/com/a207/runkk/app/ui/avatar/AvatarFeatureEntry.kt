package com.a207.runkk.app.ui.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/**
 * 아바타 만들기·꾸미기·상점 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). 홈·러닝 중 화면의 아바타 칸을 채우고
 * (design-oo 05-30 [임시:D-성-100]), 랭킹 시상대·기록런 VS 칸의 전신 정지 그림을 넣고(S15P31A207-300), 로그인한 동안 내 아바타를
 * 미리 준비해 둔다([임시:D-성-104]).
 */
class AvatarFeatureEntry @Inject constructor(private val store: MyAvatarStore) : FeatureEntry {
    override val route: String = Routes.AVATAR

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.AVATAR) { AvatarRoute() }
        builder.route(Routes.AVATAR_RUNNER) { AvatarRunnerRoute() }
        builder.route(Routes.AVATAR_SETUP) { AvatarSetupRoute() }
        builder.route(Routes.SHOP) { ShopRoute() }
        builder.route(Routes.AVATAR_BACKGROUND) { AvatarRoute(startOnBackground = true) }
    }

    /** 칸 내용은 한 번 만들어 둔다(다시 그릴 때마다 새 람다면 칸을 가진 화면이 칸 내용을 새로 만들 수 있다). */
    override val slots: Map<SlotKey, @Composable () -> Unit> = mapOf(
        SlotKey.HOME_AVATAR to { HomeAvatarSlotContent(store) },
        SlotKey.RUN_AVATAR to { RunAvatarSlotContent(store) },
        SlotKey.AVATAR_STILL to { AvatarStillSlotContent(store) }
    )

    /**
     * 미리 준비([임시:D-성-104]): RunkkNavHost는 로그인한 동안 Overlay를 늘 그리므로 로그인된 앱 시작과 로그인·가입 완료 뒤 한 번씩
     * 데운다. 로그아웃 때 비우기는 저장소가 세션 상태로 한다(앞 회원 그림을 다음 회원에게 보이지 않는다). 그리는 것은 없다.
     */
    @Composable
    override fun Overlay(top: NavKey?) {
        // 빠질 때(회전·구성 변경, 로그아웃) 비우지 않는다. 로그아웃은 저장소가 세션 상태를 보고 비운다(리뷰 R2-7, [임시:D-성-161]).
        LaunchedEffect(store) { store.warm() }
    }
}
