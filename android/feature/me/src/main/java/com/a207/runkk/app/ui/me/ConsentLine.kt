package com.a207.runkk.app.ui.me

import androidx.annotation.StringRes

/** 가입 동의 항목(서버 ConsentItem 이름). [legalType]은 서버 DocumentType 이름이고, 있으면 "보기"로 고지문서를 연다(FR-018). 서버에 AI 코멘트용 전송만의 문서가 없어 개인정보 처리방침을 연다. */
data class ConsentLine(
    val item: String,
    @StringRes val labelRes: Int,
    val isRequired: Boolean,
    val legalType: String? = null
)
