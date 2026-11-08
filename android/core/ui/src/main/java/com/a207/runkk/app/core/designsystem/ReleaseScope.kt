package com.a207.runkk.app.core.designsystem

/**
 * 미공개 기능 숨김 판정(D-252, D-297). RunkkNavHost가 빌드 플래그 LOCK_UNRELEASED로 만든 값을 [LocalReleaseScope]로 내려 주고,
 * 화면은 `LocalReleaseScope.current.isOpen(feature)` 한 줄로 진입점을 가린다. ViewModel은 이 판정을 모른다.
 * 배포마다 연 기능만 [opened]로 넘긴다.
 */
fun interface ReleaseScope {
    fun isOpen(feature: UnreleasedFeature): Boolean

    companion object {
        /** 모든 기능을 연다(local 빌드, 미리보기·시험 기본값). */
        val ALL_OPEN: ReleaseScope = ReleaseScope { true }

        /** 1차 배포: [UnreleasedFeature] 전부 닫는다. 지금 빌드는 쓰지 않고, 닫힌 진입점 시험이 쓴다. */
        val FIRST_RELEASE: ReleaseScope = ReleaseScope { false }

        /**
         * 2차 배포(11/13, 사용자 결정 2026-11-09, S15P31A207-248): 크루·아바타 꾸미기·런바디를 연다. 카드 뒷면과 Runimal 공유는
         * 디자인을 다시 그린 뒤 따로 열어 닫아 둔다.
         */
        val SECOND_RELEASE: ReleaseScope = ReleaseScope {
            it == UnreleasedFeature.CREW || it == UnreleasedFeature.AVATAR_CUSTOMIZE || it == UnreleasedFeature.RUNBODY
        }

        /** 빌드 플래그 LOCK_UNRELEASED로 고른다. 잠그는 빌드(prod·develop)는 지금 배포 범위다. */
        fun of(isLockingUnreleased: Boolean): ReleaseScope = if (isLockingUnreleased) SECOND_RELEASE else ALL_OPEN

        /** [features]만 연다. */
        fun opened(features: Set<UnreleasedFeature>): ReleaseScope = ReleaseScope { it in features }
    }
}
