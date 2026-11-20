package com.a207.runkk.app.core.avatar

import android.graphics.SurfaceTexture
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.renderers.Renderer
import app.rive.runtime.kotlin.renderers.RiveArtboardRenderer

/**
 * 재생 빠르기를 바꿀 수 있는 Rive 뷰. Rive 상태 기계에는 빠르기 입력이 없어, 렌더러가 프레임마다 넘기는 경과 시간에
 * [playbackSpeed]를 곱한다(1 = 원래 빠르기). 러닝 중 달리기 동작을 페이스에 맞춰 빠르게·느리게 돌릴 때 쓴다.
 */
internal class PacedRiveAnimationView(builder: Builder) : RiveAnimationView(builder) {
    /** 판 하나가 화면 텍스처에 들어올 때마다(주 스레드). 보이게 된 뒤 첫 판을 기다리는 데 쓴다(S15P31A207-335 2차). */
    var onFrameDrawn: () -> Unit = {}

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        super.onSurfaceTextureUpdated(surface)
        onFrameDrawn()
    }

    @Volatile
    var playbackSpeed: Float = 1f

    override fun createRenderer(): Renderer = object : RiveArtboardRenderer(
        rendererAttributes.riveTraceAnimations,
        rendererAttributes.rendererType,
        controller
    ) {
        // 부모 생성자 안에서 렌더러를 만들 때는 아직 0이므로 1로 본다.
        override fun advance(elapsed: Float) {
            val speed = playbackSpeed
            super.advance(elapsed * if (speed > 0f) speed else 1f)
        }
    }
}
