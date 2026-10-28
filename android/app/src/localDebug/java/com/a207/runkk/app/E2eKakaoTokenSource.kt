package com.a207.runkk.app

import android.content.Context
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.ui.me.KakaoAccessTokenSource
import com.a207.runkk.app.ui.me.KakaoTokenOverride

/**
 * E2E 시험 전용 카카오 토큰 창구(local 플레이버 + debug 빌드의 src/localDebug에만 있다, e2e/README.md).
 * Kakao SDK 대신 빌드 속성 RUNKK_E2E_KAKAO_TOKEN 값을 그대로 돌려준다. 서버는 이 토큰을 KAKAO_API_BASE_URL(시험 때는 카카오
 * 스텁 e2e/kakao_stub.py)에 물어 실제 검증 절차(회원 ID·앱 ID 확인)를 그대로 거친다. 서버 쪽 인증 우회는 없다.
 * app 패키지에 두고 ui.me의 창구 자리(KakaoAccessTokenSource·KakaoTokenOverride)를 명시해 import한다(리뷰 N-3). 5.1.2 표 밖의
 * app → ui.me import는 이 localDebug 파일과 E2eLoginModule 두 곳만 ArchitectureTest가 예외로 둔다.
 */
internal class E2eKakaoTokenSource(private val token: String) : KakaoAccessTokenSource {
    override val isNativeAppKeyRequired: Boolean = false

    override suspend fun accessToken(context: Context, nativeAppKey: String): Outcome<String> = Outcome.Success(token)
}

internal fun e2eKakaoTokenSources(token: String): Set<KakaoAccessTokenSource> =
    token.trim().let { if (it.isEmpty()) emptySet() else setOf(E2eKakaoTokenSource(it)) }
