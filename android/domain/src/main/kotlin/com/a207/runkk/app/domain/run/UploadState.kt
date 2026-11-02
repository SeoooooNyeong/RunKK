package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant

/** 보관 기록 전송 상태. */
enum class UploadState {
    NOT_SENT,
    SENDING,

    /**
     * 서버가 4xx로 받지 않았다(검증 실패 등, R12 D2-b). 고쳐 보내도 같은 결과라 자동 재전송을 멈추고 사유와 함께 보관한다
     * (03-00 앱의 응답 해석: "4xx는 기록을 지우지 않고 거부 사유와 함께 보존"). 보관함에는 미전송으로 남는다.
     */
    REJECTED
}
