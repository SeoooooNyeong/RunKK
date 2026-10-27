package com.a207.runkk.app.ui.me

import javax.inject.Qualifier

/**
 * 카카오 토큰 창구를 갈아 끼우는 자리(멀티바인딩 집합). 출시 코드에는 넣는 곳이 없어 늘 비어 Kakao SDK를 쓴다.
 * :app의 localDebug 소스셋만 빌드 속성 RUNKK_E2E_KAKAO_TOKEN이 있을 때 E2E 시험용 창구를 넣는다(e2e/README.md).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KakaoTokenOverride
