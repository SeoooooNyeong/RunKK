package com.a207.runkk.app.core.model

/**
 * 앱 오류 형식(5.1.2). 데이터 모듈 경계에서 예외를 이 형식으로 바꾸고 도메인·화면에는 던지지 않는다(13.2).
 */
sealed interface AppError {
    /** 네트워크 실패: 연결하지 못했거나 시간이 지났다(IOException). 서버가 응답했다면 Network가 아니다. */
    data object Network : AppError

    /**
     * 서버는 응답했으나 지금 처리하지 못한다: 5xx, 요청 시간 초과(408)·사유 코드 없는 요청 과다(429), 해독할 수 없는 응답(status 0).
     * 잠시 뒤 다시 하면 될 수 있으므로 재전송 대상이다(Rejected가 아니다).
     */
    data class Server(val status: Int) : AppError

    /** 인증 실패(401). */
    data object Unauthorized : AppError

    /** 기기 저장소 오류. */
    data object Storage : AppError

    /** 업무 규칙 거부. code는 서버 사유 코드(409, 사유 코드가 있는 429 등) 또는 앱 도메인의 거부 사유다. */
    data class Rejected(val code: String) : AppError {
        companion object {
            /** 위치 수집 동의 또는 OS 위치 권한 없음(FR-031). */
            const val LOCATION_CONSENT = "LOCATION_CONSENT"

            /** 이미 측정 중인데 다시 시작 요청. */
            const val RUN_IN_PROGRESS = "RUN_IN_PROGRESS"

            /** 소셜 로그인은 됐으나 가입 전(가입 티켓 발급, D-19). */
            const val SIGN_UP_REQUIRED = "SIGN_UP_REQUIRED"
        }
    }
}
