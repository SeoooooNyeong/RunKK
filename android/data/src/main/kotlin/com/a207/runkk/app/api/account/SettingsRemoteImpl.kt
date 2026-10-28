package com.a207.runkk.app.api.account

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.SettingsRemote
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import javax.inject.Inject

/** 서버 설정 원본 조회(GET /me/settings)를 구현한다(6.1.14, D-53). data.settings가 이 포트로만 원본을 받는다. */
internal class SettingsRemoteImpl
@Inject
constructor(private val me: MeApi) : SettingsRemote {
    override suspend fun fetch(): Outcome<SettingsSnapshot> = ApiErrorMapper.call { me.settings().toDomain() }
}
