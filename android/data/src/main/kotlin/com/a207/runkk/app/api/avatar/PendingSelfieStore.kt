package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.domain.avatar.SelfieFailure

/**
 * 접수하고 아직 끝내지 않은 셀카 작업 ID(S15P31A207-248). 서버는 이 작업을 끝내기(결과 받기 또는 실패 확인) 전까지 새 셀카를
 * SELFIE_IN_PROGRESS로 막으므로, 화면을 떠나거나 앱을 다시 켜도 이어 조회하려고 남긴다. 사진은 따로(FilePendingSelfiePhoto) 둔다.
 * 접수 시각은 워커가 다시 시작해도 총 상한(약 16분)을 접수 시각 기준으로 세려고 남긴다(design-oo 05-30 D-749).
 */
internal interface PendingSelfieStore {
    fun load(): String?

    /** 작업 ID와 접수 시각(에포크 밀리초, 모르면 null)을 남긴다. 같은 작업을 다시 남기면 앞 접수 시각을 둔다. */
    fun save(jobId: String, submittedAtMillis: Long? = null)

    /** 남긴 작업의 접수 시각. 없거나 모르면 null. */
    fun submittedAt(): Long? = null

    fun clear()

    /** 마지막 셀카 실패(DV-10). 남긴 작업과 따로 둔다. */
    fun failure(): SelfieFailure? = null

    fun saveFailure(failure: SelfieFailure) = Unit

    fun clearFailure() = Unit
}
