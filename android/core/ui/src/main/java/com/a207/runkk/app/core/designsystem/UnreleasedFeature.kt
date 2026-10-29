package com.a207.runkk.app.core.designsystem

/** 1차 배포에서 닫아 두는 기능(D-252, FR-223). 화면은 [ReleaseScope.isOpen]으로 진입점을 가린다. */
enum class UnreleasedFeature {
    /** 크루(크루 탭·랭킹 크루 분절·결과의 크루 반영 줄·프로필 크루명·가이드 크루 단계). */
    CREW,

    /** 카드 공유(카드 팝업 "공유"·뒤집기, 기록 상세 "카드 공유", MY Runimal "공유"). */
    CARD_SHARE,

    /** 아바타 꾸미기(홈 아바타 눌러 꾸미기, 상점·배경 선택 진입, 셀카 얼굴, 가이드 포인트 단계). */
    AVATAR_CUSTOMIZE
}
