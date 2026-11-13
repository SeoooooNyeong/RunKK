package com.a207.runkk.app.api.avatar

/** 셀카 작업 조회 한 번의 실행([SelfieJobPoller.poll])이 어떻게 끝났는가. */
internal enum class SelfiePollEnd {
    /** 작업이 끝났거나(성공·실패·상한 초과) 따라갈 작업이 아니다. 다시 걸지 않는다. */
    FINISHED,

    /** 한 번의 실행 시간(8분)을 다 썼다. 같은 작업을 뒤에 다시 건다(APPEND_OR_REPLACE). */
    CONTINUE
}
