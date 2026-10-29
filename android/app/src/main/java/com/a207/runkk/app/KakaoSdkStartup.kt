package com.a207.runkk.app

import android.content.Context
import com.kakao.sdk.common.KakaoSdk

/**
 * 카카오 SDK 초기화(D-275). 카카오 로그인(ui.me)과 카카오톡 공유(ui.battle)가 함께 쓰므로 앱 시작 때 한 번 한다.
 * 앱 키는 빌드 속성 KAKAO_NATIVE_APP_KEY(저장소에 넣지 않는다)이고, 비어 있으면 초기화를 건너뛰어 로그인·공유가 각자 실패 안내를 한다.
 */
object KakaoSdkStartup {
    /** 초기화했으면 참. */
    fun init(context: Context, appKey: String): Boolean {
        if (appKey.isBlank()) return false
        KakaoSdk.init(context.applicationContext, appKey)
        return true
    }
}
