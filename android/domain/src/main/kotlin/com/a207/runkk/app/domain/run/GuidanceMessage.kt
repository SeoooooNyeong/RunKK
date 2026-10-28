package com.a207.runkk.app.domain.run

/**
 * 안내 문구 키(2차 리뷰 M-3, 1차 S-5). 도메인(순수 JVM)은 문구를 만들지 않고 키와 인자만 정한다.
 * data.device.AndroidGuidanceOutput이 문자열 리소스로 바꿔 TTS·화면에 낸다. 인자 순서는 각 항목 설명대로다.
 */
enum class GuidanceMessage {
    /** 짧게 누른 종료: 길게 눌러 끝내라는 안내. */
    FINISH_HINT,

    /** 잠금 중 누름: 길게 눌러 해제하라는 안내. */
    UNLOCK_HINT,

    /** 1km 지점. 인자: km. */
    SPLIT,

    /** 목표 거리 도달. 인자: km(소수). */
    TARGET_REACHED,

    /** 목표 페이스보다 10% 넘게 느림. */
    PACE_SLOW,

    /** 심박이 높음(의료 판단 아님). */
    BREATH_HIGH,

    /** 대결 격차: 앞섬. 인자: 격차 m. */
    GAP_AHEAD,

    /** 대결 격차: 뒤처짐. 인자: 격차 m(양수). */
    GAP_BEHIND,

    /** 대결 격차: 앞섬 + 남은 거리. 인자: 격차 m, 남은 m. */
    GAP_AHEAD_REMAINING,

    /** 대결 격차: 뒤처짐 + 남은 거리. 인자: 격차 m(양수), 남은 m. */
    GAP_BEHIND_REMAINING,

    /** 크루런 순위. 인자: 참가자 수, 순위. */
    RANK,

    /** 추월. 인자: 앞선 m. */
    OVERTOOK,

    /** 역전당함. */
    OVERTAKEN,

    /** 기기 보관함 저장 실패. */
    SAVE_FAILED,

    /** 대결 상태를 서버에 알리지 못함. */
    REPORT_FAILED,

    /** 일시정지했다(FR-194). */
    PAUSED,

    /** 다시 측정한다(FR-194). */
    RESUMED,

    /** 정지가 55분이 되어 5분 뒤 자동 종료된다(FR-195). */
    PAUSE_AUTO_END_SOON,

    /** 정지가 60분이 되어 자동 종료했다(FR-195). */
    PAUSE_AUTO_ENDED
}
