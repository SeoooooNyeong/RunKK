package com.a207.runkk.app.domain.notification

/**
 * 1차 배포 잠금 여부 포트(빌드 플래그 LOCK_UNRELEASED, D-251·D-339). 참이면 [GatedFeature]가 모두 닫혀 있다. 구현은
 * api.notification(빌드 설정). 화면은 이 포트 대신 core.designsystem의 ReleaseScope를 쓴다.
 */
fun interface ReleaseLock {
    fun isLocked(): Boolean
}
