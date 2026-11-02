package com.a207.runkk.app.domain.run

/**
 * 연결된 워치에 거부 기록 다시 보내기를 요청하는 포트(W40). 구현 data.watch.WatchLink(WearPaths.RESEND_REJECTED, MessageClient).
 * 워치가 닿지 않으면 요청은 버려진다(워치 건수는 폰에 보이지 않는다). ResendRejectedRunsUseCase만 부른다.
 */
fun interface WatchResendRequester {
    fun requestResend()
}
