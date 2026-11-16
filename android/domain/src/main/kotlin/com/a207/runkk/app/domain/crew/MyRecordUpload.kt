package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance

/**
 * 크루런 결과 「확인」이 기다리는 내 기록의 저장·업로드 단계(설계 05-32 2.3 CrewRunCompletion). 서버가 받았다고 확인된 [Stored]만
 * 성공이다. [Retryable]은 다시 보낼 수 있는 내 전송 실패, [Blocked]는 서버 거부·기기 저장 실패다.
 */
sealed interface MyRecordUpload {
    /** 기기 저장 중·전송 대기·아직 모름. */
    data object Waiting : MyRecordUpload

    /** 서버가 받았다. [isValid]가 거짓이면 유효 기여는 0이다. [distance]는 측정 거리이고 모르면 null이다. */
    data class Stored(val isValid: Boolean, val distance: Distance?) : MyRecordUpload

    data class Retryable(val error: AppError) : MyRecordUpload

    data object Blocked : MyRecordUpload
}
