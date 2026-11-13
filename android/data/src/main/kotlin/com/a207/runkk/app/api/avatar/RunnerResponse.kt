package com.a207.runkk.app.api.avatar

import kotlinx.serialization.Serializable

/** GET /avatar/runner 응답(서버 AvatarDtos.RunnerResponse, contract-W8.md 2.1). */
@Serializable
internal data class RunnerResponse(
    val bodyShape: String,
    val bodyVariant: Int = 0,
    val skinTone: String,
    val typicalStartHour: Int? = null,
    val recentRunCount: Int = 0,
    val recentDistanceMeters: Long = 0,
    val runAmount: Int = 0,
    val restDaysBeforeDecline: Int = DEFAULT_REST_DAYS
)

/** 서버 체형규칙 쉼 일수 기본값(FR-163). */
private const val DEFAULT_REST_DAYS = 4
