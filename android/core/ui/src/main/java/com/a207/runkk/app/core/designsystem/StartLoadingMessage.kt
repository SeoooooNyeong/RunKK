package com.a207.runkk.app.core.designsystem

import androidx.annotation.StringRes

/**
 * 시작 로딩 화면의 상태 문구(S15P31A207-345 2차, Codex 최종안). 고르기 규칙은 앱(startLoadingMessageOf)이, 보이는 시간은
 * [StartLoadingMessageHold]가 정한다.
 */
enum class StartLoadingMessage(@StringRes val text: Int) {
    /** 시작 직후, 받을 목록을 만드는 동안. */
    START(R.string.core_start_loading_start),

    /** 내 머리를 받는 동안. */
    MY_AVATAR_FETCH(R.string.core_start_loading_my_avatar_fetch),

    /** 내 아바타 합성·첫 판 준비 동안. */
    MY_AVATAR_PREPARE(R.string.core_start_loading_my_avatar_prepare),

    /** 랭킹 응답·머리를 받는 동안. */
    RANKING(R.string.core_start_loading_ranking),

    /** 셀럽 목록·기록을 받는 동안. */
    CELEB(R.string.core_start_loading_celeb),

    /** 그 밖의 첫 화면 데이터(기록·크루·친구·홈). */
    RECORDS_CREW_FRIENDS(R.string.core_start_loading_records_crew_friends),

    /** 90% 이상. */
    ALMOST_DONE(R.string.core_start_loading_almost_done),

    /** 연결·서버 일시 실패로 자동 재시도 중. */
    RETRYING(R.string.core_start_loading_retrying)
}
