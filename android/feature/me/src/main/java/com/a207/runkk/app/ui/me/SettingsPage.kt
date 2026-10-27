package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes

/** 설정 화면 종류. 같은 SettingsViewModel을 화면마다 따로 둔다. */
enum class SettingsPage(@StringRes val titleRes: Int) {
    APP(R.string.me_settings_page_app),
    FREE_RUN(R.string.me_settings_page_free_run),
    NOTIFICATIONS(R.string.me_settings_page_notifications),
    CONSENTS(R.string.me_settings_page_consents),

    /** 소셜 계정 연동(F9, FR-198). */
    ACCOUNT_LINK(R.string.me_settings_page_account_link)
}
