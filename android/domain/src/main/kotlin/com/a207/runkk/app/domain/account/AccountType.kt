package com.a207.runkk.app.domain.account

/** 계정 종류(서버 member.AccountType, FR-197, F.1.6): 게스트 / 소셜 계정 연동 회원. */
enum class AccountType {
    GUEST,
    LINKED
}
