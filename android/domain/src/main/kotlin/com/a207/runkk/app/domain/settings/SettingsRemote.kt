package com.a207.runkk.app.domain.settings

import com.a207.runkk.app.core.model.Outcome

/** 서버 설정 원본 조회 포트(GET /me/settings). 구현은 api.account.SettingsRemoteImpl(D-53). */
interface SettingsRemote {
    suspend fun fetch(): Outcome<SettingsSnapshot>
}
