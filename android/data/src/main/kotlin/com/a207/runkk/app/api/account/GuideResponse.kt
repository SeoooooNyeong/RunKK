package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/**
 * GET /me/guide 응답(서버 D-244, D-277): 이 회원이 첫 사용 가이드를 끝냈는가. 가정: 계약 자료(android/contract)에 아직 없어
 * 05-08 API 표의 이름 그대로 두었다(서버 대조 필요). 값이 없으면 거짓으로 읽는다.
 */
@Serializable
internal data class GuideResponse(val seen: Boolean = false)
