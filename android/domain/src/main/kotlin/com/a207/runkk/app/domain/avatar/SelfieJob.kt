package com.a207.runkk.app.domain.avatar

/**
 * 셀카 얼굴 작업(서버 avatar.FaceJob, S15P31A207-276). 서버는 셀카를 받으면 얼굴 생성을 기다리지 않고 202와 작업을 돌려주고, 앱이 상태를
 * 조회한다. 끝나면(SUCCEEDED) 결과를 한 번 받아야 서버가 내 얼굴을 셀카 머리("head:{회원ID}:{작업ID}")로 바꾼다.
 * 실패(FAILED)면 [failureCode]에 사유(SELFIE_QUALITY_REJECTED·FACE_ASSETS_INCOMPLETE·STORAGE_FAILED·UPSTREAM_TIMEOUT 등)가 있다.
 */
data class SelfieJob(val jobId: String, val status: Status, val failureCode: String? = null) {
    enum class Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED
    }
}
