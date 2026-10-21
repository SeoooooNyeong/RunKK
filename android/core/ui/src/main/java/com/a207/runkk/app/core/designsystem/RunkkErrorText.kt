package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.AppError

/**
 * AppError → 화면 문구(core.designsystem → core.model, 5.1.2). ViewModel이 Outcome.Failure를 UiState 문구로 바꿀 때 쓴다.
 * 서버 사유 코드(409)는 10/21 API 계약의 코드 이름을 따르고, 모르는 코드는 일반 문구로 보인다.
 */
object RunkkErrorText {
    fun of(error: AppError): String = when (error) {
        AppError.Network -> "네트워크에 연결할 수 없어요. 연결을 확인하고 다시 시도해 주세요"
        AppError.Unauthorized -> "로그인이 풀렸어요. 다시 로그인해 주세요"
        AppError.Storage -> "기기에 저장하지 못했어요. 저장 공간을 확인해 주세요"
        is AppError.Rejected -> rejected(error.code)
    }

    /** 사유 코드 문구. 모르는 코드면 일반 문구. */
    fun rejected(code: String): String = REJECTED[code] ?: "요청을 처리하지 못했어요"

    private val REJECTED: Map<String, String> = mapOf(
        AppError.Rejected.LOCATION_CONSENT to "러닝을 측정하려면 위치 권한과 위치 수집 동의가 필요해요",
        AppError.Rejected.RUN_IN_PROGRESS to "이미 러닝을 측정하고 있어요",
        AppError.Rejected.SIGN_UP_REQUIRED to "가입이 필요해요",
        "PROVIDER_UNAVAILABLE" to "로그인 제공자 연결 실패. 잠시 뒤 다시 시도해 주세요",
        "PROVIDER_SDK_NOT_READY" to "이 로그인 방법은 아직 준비 중이에요",
        "LOGIN_CANCELLED" to "로그인을 취소했어요",
        "CONSENT_MISSING" to "필수 항목에 모두 동의해 주세요",
        "UNDER_14" to "만 14세 이상만 가입할 수 있음",
        "NICKNAME_INVALID" to "닉네임은 2~20자로 입력해 주세요",
        "NICKNAME_TAKEN" to "이미 쓰고 있는 닉네임이에요",
        "TAG_INVALID" to "성향 태그는 분류마다 하나씩, 1~5개를 골라 주세요",
        "INTRO_TOO_LONG" to "소개는 40자 이내로 입력해 주세요",
        "SETTING_INVALID" to "바꿀 수 없는 설정 값이에요",
        "CREW_LEADER_TRANSFER_REQUIRED" to "크루장 양도 필요. 크루원에게 크루장을 넘긴 뒤 탈퇴해 주세요",
        "BATTLE_IN_PROGRESS" to "진행 중인 대결이나 매칭 대기가 있어요. 대결을 끝낸 뒤 다시 시도해 주세요",
        "NO_TYPE" to "아직 Runimal 유형이 없어요",
        "COURSE_MAP_NOT_SHARED" to "코스 지도 공유가 꺼져 있어 뒷면을 공유할 수 없어요"
    )
}
