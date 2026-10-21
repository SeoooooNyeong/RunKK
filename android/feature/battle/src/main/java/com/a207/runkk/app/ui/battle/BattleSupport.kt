package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkFormat
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
    fun of(error: AppError): String {
        val code = (error as? AppError.Rejected)?.code
        return code?.let(REJECTED::get) ?: RunkkErrorText.of(error)
    }

    private val REJECTED: Map<String, String> = mapOf(
        "RANKED_ONLY" to "유효 러닝 3회 뒤 랜덤 대결 가능, 나와의 싸움은 지금 가능",
        "ALREADY_IN_BATTLE" to "이미 매칭을 기다리거나 대결 중이에요",
        "ALREADY_MATCHED" to "이미 상대를 찾았어요. 곧 출발해요",
        "BATTLE_DISTANCE_INVALID" to "고를 수 없는 대결 거리예요",
        "LOCATION_CONSENT_REQUIRED" to "대결하려면 위치 수집 동의가 필요해요. 마이 > 동의 관리에서 켤 수 있어요",
        "QUEUE_UNAVAILABLE" to "잠시 후 다시 시도해 주세요",
        "NO_RECORD" to "아직 기록이 없어요",
        "CELEBRITY_REMOVED" to "고를 수 없는 기록이에요. 목록을 새로 불러왔어요",
        "BATTLE_NOT_FOUND" to "대결을 찾지 못했어요",
        AppError.Rejected.LOCATION_CONSENT to "대결하려면 위치 권한과 위치 수집 동의가 필요해요"
    )
}

/** 거리 라벨: 21,097m → "하프", 42,195m → "풀", 그 밖은 "5K". */
internal fun distanceLabel(distance: Distance): String = when (distance.meters) {
    in HALF_MIN..HALF_MAX -> "하프"
    in FULL_MIN..FULL_MAX -> "풀"
    else -> RunkkFormat.targetLabel(distance)
}

private const val HALF_MIN = 21_000L
private const val HALF_MAX = 21_200L
private const val FULL_MIN = 42_000L
private const val FULL_MAX = 42_300L

/** 하프·풀 마라톤 거리(m). */
internal const val HALF_MARATHON_METERS = 21_097L
internal const val FULL_MARATHON_METERS = 42_195L

// ---------- 통합 브랜치 domain과의 호환 ----------
// 통합 브랜치(feature/runkk-impl, 436ef01)의 BattleRunCoordinator에는 failure·acknowledgeFinished가 있다. 이 작업 트리를 만든
// 9bd7e7c의 domain에는 아직 없어 같은 이름의 확장으로 컴파일만 맞춘다. 통합하면 멤버가 이 확장보다 먼저 골라지므로(멤버 우선)
// 화면 코드는 그대로 두고 아래 두 선언만 지운다.

/** 이 회의 실패(통합 전에는 늘 null). */
internal val BattleRunCoordinator.failure: StateFlow<AppError?> get() = NO_FAILURE

/** 결과 화면이 Finished를 보인 뒤 부른다(통합 전에는 아무것도 하지 않는다). */
@Suppress("UnusedReceiverParameter")
internal fun BattleRunCoordinator.acknowledgeFinished() = Unit

private val NO_FAILURE: StateFlow<AppError?> = MutableStateFlow<AppError?>(null).asStateFlow()
