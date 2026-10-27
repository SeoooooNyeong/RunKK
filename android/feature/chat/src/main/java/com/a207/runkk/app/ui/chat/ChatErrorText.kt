package com.a207.runkk.app.ui.chat

import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError

/** 채팅·신고 거부 사유 문구(D-111). 키는 서버 common.ReasonCode(F.2.3)이고, 나머지는 공용 문구다. */
internal object ChatErrorText {
    fun of(error: AppError): UiText {
        val code = (error as? AppError.Rejected)?.code
        return code?.let(REJECTED::get)?.let { UiText.Res(it) } ?: RunkkErrorText.of(error)
    }

    internal val REJECTED: Map<String, Int> = mapOf(
        "CHAT_EMPTY" to R.string.chat_error_chat_empty,
        "BODY_TOO_LONG" to R.string.chat_error_body_too_long,
        "NOT_CREW_MEMBER" to R.string.chat_error_not_crew_member,
        "CHAT_MESSAGE_NOT_FOUND" to R.string.chat_error_chat_message_not_found,
        "ALREADY_REPORTED" to R.string.chat_error_already_reported,
        "REPORT_TARGET_NOT_FOUND" to R.string.chat_error_report_target_not_found,
        "TARGET_INVALID" to R.string.chat_error_target_invalid,
        "TOO_MANY_ATTEMPTS" to R.string.chat_error_too_many_attempts
    )
}
