package com.a207.runkk.app.core.designsystem

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.kakao.vectormap.KakaoMapSdk

/**
 * Kakao 지도 네이티브 앱 키(C-005). 앱이 플레이버별 BuildConfig 값(.local·.dev·운영 패키지마다 다른 키)을 화면 전체에 넣는다.
 * 기본값은 빈 문자열이라 화면 시험·스크린샷은 늘 Canvas 경로선을 그린다.
 */
val LocalKakaoMapKey = staticCompositionLocalOf { "" }

/** 키가 있고 지도 SDK 네이티브 라이브러리를 불러올 수 있으면 참. 아니면 Canvas 경로선을 그린다(C-005 대체). */
fun kakaoMapEnabled(key: String, hasMapLibrary: Boolean): Boolean = key.isNotBlank() && hasMapLibrary

/**
 * 이 앱에서 지도를 띄울 수 있는가. 지도 SDK는 arm 라이브러리만 있어 x86_64 APK에는 들어가지 않는다. 기기 ABI 목록은
 * arm 번역이 있는 에뮬레이터에서 arm64를 함께 내므로 믿지 않고, 라이브러리를 실제로 불러 본다.
 */
fun canShowKakaoMap(key: String): Boolean = kakaoMapEnabled(key, kakaoMapLibraryLoadable)

/** 지도 SDK를 한 번만 초기화한다. */
fun ensureKakaoMap(context: Context, appKey: String) = KakaoMapInit.ensure(context, appKey)

private const val KAKAO_MAP_LIBRARY = "K3fAndroid"

/** 지도 SDK 네이티브 라이브러리를 한 번 불러 본 결과. */
private val kakaoMapLibraryLoadable: Boolean by lazy {
    try {
        System.loadLibrary(KAKAO_MAP_LIBRARY)
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }
}

private object KakaoMapInit {
    @Volatile
    private var key: String? = null

    @Synchronized
    fun ensure(context: Context, appKey: String) {
        if (key == appKey) return
        KakaoMapSdk.init(context.applicationContext, appKey)
        key = appKey
    }
}
