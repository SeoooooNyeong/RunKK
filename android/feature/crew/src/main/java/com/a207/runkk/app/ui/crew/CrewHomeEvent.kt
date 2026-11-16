package com.a207.runkk.app.ui.crew

sealed interface CrewHomeEvent {
    /** 크루를 나왔다: 크루 탭(찾기)으로. */
    data object Left : CrewHomeEvent

    /** 초대 창에서 카카오톡을 열지 못했다([임시:FR-성-37]): 「카카오톡을 열 수 없어요」를 띄우고 창은 그대로 둔다. */
    data object InviteKakaoFailed : CrewHomeEvent

    /** 초대 창에서 공유 화면을 열지 못했다(공유할 앱 없음): 안내를 띄우고 창은 그대로 둔다. */
    data object InviteShareFailed : CrewHomeEvent
}
