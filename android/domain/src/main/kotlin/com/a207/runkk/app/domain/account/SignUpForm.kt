package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance

/**
 * 가입 입력(서버 member.SignUpForm과 같은 항목, POST /members). 검증 규칙(6.3 1.2 의사결정표)은 서버가 판정하고
 * 거부 사유는 AppError.Rejected(사유 코드)로 온다. consents의 키는 서버 ConsentItem 상수 이름이다.
 */
data class SignUpForm(
    val nickname: String?,
    val birthYear: Int?,
    val gender: Gender,
    val intro: String?,
    val consents: Map<String, Boolean>,
    val recordVisibility: String,
    val tags: Set<String>
)
