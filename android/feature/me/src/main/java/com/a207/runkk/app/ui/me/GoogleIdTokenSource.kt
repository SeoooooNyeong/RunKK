package com.a207.runkk.app.ui.me

import android.content.Context
import com.a207.runkk.app.core.model.Outcome

/** 구글 ID 토큰을 받는 창구(Credential Manager). 시험에서는 가짜로 바꾼다. */
fun interface GoogleIdTokenSource {
    suspend fun idToken(context: Context, webClientId: String): Outcome<String>
}
