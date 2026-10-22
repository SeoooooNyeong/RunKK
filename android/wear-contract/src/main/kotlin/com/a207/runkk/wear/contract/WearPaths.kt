package com.a207.runkk.wear.contract

/**
 * 폰–워치 Wearable Data Layer 경로 이름(D-20, 6.1.15).
 * 시작 요청·표시 정보는 MessageClient, 심박 묶음은 DataClient(끊겨도 쌓였다가 전달, NFR-032)로 보낸다.
 */
object WearPaths {
    /** 워치 → 폰: 자유런 시작 요청(MessageClient, FR-183). */
    const val START_REQUEST = "/runkk/start-request"

    /** 폰 → 워치: 진행 지표·오늘 계획·수집 허용 여부(MessageClient, FR-042). */
    const val WATCH_DISPLAY = "/runkk/watch-display"

    /**
     * 워치 → 폰: 심박 묶음(DataClient, FR-043). 워치는 묶음마다 `HEART_RATE_BATCH/{첫 measuredAt}-{순번}` 경로에 넣고,
     * 폰은 이 접두어로 받아 처리한 뒤 항목을 지운다.
     */
    const val HEART_RATE_BATCH = "/runkk/heart-rate-batch"

    /**
     * 런크크 워치 앱이 알리는 능력 이름(CapabilityClient). 워치 앱 res/values/wear.xml의 android_wear_capabilities와 같아야 한다.
     * 폰은 이 능력이 닿는 노드가 바뀔 때만 연결 상태를 갱신한다(5초마다 노드를 조회하지 않는다).
     */
    const val WATCH_CAPABILITY = "runkk_watch"

    /** 워치 → 폰: 러닝 종료 요청(MessageClient). 워치의 종료는 길게 누르기이고 폰이 같은 종료 규칙으로 처리한다(FR-181). */
    const val END_REQUEST = "/runkk/end-request"
}
