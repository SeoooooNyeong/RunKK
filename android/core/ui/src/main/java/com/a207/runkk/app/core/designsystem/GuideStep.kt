package com.a207.runkk.app.core.designsystem

import androidx.annotation.StringRes

/**
 * 첫 사용 가이드 단계(D-277, FR-231). 단계마다 말풍선 문구와, 그 단계를 보이려면 열려 있어야 하는 기능([requiredFeature])이 있다.
 * 앵커는 단계 이름 그대로다: 화면 요소가 `Modifier.guideAnchor(step)`로 자기 경계를 [LocalGuideAnchors]에 적는다.
 */
enum class GuideStep(@StringRes val textRes: Int, val requiredFeature: UnreleasedFeature? = null) {
    FREE_RUN(R.string.core_guide_free_run),
    BATTLE_RUN(R.string.core_guide_battle_run),
    WEEKLY_GOAL(R.string.core_guide_weekly_goal),
    CREW(R.string.core_guide_crew, UnreleasedFeature.CREW),
    RECORD_RUN(R.string.core_guide_record_run),
    RANKING(R.string.core_guide_ranking),
    ME(R.string.core_guide_me),
    POINT(R.string.core_guide_point, UnreleasedFeature.AVATAR_CUSTOMIZE);

    companion object {
        /** [scope]에서 보이는 단계(순서 그대로). 1차 배포는 크루·포인트를 빼 6단계다(TC-9711). */
        fun visibleIn(scope: ReleaseScope): List<GuideStep> =
            entries.filter { step -> step.requiredFeature?.let(scope::isOpen) ?: true }
    }
}
