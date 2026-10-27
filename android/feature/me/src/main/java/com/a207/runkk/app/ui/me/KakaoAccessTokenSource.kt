package com.a207.runkk.app.ui.me

import android.content.Context
import com.a207.runkk.app.core.model.Outcome

/** 카카오 접근 토큰을 받는 창구(Kakao SDK). 시험에서는 가짜로 바꾼다. */
fun interface KakaoAccessTokenSource {
    /** 네이티브 앱 키가 있어야 토큰을 받을 수 있는지. 키 없이 토큰을 주는 대체 창구([KakaoTokenOverride])만 false다. */
    val isNativeAppKeyRequired: Boolean get() = true

    suspend fun accessToken(context: Context, nativeAppKey: String): Outcome<String>
}
