package com.a207.runkk.app.ui.record

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 문자열 리소스로 둔 날짜 서식(예: "M월 d일 (E)")으로 한국어 [DateTimeFormatter]를 만든다. */
@Composable
internal fun rememberDateFormatter(@StringRes pattern: Int): DateTimeFormatter {
    val text = stringResource(pattern)
    return remember(text) { DateTimeFormatter.ofPattern(text, Locale.KOREAN) }
}
