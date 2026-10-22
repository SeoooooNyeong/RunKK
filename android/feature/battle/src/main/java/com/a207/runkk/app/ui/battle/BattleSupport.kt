package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 대결 상대 종류(화면 문구용). Routes.BattleRunning.opponentKind 값이다. 고스트는 랜덤으로 보인다(Q-02).
 */
enum class OpponentKind {
    RANDOM,
    SELF,
    CELEBRITY;

    companion object {
        fun of(name: String): OpponentKind = entries.firstOrNull { it.name == name } ?: RANDOM
    }
}

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

/** 거리 라벨: 21,097m → "하프", 42,195m → "풀", 그 밖은 "5km". */
internal fun distanceLabel(distance: Distance): UiText = when (distance.meters) {
    in HALF_MIN..HALF_MAX -> UiText.Res(R.string.battle_distance_half)
    in FULL_MIN..FULL_MAX -> UiText.Res(R.string.battle_distance_full)
    else -> UiText.Plain(RunkkFormat.targetLabel(distance))
}

private const val HALF_MIN = 21_000L
private const val HALF_MAX = 21_200L
private const val FULL_MIN = 42_000L
private const val FULL_MAX = 42_300L

/** 하프·풀 마라톤 거리(m). */
internal const val HALF_MARATHON_METERS = 21_097L
internal const val FULL_MARATHON_METERS = 42_195L
