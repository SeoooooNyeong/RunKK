package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/**
 * POST /members 요청(서버 SignUpController.SignUpRequest, D-90). 가입 티켓·닉네임·동의만 보낸다(출생연도·성별·사진·소개·공개 범위·
 * 태그는 v0.11에서 지웠다). consents 키는 서버 ConsentItem 이름이다.
 */
@Serializable
internal data class SignUpRequest(val signUpTicket: String, val nickname: String?, val consents: Map<String, Boolean>)
