package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.domain.avatar.Avatar

/** 아바타·상점 거부 사유 문구(서버 ReasonCode). 모르는 코드는 공용 문구. */
internal object AvatarErrorText {
    fun of(error: AppError): UiText {
        val code = (error as? AppError.Rejected)?.code
        return code?.let(REJECTED::get)?.let { UiText.Res(it) } ?: RunkkErrorText.of(error)
    }

    /** 착용 거부 사유(Avatar.rejected 값, 서버 ReasonCode 이름)의 문구. 모르는 코드는 공용 문구다. */
    fun ofCode(code: String): UiText = REJECTED[code]?.let { UiText.Res(it) } ?: RunkkErrorText.rejected(code)

    internal val REJECTED: Map<String, Int> = mapOf(
        // 셀카 변환 실패(서버 avatar.FailureReason, 200 + created=false라 앱이 Rejected로 바꾼다)
        "NO_FACE" to R.string.avatar_error_no_face,
        "MULTIPLE_FACES" to R.string.avatar_error_multiple_faces,
        "UNSUPPORTED_IMAGE" to R.string.avatar_error_unsupported_image,
        "TOO_LARGE" to R.string.avatar_error_too_large,
        "UPSTREAM_FAILED" to R.string.avatar_error_upstream_failed,
        "TIMEOUT" to R.string.avatar_error_timeout,
        "SELFIE_FAILED" to R.string.avatar_error_selfie_failed,
        "SELFIE_LIMIT" to R.string.avatar_error_selfie_limit,
        "SELFIE_DISABLED" to R.string.avatar_error_selfie_disabled,
        "FACE_CONSENT_REQUIRED" to R.string.avatar_error_face_consent_required,
        "SELFIE_RETRY_LIMIT" to R.string.avatar_error_selfie_retry_limit,
        "FACE_IMAGE_NOT_FOUND" to R.string.avatar_error_face_image_not_found,
        "SELFIE_IN_PROGRESS" to R.string.avatar_error_selfie_in_progress,
        "ITEM_NOT_OWNED" to R.string.avatar_error_item_not_owned,
        "ITEM_NOT_FOUND" to R.string.avatar_error_item_not_found,
        "ITEM_NOT_ON_SALE" to R.string.avatar_error_item_not_on_sale,
        "ALREADY_OWNED" to R.string.avatar_error_already_owned,
        "INSUFFICIENT_REWARD" to R.string.avatar_error_insufficient_reward,
        // 해금하지 않은 배경(FR-201)
        "BACKGROUND_LOCKED" to R.string.avatar_error_background_locked
    )
}
