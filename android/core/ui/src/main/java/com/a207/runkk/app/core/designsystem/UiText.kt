package com.a207.runkk.app.core.designsystem

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * ViewModel이 화면에 넘기는 문구. 문자열 리소스([Res])나 서버가 준 그대로의 문구([Plain])를 담아 두고
 * 화면에서 [asString]으로 푼다(ViewModel은 Context를 알지 않는다).
 */
sealed interface UiText {
    /** 문자열 리소스. [args]는 서식(%1$s·%1$d) 인자이고, [UiText]가 들어 있으면 먼저 풀어서 넣는다. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** 서버·사용자가 만든 문구(닉네임·서버 메시지)처럼 번역 대상이 아닌 그대로의 문구. */
    data class Plain(val value: String) : UiText

    fun asString(context: Context): String = when (this) {
        is Plain -> value
        is Res -> context.getString(id, *args.map { if (it is UiText) it.asString(context) else it }.toTypedArray())
    }
}

/** 화면에서 문구를 푼다. */
@Composable
fun UiText.asString(): String = asString(LocalContext.current)

/** 문자열 리소스 문구를 인자와 함께 만든다: `uiText(R.string.xxx, count)`. */
fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())
