package com.a207.runkk.app.ui.record.runbody

/**
 * 런바디 경로(Routes.REPORT) 안의 쪽: 탭 화면, 러닝 선택(G-30), 런바디 상세(G-20). 1차 배포 잠금(BetaLock)이 Routes.REPORT 하나로
 * 런바디를 막으므로 새 경로를 만들지 않고 한 경로 안에서 쪽을 바꾼다. 뒤로는 탭 화면으로 돌아온다.
 */
enum class RunBodyPage {
    MAIN,
    PICK,
    DETAIL
}
