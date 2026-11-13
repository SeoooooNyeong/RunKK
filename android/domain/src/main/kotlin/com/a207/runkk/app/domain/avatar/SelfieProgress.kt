package com.a207.runkk.app.domain.avatar

/**
 * 셀카 얼굴 작업의 진행(design-oo 05-30 6.1, [임시:D-성-117]). 원본은 기기에 남긴 작업 ID·접수 시각·마지막 실패이고, 워커
 * (api.avatar SelfieJobWorker)가 고친 값을 같은 프로세스의 화면이 [AvatarRepository.selfieProgress]로 바로 받는다.
 */
sealed interface SelfieProgress {
    /** 맡긴 작업이 없다. */
    data object None : SelfieProgress

    /** 서버가 얼굴을 만드는 중이다. [submittedAtMillis]는 접수 시각(에포크 밀리초)이고 모르면 null이다. */
    data class Working(val jobId: String, val submittedAtMillis: Long? = null) : SelfieProgress

    /** 끝나서 내 얼굴에 적용했다([임시:D-성-118]). [faceKey]는 바뀐 얼굴 키다. */
    data class Succeeded(val jobId: String, val faceKey: String?) : SelfieProgress

    /** 얼굴을 만들지 못했다. [code]는 서버 사유 코드이고, 시간 초과는 [TIMEOUT], 연결 실패가 이어지면 null이다. */
    data class Failed(val jobId: String, val code: String?) : SelfieProgress

    companion object {
        /** 접수 뒤 약 16분이 지나도 끝나지 않았다(6.1 상한 초과). */
        const val TIMEOUT = "SELFIE_TIMEOUT"
    }
}
