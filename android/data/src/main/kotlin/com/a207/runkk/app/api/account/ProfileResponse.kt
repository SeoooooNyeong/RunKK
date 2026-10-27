package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/**
 * GET /members/{id}/profile 응답(서버 MeController.ProfileResponse). 공개 수준에 따라 서버가 걸러 보낸 항목만 있다(D-52).
 * v0.11: tags를 지우고 avatarBackgroundKey(null이면 기본 배경, FR-202)를 더했다(D-91).
 */
@Serializable
internal data class ProfileResponse(
    val memberId: Long,
    val displayName: String,
    val disclosure: String,
    val tier: String? = null,
    val level: Int? = null,
    val intro: String? = null,
    val runimalType: String? = null,
    val faceKey: String? = null,
    val avatarBackgroundKey: String? = null,
    val crewName: String? = null,
    val longestDistanceMeters: Long? = null,
    val ageBand: String? = null,
    val gender: String? = null,
    val photoUrl: String? = null
)
