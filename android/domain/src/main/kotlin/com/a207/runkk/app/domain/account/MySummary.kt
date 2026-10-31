package com.a207.runkk.app.domain.account

/**
 * 본인 요약(5.1.2 domain.account, v0.8 검증 R35, D-331). 홈 상단과 배틀런 화면이 쓰는 값만 담는다. 본인 프로필 응답
 * (GET /members/{id}/profile) 하나에서 api.account 매퍼가 투영하므로 서버 API는 그대로다. 다른 러너 프로필은 [Profile]이다.
 *
 * [validRuns]는 본인 프로필의 runCount(running 유효 기록 수, v0.12 D29)다. 랜덤 대결 잠금의 남은 횟수를 셀 때 쓴다
 * (RandomBattleGate, D-331). 옛 서버가 주지 않으면 null이다.
 */
data class MySummary(
    val displayName: String,
    val tier: String?,
    val level: Int?,
    val faceKey: String?,
    val avatarBackgroundKey: String?,
    val validRuns: Int?
)
