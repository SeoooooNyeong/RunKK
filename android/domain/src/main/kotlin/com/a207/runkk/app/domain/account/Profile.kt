package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance

/**
 * 회원 프로필(GET /members/{id}/profile, 서버 profile.ProfileView). 공개 수준에 따라 서버가 걸러 보낸 항목만 채운다(D-52).
 * 필드 이름·형식은 10/21 API 계약으로 확정하고 api.account 매퍼가 맞춘다.
 */
data class Profile(
    val memberId: Long,
    val displayName: String,
    val disclosure: String,
    val tier: String?,
    val level: Int?,
    val intro: String?,
    val runimalType: String?,
    val faceKey: String?,
    val crewName: String?,
    val longestDistance: Distance?,
    val ageBand: String? = null,
    val gender: Gender? = null,
    val photoUrl: String? = null,
    /** 아바타 배경 키(공개 수준이 아바타를 보일 때만, null이면 기본 배경, FR-202). */
    val avatarBackgroundKey: String? = null
)
