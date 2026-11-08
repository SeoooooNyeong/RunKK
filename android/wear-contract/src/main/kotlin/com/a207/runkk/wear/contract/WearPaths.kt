package com.a207.runkk.wear.contract

/**
 * 폰–워치 Wearable Data Layer 경로 이름(D-20, 6.1.15, F.4.1). 경로는 더하기만 한다(D-97).
 * 심박 묶음, 로그아웃 표시, 거부 기록 다시 보내기 요청, 워치 거부 기록 건수는 DataClient(끊겨도 쌓였다가 전달, NFR-032, D-371, W43)이고
 * 나머지는 MessageClient다([DATA_ITEM_PATHS]). 경로 14개는 서로 겹치지 않는다(계약 시험). 받는 쪽은 모르는 경로를 버린다(F.4.4).
 */
object WearPaths {
    /** 워치 → 폰: 자유런 시작 요청(MessageClient, FR-183). */
    const val START_REQUEST = "/runkk/start-request"

    /** 폰 → 워치: 진행 지표·오늘 계획·수집 허용 여부·일시정지 상태(MessageClient, FR-042, FR-194). */
    const val WATCH_DISPLAY = "/runkk/watch-display"

    /**
     * 워치 → 폰: 심박 묶음(DataClient, FR-043). 워치는 묶음마다 `HEART_RATE_BATCH/{첫 measuredAt}-{순번}` 경로에 넣고,
     * 폰은 이 접두어로 받아 처리한 뒤 항목을 지운다.
     */
    const val HEART_RATE_BATCH = "/runkk/heart-rate-batch"

    /**
     * 런크크 워치 앱이 알리는 능력 이름(CapabilityClient). 워치 앱 res/values/wear.xml의 android_wear_capabilities와 같아야 한다.
     * 폰은 이 능력이 닿는 노드가 바뀔 때만 연결 상태를 갱신한다(5초마다 노드를 조회하지 않는다, CR34-16).
     */
    const val WATCH_CAPABILITY = "runkk_watch"

    /** 워치 → 폰: 러닝 종료 요청(MessageClient). 워치의 종료는 길게 누르기이고 폰이 같은 종료 규칙으로 처리한다(FR-181). */
    const val END_REQUEST = "/runkk/end-request"

    /** 워치 → 폰: 자유런 일시정지·재개 요청(MessageClient, PauseRequestMessage, FR-194). */
    const val PAUSE_REQUEST = "/runkk/pause-request"

    /** 폰 → 워치: 대결 근접 알림 간격(MessageClient, 유실 허용, ProximityMessage, FR-196). */
    const val PROXIMITY = "/runkk/proximity"

    /** 폰 → 워치: 추월·역전 순간(MessageClient, LeadChangeMessage, FR-188, FR-196). */
    const val LEAD_CHANGE = "/runkk/lead-change"

    /** 워치 → 폰: 로그인 정보 요청(MessageClient, 본문 없음, FR-242, D-371). 워치가 로그인 정보 없이 열렸을 때 보낸다. */
    const val SESSION_REQUEST = "/runkk/session-request"

    /** 폰 → 워치: 로그인 정보(MessageClient만, WatchSessionMessage, FR-240, D-371). 토큰을 실으므로 DataClient로 보내지 않는다(NFR-050). */
    const val WATCH_SESSION = "/runkk/watch-session"

    /** 폰 → 워치: 로그아웃 표시(DataClient 항목, SessionRevokedMessage, FR-241, D-371). 비밀값은 없다. */
    const val SESSION_REVOKED = "/runkk/session-revoked"

    /** 워치 → 폰: 워치 측정 중 여부(MessageClient, WatchMeasuringMessage, FR-252, D-371, D-373). */
    const val WATCH_MEASURING = "/runkk/watch-measuring"

    /**
     * 폰 → 워치: 거부 기록 다시 보내기 요청(DataClient 항목, ResendRejectedMessage, W40, W43). 워치가 연결되지 않았을 때 누른 요청도
     * 남았다가 연결되면 전달된다. 요청 시각이 매번 달라 같은 경로에 넣어도 바뀐 항목으로 간다. 모르는 옛 워치는 버린다(D-97).
     */
    const val RESEND_REJECTED = "/runkk/resend-rejected"

    /**
     * 워치 → 폰: 워치의 거부 기록(REJECTED) 건수(DataClient 항목, RejectedCountMessage, W43). 건수가 바뀔 때마다 같은 경로에 넣는다.
     * 폰은 설정의 못 올린 기록 건수에 더한다(워치가 연결되어 있고 워치 연동이 켜져 있을 때만).
     */
    const val REJECTED_COUNT = "/runkk/rejected-count"

    /**
     * 워치 → 폰: 지금 표시 정보 요청(MessageClient, 본문 없음, S15P31A207-278). 워치 앱이 시작될 때와 화면이 다시 보일 때 보낸다.
     * 폰→워치 표시는 일회성 메시지라 워치 앱이 꺼져 있던 동안 보낸 것(측정 중 진행값, 대기 화면, 종료 표시)은 잃는다. 폰은 이 요청을 받으면
     * 마지막 표시(없으면 대기 화면)를 다시 보내고, 보낸 워치를 닿는 노드로 등록한다. 폰 앱이 꺼져 있어도 매니페스트 수신 서비스가 받는다.
     * 비밀값은 싣지 않는다(로그인 정보는 SESSION_REQUEST 경로).
     */
    const val DISPLAY_REQUEST = "/runkk/display-request"

    /**
     * DataClient 항목으로 주고받는 경로(접두어). 항목은 Data Layer 저장소에 남으므로 비밀값을 싣는 경로(WATCH_SESSION)는 여기 두지 않는다
     * (NFR-050, 계약 시험). 그 밖의 경로는 MessageClient다.
     */
    val DATA_ITEM_PATHS: Set<String> = setOf(HEART_RATE_BATCH, SESSION_REVOKED, RESEND_REJECTED, REJECTED_COUNT)
}
