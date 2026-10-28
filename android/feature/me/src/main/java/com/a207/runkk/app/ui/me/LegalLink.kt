package com.a207.runkk.app.ui.me

/**
 * 고지 문서(FR-018) 응답이 밖에서 열 주소인가. https 주소만 브라우저로 연다. http 주소는 암호화 없는 연결이라 열지 않고 본문처럼
 * 대화상자에 보인다(AN5-D-15). 가입·설정 두 화면이 같은 규칙을 쓴다.
 */
internal fun legalLinkOrNull(document: String): String? = document.trim().takeIf { it.startsWith(HTTPS) }

private const val HTTPS = "https://"
