package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.AppError

/**
 * AppError → 화면 문구(core.designsystem → core.model, 5.1.2). ViewModel이 Outcome.Failure를 UiState 문구로 바꿀 때 쓴다.
 * 서버 사유 코드(409)는 10/21 API 계약의 코드 이름을 따르고, 모르는 코드는 일반 문구로 보인다.
 */
object RunkkErrorText {
    fun of(error: AppError): UiText = when (error) {
        AppError.Network -> UiText.Res(R.string.core_error_network)
        is AppError.Server -> UiText.Res(R.string.core_error_server)
        AppError.Unauthorized -> UiText.Res(R.string.core_error_unauthorized)
        AppError.Storage -> UiText.Res(R.string.core_error_storage)
        is AppError.Rejected -> rejected(error.code)
    }

    /** 사유 코드 문구. 모르는 코드면 일반 문구. */
    fun rejected(code: String): UiText = UiText.Res(REJECTED[code] ?: R.string.core_error_generic)

    /**
     * 사유 코드 → 문구. 키는 서버 common.ReasonCode 이름, 서버 오류 응답의 일반 코드(ErrorResponse: BAD_REQUEST·PERMISSION_DENIED·
     * INVALID_STATE·CONCURRENT_UPDATE), 앱 안에서 만드는 코드(위 AppError.Rejected 상수·LOGIN_CANCELLED 등)뿐이다.
     * 계약 시험(ContractEnumsTest)이 이 밖의 이름을 막는다. 대결·크루·아바타 전용 문구는 각 화면 모듈이 먼저 찾는다.
     */
    internal val REJECTED: Map<String, Int> = mapOf(
        AppError.Rejected.LOCATION_CONSENT to R.string.core_error_location_consent,
        AppError.Rejected.RUN_IN_PROGRESS to R.string.core_error_run_in_progress,
        AppError.Rejected.SIGN_UP_REQUIRED to R.string.core_error_sign_up_required,
        "NOT_FOUND" to R.string.core_error_not_found,
        "BAD_REQUEST" to R.string.core_error_bad_request,
        "PERMISSION_DENIED" to R.string.core_error_permission_denied,
        "INVALID_STATE" to R.string.core_error_invalid_state,
        "CONCURRENT_UPDATE" to R.string.core_error_concurrent_update,
        "TAG_INVALID" to R.string.core_error_tag_invalid,
        "VALUE_INVALID" to R.string.core_error_value_invalid,
        "PROVIDER_UNAVAILABLE" to R.string.core_error_provider_unavailable,
        "TEMPORARILY_UNAVAILABLE" to R.string.core_error_temporarily_unavailable,
        "PROVIDER_SDK_NOT_READY" to R.string.core_error_provider_sdk_not_ready,
        "LOGIN_CANCELLED" to R.string.core_error_login_cancelled,
        "AUTH_FAILED" to R.string.core_error_auth_failed,
        "SIGN_UP_TICKET_INVALID" to R.string.core_error_sign_up_ticket_invalid,
        "ALREADY_MEMBER" to R.string.core_error_already_member,
        "CONSENT_MISSING" to R.string.core_error_consent_missing,
        "UNDER_14" to R.string.core_error_under_14,
        "NICKNAME_INVALID" to R.string.core_error_nickname_invalid,
        "NICKNAME_TAKEN" to R.string.core_error_nickname_taken,
        "CREW_NAME_TAKEN" to R.string.core_error_crew_name_taken,
        "MATCH_FAILED" to R.string.core_error_match_failed,
        "FACE_IMAGE_NOT_FOUND" to R.string.core_error_face_image_not_found,
        "SELFIE_RETRY_LIMIT" to R.string.core_error_selfie_retry_limit,
        "TOO_MANY_ATTEMPTS" to R.string.core_error_too_many_attempts,
        "INTRO_TOO_LONG" to R.string.core_error_intro_too_long,
        "SETTING_INVALID" to R.string.core_error_setting_invalid,
        "CONSENT_ITEM_NOT_SETTING" to R.string.core_error_consent_item_not_setting,
        "LEADER_MUST_TRANSFER" to R.string.core_error_leader_must_transfer,
        "CREW_DELETE_REQUIRED" to R.string.core_error_crew_delete_required,
        "BATTLE_IN_PROGRESS" to R.string.core_error_battle_in_progress,
        "WITHDRAWAL_REJECTED" to R.string.core_error_withdrawal_rejected,
        "RECORD_NOT_FOUND" to R.string.core_error_record_not_found,
        "ROUTE_PURGED" to R.string.core_error_route_purged,
        "RECORD_PRIVATE" to R.string.core_error_record_private,
        "RECORD_CREW_ONLY" to R.string.core_error_record_crew_only,
        "GOAL_INVALID" to R.string.core_error_goal_invalid,
        "PLAN_INVALID" to R.string.core_error_plan_invalid,
        "PAST_DATE" to R.string.core_error_past_date,
        "NOTIFICATION_NOT_FOUND" to R.string.core_error_notification_not_found,
        "NO_TYPE" to R.string.core_error_no_type,
        "COURSE_MAP_NOT_SHARED" to R.string.core_error_course_map_not_shared
    )
}
