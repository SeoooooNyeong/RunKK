package com.a207.runkk.app.api.notification

import com.a207.runkk.app.api.core.BuildConfig
import com.a207.runkk.app.domain.notification.GatedFeature
import com.a207.runkk.app.domain.notification.ReleaseLock
import javax.inject.Inject

/** 빌드 플래그 LOCK_UNRELEASED(앱과 같은 규칙: local 플레이버만 거짓, -Prunkk.lockUnreleased가 앞선다)를 읽는다(D-339). */
internal class BuildConfigReleaseLock @Inject constructor() : ReleaseLock by releaseLockOf(BuildConfig.LOCK_UNRELEASED)

/**
 * 잠그는 빌드는 지금 배포 범위에서 연 기능만 연다. 2차 배포(S15P31A207-248)는 크루·아바타 꾸미기를 열어 [GatedFeature] 모두가 열린다
 * (앱 ReleaseScope.SECOND_RELEASE와 같다). 푸시를 띄울지는 서버 공개 스위치(CREW_RELEASED·AVATAR_RELEASED)가 먼저 거른다.
 */
internal fun releaseLockOf(isLockingUnreleased: Boolean): ReleaseLock =
    if (isLockingUnreleased) ReleaseLock { it in SECOND_RELEASE } else ReleaseLock { true }

private val SECOND_RELEASE = setOf(GatedFeature.CREW, GatedFeature.AVATAR_CUSTOMIZE)
