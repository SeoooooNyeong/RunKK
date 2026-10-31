package com.a207.runkk.app.core.designsystem

/**
 * 미공개 기능 숨김 판정(D-252, D-297). RunkkNavHost가 빌드 플래그 LOCK_UNRELEASED로 만든 값을 [LocalReleaseScope]로 내려 주고,
 * 화면은 `LocalReleaseScope.current.isOpen(feature)` 한 줄로 진입점을 가린다. ViewModel은 이 판정을 모른다.
 * 2차 배포 때 기능별로 열려면 [opened]에 연 기능만 넘긴다.
 */
fun interface ReleaseScope {
    fun isOpen(feature: UnreleasedFeature): Boolean

    companion object {
        /** 모든 기능을 연다(local 빌드, 미리보기·시험 기본값). */
        val ALL_OPEN: ReleaseScope = ReleaseScope { true }

        /** 1차 배포: [UnreleasedFeature] 전부 닫는다. */
        val FIRST_RELEASE: ReleaseScope = ReleaseScope { false }

        /** 빌드 플래그 LOCK_UNRELEASED로 고른다. */
        fun of(isLockingUnreleased: Boolean): ReleaseScope = if (isLockingUnreleased) FIRST_RELEASE else ALL_OPEN

        /** [features]만 연다. */
        fun opened(features: Set<UnreleasedFeature>): ReleaseScope = ReleaseScope { it in features }
    }
}
