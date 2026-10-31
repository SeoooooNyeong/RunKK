package com.a207.runkk.app.api.notification

import com.a207.runkk.app.api.core.BuildConfig
import com.a207.runkk.app.domain.notification.ReleaseLock
import javax.inject.Inject

/** 빌드 플래그 LOCK_UNRELEASED(앱과 같은 규칙: local 플레이버만 거짓, -Prunkk.lockUnreleased가 앞선다)를 읽는다(D-339). */
internal class BuildConfigReleaseLock @Inject constructor() : ReleaseLock {
    override fun isLocked(): Boolean = BuildConfig.LOCK_UNRELEASED
}
