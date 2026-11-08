package com.a207.runkk.app.domain.notification

/**
 * 미공개 기능 잠금 포트(빌드 플래그 LOCK_UNRELEASED, D-251·D-339): [GatedFeature]마다 지금 빌드에서 열려 있는지 답한다. 구현은
 * api.notification(빌드 설정). 화면은 이 포트 대신 core.designsystem의 ReleaseScope를 쓴다.
 */
fun interface ReleaseLock {
    fun isOpen(feature: GatedFeature): Boolean
}
