package com.a207.runkk.app.core.avatar

/**
 * 승인 Rive 파일 네 개의 바닥 그림자(타원 「Ground shadow」 105x8, SolidColor 0x143D2D25, 알파 20/255)를 숨긴다(D-739).
 * 그림자는 앱이 바닥선에 맞춰 그리므로([AvatarStageFrame]) 두 겹이 되지 않게, 읽은 바이트에서 그 칠의 알파만 0으로 바꾼다.
 * 파일은 승인본(SHA 시험) 그대로다. 방법은 [RivReviewBackground]와 같다.
 */
internal object RivGroundShadow {
    private const val NAME = "Ground shadow"

    /** [riv]의 사본에서 그림자를 숨긴다. */
    fun hidden(riv: ByteArray): ByteArray = riv.copyOf().also(::hideInPlace)

    fun hideInPlace(riv: ByteArray) = RivReviewBackground.hideShapesInPlace(riv) { it == NAME }
}
