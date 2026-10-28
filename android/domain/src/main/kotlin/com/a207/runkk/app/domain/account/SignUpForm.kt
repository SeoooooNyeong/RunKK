package com.a207.runkk.app.domain.account

/**
 * 가입 입력(POST /members, FR-003, D-90): 닉네임(없으면 서버가 "러너" + 숫자 4자리)과 동의. 가입 티켓은 구현이 보관한다(D-19).
 * 생년·성별·소개·공개 범위·태그 단계는 없다(D-90, D-91). 닉네임 규칙 밖 값은 서버가 고친다(D-143). consents의 키는 서버 ConsentItem 이름이다.
 */
data class SignUpForm(val nickname: String?, val consents: Map<String, Boolean>)
