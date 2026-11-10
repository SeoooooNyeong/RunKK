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

    /**
     * 셀카 접수·조회 실패. 서버 사유 코드는 [of]와 같고, 얼굴 서버가 못 받은 접수(503 UPSTREAM_FAILED·504 TIMEOUT)는 셀카 문구로 보인다.
     */
    fun ofSelfie(error: AppError): UiText = when {
        error == AppError.Server(SERVICE_UNAVAILABLE) -> UiText.Res(R.string.avatar_error_upstream_failed)
        error == AppError.Server(GATEWAY_TIMEOUT) -> UiText.Res(R.string.avatar_error_timeout)
        error is AppError.Rejected && error.code in SELFIE_JOB -> UiText.Res(SELFIE_JOB.getValue(error.code))
        else -> of(error)
    }

    /** 얼굴 작업 실패 사유(서버 FaceJob error.code)의 문구. 모르는 사유는 "얼굴을 만들지 못했어요"다. */
    fun ofSelfieFailure(code: String?): UiText = UiText.Res(
        code?.let { SELFIE_JOB[it] ?: REJECTED[it] } ?: R.string.avatar_error_selfie_failed
    )

    /**
     * 얼굴 작업 사유(서버 ReasonCode가 아니다): 작업 상태 200의 error.code(avatar-ai INTEGRATION.md의 Node 사유)와 작업 조회 거부
     * (AiServerFaceJobGateway의 reason). 목록에 없는 실패 사유(FACE_ASSETS_INCOMPLETE·STORAGE_FAILED·GENERATION_* 등)는 공용 문구다.
     */
    internal val SELFIE_JOB: Map<String, Int> = mapOf(
        "SELFIE_QUALITY_REJECTED" to R.string.avatar_error_no_face,
        "NO_FACE_DETECTED" to R.string.avatar_error_no_face,
        "UPSTREAM_TIMEOUT" to R.string.avatar_error_timeout,
        "JOB_NOT_FOUND" to R.string.avatar_error_selfie_failed,
        "JOB_NOT_READY" to R.string.avatar_error_selfie_failed
    )

    private const val SERVICE_UNAVAILABLE = 503
    private const val GATEWAY_TIMEOUT = 504

    /** 착용 거부 사유(Avatar.rejected 값, 서버 ReasonCode 이름)의 문구. 모르는 코드는 공용 문구다. */
    fun ofCode(code: String): UiText = REJECTED[code]?.let { UiText.Res(it) } ?: RunkkErrorText.rejected(code)

    internal val REJECTED: Map<String, Int> = mapOf(
        // 셀카 변환 실패 사유(서버 avatar.FailureReason)와 셀카 접수 거부(413 TOO_LARGE·415 UNSUPPORTED_IMAGE reason)
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
        "ITEM_LOCKED" to R.string.avatar_error_item_locked,
        "ALREADY_OWNED" to R.string.avatar_error_already_owned,
        "INSUFFICIENT_REWARD" to R.string.avatar_error_insufficient_reward,
        // 해금하지 않은 배경(FR-201)
        "BACKGROUND_LOCKED" to R.string.avatar_error_background_locked
    )
}
