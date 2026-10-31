package com.a207.runkk.app.api.account

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.settings.GuideRemote
import javax.inject.Inject

/** 첫 사용 가이드 서버 값(GET·PUT /me/guide)을 구현한다(D-277, D-300). SettingsRemoteImpl과 같은 MeApi를 쓴다. */
internal class GuideRemoteImpl
@Inject
constructor(private val me: MeApi) : GuideRemote {
    override suspend fun seen(): Outcome<Boolean> = ApiErrorMapper.call { me.guide().seen }

    override suspend fun markSeen(): Outcome<Unit> = ApiErrorMapper.call { me.markGuideSeen() }
}
