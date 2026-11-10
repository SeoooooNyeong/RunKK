package com.a207.runkk.app.ui.crew

/** 크루 홈 안에서 덮어 여는 쪽(피그마 F-17·F-24 ⋯ 메뉴): 홈, 크루 정보(F-18·F-25), 크루원(F-22·F-29), 크루 정보 수정(F-26). */
enum class CrewHomePage {
    HOME,
    INFO,
    MEMBERS,
    EDIT;

    companion object {
        /**
         * 경로의 쪽 이름(Routes.CrewHome.page) → 쪽. 가입 신청 알림은 크루 정보(INFO)로 연다. 없거나 모르면 홈이고, 수정 쪽은 입력을
         * 채울 크루 정보가 없으므로 경로로 열지 않는다(홈).
         */
        fun of(name: String?): CrewHomePage = entries.firstOrNull { it.name == name && it != EDIT } ?: HOME
    }
}
