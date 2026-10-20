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

    /** 워치 → 폰: 심박 묶음(DataClient, FR-043). */
    const val HEART_RATE_BATCH = "/runkk/heart-rate-batch"
}
