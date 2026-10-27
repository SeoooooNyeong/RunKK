package com.a207.runkk.app.ui.me

import com.a207.runkk.app.domain.account.MemberSettings

/** 동의 항목 키(서버 ConsentItem 이름). 동의 주기·철회(PUT·DELETE /me/consents/{item})와 MemberSettings.consents의 키다. */
object ConsentKeys {
    const val LOCATION = "LOCATION"
    const val HEART_RATE = "HEART_RATE"
    const val MARKETING = "MARKETING"
    const val LLM_TRANSFER = "LLM_TRANSFER"
    const val FACE_TRANSFER = "FACE_TRANSFER"
}
