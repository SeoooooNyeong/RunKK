package com.a207.runkk.app.core.navigation

import kotlinx.serialization.Serializable

/**
 * 칸에 넘기는 다른 러너의 아바타 모습([임시:D-성-259]). core.model.RunnerLook과 같은 값을 원시값으로만 담는다(이 패키지는 core.model을
 * import하지 않는다, 5.1.2). 표시 성별이 여자면 [isFemale]이 참이다. 부위 ID는 입은 상점 아이템(안 입었으면 null), [bodyVariant]는
 * 체형(0~2), [runAmount]는 러닝량(0~100), [faceKey]는 얼굴 키(없으면 성별 기본 머리)다. 대결 진행 경로 값(Routes.BattleRunning)에도 싣는다.
 */
@Serializable
data class SlotRunnerLook(
    val isFemale: Boolean,
    val topItemId: Long? = null,
    val bottomItemId: Long? = null,
    val shoesItemId: Long? = null,
    val bodyVariant: Int = 0,
    val runAmount: Int = 0,
    val faceKey: String? = null
)
