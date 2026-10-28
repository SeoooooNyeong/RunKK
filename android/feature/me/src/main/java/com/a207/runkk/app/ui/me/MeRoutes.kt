package com.a207.runkk.app.ui.me

/** ui.me 안에서만 오가는 경로(다른 모듈이 열지 않는 화면). 공용 경로는 core.navigation.Routes에 있다. */
internal object MeRoutes {
    /** 소셜 계정 연동(F9, FR-198): 게스트 안내·설정·로그아웃 확인에서 온다. */
    const val ACCOUNT_LINK = "me/settings/account"
}
