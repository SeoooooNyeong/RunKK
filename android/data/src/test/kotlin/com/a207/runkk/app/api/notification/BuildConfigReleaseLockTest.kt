package com.a207.runkk.app.api.notification

import com.a207.runkk.app.domain.notification.GatedFeature
import kotlin.test.Test
import kotlin.test.assertTrue

/** 시스템 푸시 잠금(D-339): 2차 배포(S15P31A207-248) 운영 빌드는 크루·아바타 꾸미기 알림을 띄운다. 서버가 공개 스위치로 푸시를 거른다. */
class BuildConfigReleaseLockTest {
    @Test
    fun `S15P31A207-248 잠금 빌드도 크루·아바타 꾸미기 알림을 연다`() {
        GatedFeature.entries.forEach { assertTrue(releaseLockOf(isLockingUnreleased = true).isOpen(it), "$it") }
    }

    @Test
    fun `잠그지 않는 빌드는 모두 연다`() {
        GatedFeature.entries.forEach { assertTrue(releaseLockOf(isLockingUnreleased = false).isOpen(it), "$it") }
    }
}
