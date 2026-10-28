package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError

/** 대결 거부 사유 문구(서버 ReasonCode). 모르는 코드는 공용 문구(RunkkErrorText). */
internal object BattleErrorText {
    fun of(error: AppError): UiText {
        val code = (error as? AppError.Rejected)?.code
        return code?.let(REJECTED::get)?.let { UiText.Res(it) } ?: RunkkErrorText.of(error)
    }

    internal val REJECTED: Map<String, Int> = mapOf(
        "RANKED_ONLY" to R.string.battle_error_ranked_only,
        "ALREADY_IN_BATTLE" to R.string.battle_error_already_in_battle,
        "ALREADY_MATCHED" to R.string.battle_error_already_matched,
        "MATCH_FAILED" to R.string.battle_error_match_failed,
        "BATTLE_DISTANCE_INVALID" to R.string.battle_error_distance_invalid,
        "LOCATION_CONSENT_REQUIRED" to R.string.battle_error_location_consent_required,
        "QUEUE_UNAVAILABLE" to R.string.battle_error_queue_unavailable,
        "NO_RECORD" to R.string.battle_no_record_yet,
        "CELEBRITY_REMOVED" to R.string.battle_error_celebrity_removed,
        "BATTLE_NOT_FOUND" to R.string.battle_error_not_found,
        AppError.Rejected.LOCATION_CONSENT to R.string.battle_error_location_permission
    )
}
