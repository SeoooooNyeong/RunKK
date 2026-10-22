package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance

/** 서비스 JWT(접근 1시간, 갱신 14일, D-19). */
data class SessionTokens(val access: String, val refresh: String) {
    override fun toString(): String = "SessionTokens(***)"
}
