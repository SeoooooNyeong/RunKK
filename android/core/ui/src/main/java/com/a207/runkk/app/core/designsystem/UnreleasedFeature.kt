package com.a207.runkk.app.core.designsystem

/**
 * 배포 범위로 여닫는 기능(D-252, FR-223). 화면은 [ReleaseScope.isOpen]으로 진입점을 가린다. 2차 배포(S15P31A207-248)는
 * 크루·아바타 꾸미기·런바디를 열고 카드 뒷면·Runimal 공유를 닫아 둔다([ReleaseScope.SECOND_RELEASE]).
 * 기록 카드 앞면 공유(카드 팝업 "공유", 기록 상세 "카드 공유")는 1차 배포에서 연다(D93). 크루 축하 카드 공유는 크루 화면 안에 있어 [CREW]로 닫힌다.
 */
enum class UnreleasedFeature {
    /** 크루(크루 탭·랭킹 크루 분절·결과의 크루 반영 줄·프로필 크루명·가이드 크루 단계). */
    CREW,

    /** 기록 카드 뒷면(카드 팝업 뒤집기·면 점, 공유 단계 "코스 지도 표시"). */
    CARD_BACK,

    /** Runimal 카드 공유(MY Runimal "공유"). */
    RUNIMAL_SHARE,

    /** 아바타 꾸미기(홈 아바타 눌러 꾸미기, 상점·배경 선택 진입, 셀카 얼굴, 가이드 포인트 단계). */
    AVATAR_CUSTOMIZE,

    /** 런바디(마이 메뉴 "런바디", 리포트 화면). 2차 배포에서 연다(사용자 결정 2026-10-31). */
    RUNBODY
}
