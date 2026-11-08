package com.a207.runkk.app.core.model

/**
 * 진행 알림(FR-224)이 앱을 열 때 인텐트에 싣는 값의 이름. data.device가 알림 인텐트에 넣고 app이 읽는다(S15P31A207-278).
 * 두 쪽이 함께 보는 모듈이 core.model뿐이라 여기에 둔다.
 */
object RunNoticeExtras {
    /** 대결 진행 알림의 대결 ID(Long). 앱을 열면 그 대결 진행 화면으로 돌아간다. 자유런 알림에는 없다. */
    const val BATTLE_ID = "com.a207.runkk.extra.BATTLE_ID"
}
