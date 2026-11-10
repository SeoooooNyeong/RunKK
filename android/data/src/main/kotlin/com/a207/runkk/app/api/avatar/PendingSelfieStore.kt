package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.domain.avatar.SelfieFailure

/**
 * 접수하고 아직 끝내지 않은 셀카 작업 ID(S15P31A207-248). 서버는 이 작업을 끝내기(결과 받기 또는 실패 확인) 전까지 새 셀카를
 * SELFIE_IN_PROGRESS로 막으므로, 화면을 떠나거나 앱을 다시 켜도 이어 조회하려고 남긴다. 사진은 남기지 않는다.
 */
internal interface PendingSelfieStore {
    fun load(): String?

    fun save(jobId: String)

    fun clear()

    /** 마지막 셀카 실패(DV-성-10). 남긴 작업과 따로 둔다. */
    fun failure(): SelfieFailure? = null

    fun saveFailure(failure: SelfieFailure) = Unit

    fun clearFailure() = Unit
}
