package com.a207.runkk.app.ui.me

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/** 로그인·가입·마이 화면 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). */
class MeFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.ME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.LOGIN) { LoginRoute() }
        builder.route(Routes.SIGN_UP) { SignUpRoute(isGuest = false) }
        builder.route(Routes.GUEST_START) { SignUpRoute(isGuest = true) }
        builder.route(Routes.ME) { MeRoute() }
        builder.route(Routes.PROFILE_EDIT) { ProfileEditRoute() }
        builder.route(Routes.SETTINGS) { SettingsRoute(SettingsPage.APP) }
        builder.route(Routes.FREE_RUN_SETTINGS) { SettingsRoute(SettingsPage.FREE_RUN) }
        builder.route(Routes.NOTIFICATION_SETTINGS) { SettingsRoute(SettingsPage.NOTIFICATIONS) }
        builder.route(Routes.CONSENTS) { SettingsRoute(SettingsPage.CONSENTS) }
        builder.route(Routes.WITHDRAW) { WithdrawRoute() }
        builder.route(MeRoutes.ACCOUNT_LINK) { SettingsRoute(SettingsPage.ACCOUNT_LINK) }
        builder.addEntryProvider(Routes.MemberProfile::class) { key -> OtherProfileRoute(key.memberId) }
    }
}
