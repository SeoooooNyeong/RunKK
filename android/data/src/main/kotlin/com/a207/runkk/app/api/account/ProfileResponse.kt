package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/**
 * GET /members/{id}/profile 응답(서버 MeController.ProfileResponse). 공개 수준에 따라 서버가 걸러 보낸 항목만 있다(D-52).
 * v0.11: tags를 지우고 avatarBackgroundKey(null이면 기본 배경, FR-202)를 더했다(D-91).
 * v0.14(D-287, 서버 D-243): 누적 거리·러닝 횟수·최근 러닝(다른 러너)·나이대·성별 공개(본인)를 더했다. 모두 선택 항목이다(D-289).
 * photoUrl은 서버가 늘 null로 주므로 읽지 않는다.
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
    val totalDistanceMeters: Long? = null,
    val runCount: Int? = null,
    val recentRuns: List<RecentRunDto> = emptyList(),
    val ageBandVisibility: String? = null,
    val genderVisibility: String? = null,
    /** 다른 러너에게 보이는 표시 성별(MALE·FEMALE, D-327·D-336). 옛 서버면 null. */
    val shownGender: String? = null
) {
    /** 최근 러닝 한 줄(종류, 시작 날짜 yyyy-MM-dd, 거리 m). */
    @Serializable
    data class RecentRunDto(val kind: String? = null, val startedOn: String, val distanceMeters: Long)
}
