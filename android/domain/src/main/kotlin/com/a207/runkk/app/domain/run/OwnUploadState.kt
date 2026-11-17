package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance

/**
 * 내 기록 한 건(recordId)의 저장·전송 상태(설계 05-32 4.4, [임시:D-성-890]). 크루런 결과 「확인」이 내 기록만 기다릴 때 본다.
 * 보관함에서 사라졌다는 것만으로 성공을 추정하지 않는다: 서버가 받았다고 답한 [Stored]만 성공이다.
 */
sealed interface OwnUploadState {
    /** 이 앱 실행에서 저장·전송을 보지 못했고 보관함에도 없다(성공이 아니다). */
    data object Unknown : OwnUploadState

    /** 기기 보관함에 넣는 중. */
    data object Saving : OwnUploadState

    /** 보관함에 있고 전송을 기다린다(재전송 대기 포함). */
    data object Pending : OwnUploadState

    /** 서버가 받았다(STORED 또는 ALREADY_STORED). [isValid]가 거짓이면 받았지만 유효 기여는 0이다. [distance]는 측정 거리다. */
    data class Stored(val isValid: Boolean, val distance: Distance?) : OwnUploadState

    /** 기기 보관함에 넣지 못했다(세 번 실패). */
    data object SaveFailed : OwnUploadState

    /** 다시 보낼 만한 전송 실패(네트워크·인증·5xx). 재전송 예약이 이어진다. */
    data class Failed(val error: AppError) : OwnUploadState

    /** 서버가 거부했다(4xx, REJECTED로 남아 자동 재전송하지 않는다). */
    data class Rejected(val code: String) : OwnUploadState
}
