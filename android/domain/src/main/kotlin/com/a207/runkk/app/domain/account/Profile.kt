package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender

/**
 * 회원 프로필(GET /members/{id}/profile, 서버 profile.ProfileView). 공개 수준에 따라 서버가 걸러 보낸 항목만 채운다(D-52).
 * 필드 이름·형식은 10/21 API 계약으로 확정하고 api.account 매퍼가 맞춘다.
 * v0.14(D-287): 사진(photoUrl)을 지우고 누적 거리·러닝 횟수(본인과 다른 러너), 최근 러닝(다른 러너), 나이대·성별 공개(본인)를 더했다.
 * 옛 서버가 주지 않는 값은 null·빈 목록이다(D-289).
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
    /** 아바타 배경 키(공개 수준이 아바타를 보일 때만, null이면 기본 배경, FR-202). */
    val avatarBackgroundKey: String? = null,
    val totalDistance: Distance? = null,
    val runCount: Int? = null,
    val recentRuns: List<RecentRun> = emptyList(),
    /** 나이대 공개(본인 응답 ageBandVisibility가 PUBLIC). 본인이 아니거나 옛 서버면 null이다. */
    val isAgePublic: Boolean? = null,
    /** 성별 공개(본인 응답 genderVisibility가 PUBLIC). 본인이 아니거나 옛 서버면 null이다. */
    val isGenderPublic: Boolean? = null,
    /**
     * 다른 러너에게 보이는 표시 성별(D-327·D-336): 성별 공개인 남·여면 그 값, 아니면 회원 ID 고정 대체값. 다른 러너 프로필이 이 값으로
     * 그 러너의 캐릭터를 그린다. 옛 서버면 null(남자 그림).
     */
    val shownGender: Gender? = null
)
