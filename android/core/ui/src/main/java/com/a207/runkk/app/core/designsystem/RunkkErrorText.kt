package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.AppError

/**
 * AppError → 화면 문구(core.designsystem → core.model, 5.1.2). ViewModel이 Outcome.Failure를 UiState 문구로 바꿀 때 쓴다.
 * 서버 사유 코드(409)는 10/21 API 계약의 코드 이름을 따르고, 모르는 코드는 일반 문구로 보인다.
 */
object RunkkErrorText {
    fun of(error: AppError): String = when (error) {
        AppError.Network -> "네트워크에 연결할 수 없어요. 연결을 확인하고 다시 시도해 주세요"
        is AppError.Server -> "서버가 응답하지 못했어요. 잠시 뒤 다시 시도해 주세요"
        AppError.Unauthorized -> "로그인이 풀렸어요. 다시 로그인해 주세요"
        AppError.Storage -> "기기에 저장하지 못했어요. 저장 공간을 확인해 주세요"
        is AppError.Rejected -> rejected(error.code)
    }

    /** 사유 코드 문구. 모르는 코드면 일반 문구. */
    fun rejected(code: String): String = REJECTED[code] ?: "요청을 처리하지 못했어요"

    /**
     * 사유 코드 → 문구. 키는 서버 common.ReasonCode 이름, 서버 오류 응답의 일반 코드(ErrorResponse: BAD_REQUEST·PERMISSION_DENIED·
     * INVALID_STATE·CONCURRENT_UPDATE), 앱 안에서 만드는 코드(위 AppError.Rejected 상수·LOGIN_CANCELLED 등)뿐이다.
     * 계약 시험(ContractEnumsTest)이 이 밖의 이름을 막는다. 대결·크루·아바타 전용 문구는 각 화면 모듈이 먼저 찾는다.
     */
    internal val REJECTED: Map<String, String> = mapOf(
        AppError.Rejected.LOCATION_CONSENT to "러닝을 측정하려면 위치 권한과 위치 수집 동의가 필요해요",
        AppError.Rejected.RUN_IN_PROGRESS to "이미 러닝을 측정하고 있어요",
        AppError.Rejected.SIGN_UP_REQUIRED to "가입이 필요해요",
        "NOT_FOUND" to "찾을 수 없어요. 이미 지워졌거나 없는 항목이에요",
        "BAD_REQUEST" to "요청 내용을 확인한 뒤 다시 시도해 주세요",
        "PERMISSION_DENIED" to "권한이 없어요",
        "INVALID_STATE" to "지금은 처리할 수 없는 상태예요. 잠시 뒤 다시 시도해 주세요",
        "CONCURRENT_UPDATE" to "다시 시도해 주세요",
        "TAG_INVALID" to "성향 태그는 분류마다 하나씩, 1~5개를 골라 주세요",
        "VALUE_INVALID" to "입력한 값을 확인해 주세요",
        "PROVIDER_UNAVAILABLE" to "로그인 제공자 연결 실패. 잠시 뒤 다시 시도해 주세요",
        "PROVIDER_SDK_NOT_READY" to "이 로그인 방법은 아직 준비 중이에요",
        "LOGIN_CANCELLED" to "로그인을 취소했어요",
        "AUTH_FAILED" to "로그인하지 못했어요. 다시 시도해 주세요",
        "SIGN_UP_TICKET_INVALID" to "가입 시간이 지났어요. 다시 로그인해 주세요",
        "ALREADY_MEMBER" to "이미 가입한 계정이에요. 로그인해 주세요",
        "CONSENT_MISSING" to "필수 항목에 모두 동의해 주세요",
        "UNDER_14" to "만 14세 이상만 가입할 수 있음",
        "NICKNAME_INVALID" to "닉네임은 2~20자로 입력해 주세요",
        "NICKNAME_TAKEN" to "이미 쓰는 닉네임이에요. 다른 닉네임을 입력해 주세요",
        "CREW_NAME_TAKEN" to "이미 있는 크루명이에요. 다른 이름을 입력해 주세요",
        "MATCH_FAILED" to "대결을 만들지 못했어요. 다시 시도해 주세요",
        "FACE_IMAGE_NOT_FOUND" to "아바타 얼굴 이미지를 찾을 수 없어요",
        "SELFIE_RETRY_LIMIT" to "오늘은 셀카 변환을 5번 실패해 더 시도할 수 없어요. 내일 다시 하거나 기본 캐릭터를 골라 주세요",
        "TOO_MANY_ATTEMPTS" to "시도가 너무 많아요. 잠시 뒤 다시 시도해 주세요",
        "INTRO_TOO_LONG" to "소개는 40자 이내로 입력해 주세요",
        "SETTING_INVALID" to "바꿀 수 없는 설정 값이에요",
        "CONSENT_ITEM_NOT_SETTING" to "동의 항목은 동의 관리에서 바꿔 주세요",
        "LEADER_MUST_TRANSFER" to "크루장 양도 필요. 크루원에게 크루장을 넘긴 뒤 탈퇴해 주세요",
        "CREW_DELETE_REQUIRED" to "크루 삭제 필요. 크루를 삭제한 뒤 탈퇴해 주세요",
        "BATTLE_IN_PROGRESS" to "진행 중인 대결이나 매칭 대기가 있어요. 대결을 끝낸 뒤 다시 시도해 주세요",
        "WITHDRAWAL_REJECTED" to "지금은 탈퇴할 수 없어요. 잠시 뒤 다시 시도해 주세요",
        "RECORD_NOT_FOUND" to "기록을 찾을 수 없어요",
        "ROUTE_PURGED" to "경로가 파기된 기록이에요",
        "RECORD_PRIVATE" to "비공개 기록이라 볼 수 없어요",
        "RECORD_CREW_ONLY" to "같은 크루원만 볼 수 있는 기록이에요",
        "GOAL_INVALID" to "주간 목표는 1~7회와 0km보다 긴 거리로 정해 주세요",
        "PLAN_INVALID" to "계획 거리는 0km보다 길게, 목표 페이스는 0초보다 크게 넣어 주세요",
        "PAST_DATE" to "지난 날짜에는 계획을 넣을 수 없어요",
        "NOTIFICATION_NOT_FOUND" to "알림을 찾을 수 없어요",
        "NO_TYPE" to "아직 Runimal 유형이 없어요",
        "COURSE_MAP_NOT_SHARED" to "코스 지도 공유가 꺼져 있어 뒷면을 공유할 수 없어요"
    )
}
