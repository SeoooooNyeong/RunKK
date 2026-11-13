package com.a207.runkk.app.domain.crew

import java.time.Instant

/**
 * 초대 코드로 찾은 크루 요약(피그마 F-03c, POST /crews/invite-preview, contract-W4 4절). 가입은 확인 뒤
 * [CrewRepository.applyWithInviteCode]로 따로 한다. [visibility]가 비공개면 크루 밖 사람은 상세를 못 보므로 상세 이동을 숨긴다.
 * 초대 코드 가입은 [joinPolicy]와 상관없이 바로 가입이다.
 */
data class CrewPreview(
    val crewId: Long,
    val name: String,
    val intro: String?,
    val tags: Set<String>,
    val logoUrl: String?,
    val visibility: CrewVisibility,
    val joinPolicy: JoinPolicy,
    val headcount: Int,
    val createdAt: Instant
)
