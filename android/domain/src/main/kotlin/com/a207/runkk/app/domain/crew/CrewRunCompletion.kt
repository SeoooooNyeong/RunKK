package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.domain.run.OwnRunUploadPort
import com.a207.runkk.app.domain.run.OwnUploadState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 크루런 결과 「확인」의 내 기록 관찰(설계 05-32 CrewRunCompletion, [임시:D-성-890]). domain.run의 내 기록 상태 포트만 보고 다른
 * 크루원이나 크루 집계는 기다리지 않는다. 이 앱 실행에서 본 결과가 없으면(앱을 다시 켰다) 결과 응답이 내 기록을 이었을 때
 * ([linkedRecordId]가 내 기록 ID) 서버가 받은 것으로 본다. 보관함에서 사라졌다는 것만으로 성공을 추정하지 않는다.
 */
class CrewRunCompletion @Inject constructor(private val uploads: OwnRunUploadPort) {
    fun observe(recordId: String, linkedRecordId: Flow<String?>): Flow<MyRecordUpload> =
        combine(uploads.observe(recordId), linkedRecordId) { upload, linked ->
            when (upload) {
                is OwnUploadState.Stored -> MyRecordUpload.Stored(upload.isValid, upload.distance)

                is OwnUploadState.Failed -> MyRecordUpload.Retryable(upload.error)

                is OwnUploadState.Rejected, OwnUploadState.SaveFailed -> MyRecordUpload.Blocked

                // 앱을 다시 켜 이 실행에서 본 것이 없으면 결과 응답이 이은 기록으로만 서버 저장을 안다.
                OwnUploadState.Unknown -> stored(linked == recordId)

                OwnUploadState.Saving, OwnUploadState.Pending -> MyRecordUpload.Waiting
            }
        }.distinctUntilChanged()

    /** 내 기록을 다시 보낸다(기존 전송 예약). */
    fun retry(recordId: String) = uploads.retry(recordId)

    private fun stored(isLinked: Boolean): MyRecordUpload =
        if (isLinked) MyRecordUpload.Stored(isValid = true, distance = null) else MyRecordUpload.Waiting
}
