package com.a207.runkk.app.core.designsystem

import androidx.annotation.StringRes

/**
 * 첫 사용 가이드 단계([임시:FR-성-35], [임시:D-성-294], D-277). 시작 카드 뒤에 차례로 보이는 4단계다: 자유런, 배틀런, 기록런, 내 러너.
 * 단계마다 말풍선 제목([titleRes])과 설명([textRes]), 그 단계를 보이려면 열려 있어야 하는 기능([requiredFeature])이 있다.
 * 앵커는 단계 이름 그대로다: 화면 요소가 `Modifier.guideAnchor(step)`로 자기 경계를 [LocalGuideAnchors]에 적는다.
 */
enum class GuideStep(
    @StringRes val titleRes: Int,
    @StringRes val textRes: Int,
    val requiredFeature: UnreleasedFeature? = null
) {
    FREE_RUN(R.string.core_guide_free_run_title, R.string.core_guide_free_run),
    BATTLE_RUN(R.string.core_guide_battle_run_title, R.string.core_guide_battle_run),
    RECORD_RUN(R.string.core_guide_record_run_title, R.string.core_guide_record_run),
    MY_RUNNER(R.string.core_guide_my_runner_title, R.string.core_guide_my_runner, UnreleasedFeature.AVATAR_CUSTOMIZE);

    companion object {
        /**
         * [scope]에서 보이는 단계(순서 그대로). 닫힌 기능의 단계는 빼고 번호를 다시 센다: 아바타 꾸미기가 닫힌 1차 배포는 내 러너를 뺀
         * 3단계, 열린 배포는 4단계다.
         */
        fun visibleIn(scope: ReleaseScope): List<GuideStep> =
            entries.filter { step -> step.requiredFeature?.let(scope::isOpen) ?: true }
    }
}
