package com.a207.runkk.app.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 하단 탭 다섯 개(크루·기록런·홈·랭킹·마이, D-250, 피그마 J-0). 경로 이름과의 대응은 app이 정한다.
 * [guideStep]은 그 탭에 붙는 첫 사용 가이드 단계다(기록런 탭만 있다, [임시:D-성-301]: 새 가이드는 자유런·배틀런·기록런·내 러너 4단계다).
 */
enum class RunkkTab(@StringRes val labelRes: Int, val icon: ImageVector, val guideStep: GuideStep?) {
    CREW(R.string.core_tab_crew, RunkkIcons.Users, null),
    RECORD_RUN(R.string.core_tab_record_run, RunkkIcons.Walk, GuideStep.RECORD_RUN),
    HOME(R.string.core_tab_home, RunkkIcons.Home, null),
    RANKING(R.string.core_tab_ranking, RunkkIcons.Trophy, null),
    ME(R.string.core_tab_me, RunkkIcons.User, null)
}
