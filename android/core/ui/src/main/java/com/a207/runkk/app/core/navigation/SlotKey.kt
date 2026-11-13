package com.a207.runkk.app.core.navigation

/** 화면 모듈끼리 의존하지 않고 한 화면에 다른 기능이 그림을 넣는 칸의 이름(D-255, D-298). */
enum class SlotKey {
    /** 기록 화면(ui.record)의 "대결" 탭. ui.battle이 대결 이력 탭을 넣는다(D-281). */
    RECORD_BATTLES,

    /** 홈 상단 알림함 단추 오른쪽 위 점. ui.notification이 안 본 진행 중 이벤트가 있을 때 라임 점을 넣는다(이벤트 계약 7절). */
    HOME_INBOX_DOT,

    /**
     * 크루 홈(ui.crew)의 세 번째 「채팅」 탭 내용([임시:D-성-21]). ui.chat이 채팅 탭(ChatTabContent)을 넣는다. 크루 ID는
     * [LocalSlotCrewId]로 받는다. 화면 모듈끼리 import하지 않아 :feature:crew → :feature:chat 의존과 Fan-Out 증가가 없다.
     */
    CREW_CHAT_TAB,

    /**
     * 홈 아바타 방의 내 Rive 아바타(design-oo 05-30 [임시:D-성-100], AV-1). ui.avatar가 바닥선·그림자·로딩 표시와 함께 서 있는 그림을
     * 넣는다. 칸 크기(390×420)와 누름(내 러너로)은 홈이 정한다. 칸이 비면 홈은 지금 그림(정적 아바타)을 그린다([임시:D-성-102]).
     */
    HOME_AVATAR,

    /**
     * 측정 화면(자유런·크루런·대결) 지도 위 내 위치 마커의 Rive 아바타([임시:D-성-121], AV-3). 동작은 [LocalSlotAvatarPose], 진행 방향
     * 원값은 [LocalSlotAvatarFacingLeft]로 받는다. 칸이 비면 측정 화면은 지금 그림(RunkkRunningFigure)을 그린다. [RunAvatarSlot] 참고.
     */
    RUN_AVATAR,

    /**
     * 전신 정지 그림(랭킹 TOP3 시상대, 기록런 나 VS 과거의 나, S15P31A207-300). ui.avatar가 서기 자세를 한 번 그려 읽은 그림을 넣는다.
     * 누구를 그릴지는 [LocalSlotAvatarStill]로 받고, 칸 크기는 부르는 쪽이 정한다. 칸이 비거나 아바타 꾸미기가 닫혀 있으면 부르는
     * 쪽은 지금 그림을 그린다. [AvatarStillSlot] 참고.
     */
    AVATAR_STILL
}
