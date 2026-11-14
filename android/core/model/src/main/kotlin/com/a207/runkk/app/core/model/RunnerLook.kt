package com.a207.runkk.app.core.model

/**
 * 다른 러너(대결 상대, 기록런의 그날의 나, 크루런 크루원)의 아바타 모습([임시:D-성-247]). 서버 `common.AvatarLook`을 옮긴 값이다.
 * [gender]는 표시 성별, 부위 ID는 입은 상점 아이템(안 입었으면 null), [bodyVariant]는 체형(0~2), [runAmount]는 러닝량(0~100),
 * [faceKey]는 얼굴 키(없으면 null: 성별 기본 머리)다.
 */
data class RunnerLook(
    val gender: Gender,
    val topItemId: Long?,
    val bottomItemId: Long?,
    val shoesItemId: Long?,
    val bodyVariant: Int,
    val runAmount: Int,
    val faceKey: String?
)
