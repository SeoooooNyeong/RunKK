package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/** 트랙의 아바타 칸은 투명 여백을 제외한 머리부터 발까지다. 지도 등 기존 칸에는 적용하지 않는다. */
val LocalTrackAvatarViewport = staticCompositionLocalOf { false }

/**
 * 출발 전 트랙 주자 미리 데우기([RunkkTrackAvatarWarmup]) 안인가. 참이면 아바타 칸은 Rive 칸을 만들지 않고 그 모습의 Rive 파일 가져오기와
 * 텍스처 합성만 미리 한다(S15P31A207-335 6회차).
 */
val LocalTrackAvatarWarmup = staticCompositionLocalOf { false }

/**
 * 트랙 주자 칸의 아바타가 아직 준비 중인지 카드에 알리는 통로. 카드는 준비 중인 주자를 그림자와 이름표까지 숨겼다가 한 번에 보인다
 * (기본 그림이 먼저 보였다가 바뀌지 않게, [임시:D-성-181]과 같은 뜻).
 */
val LocalTrackAvatarLoading = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/** 배경 없는 Rive 칸의 바닥선 94%, 아트보드 1.17배와 몸 높이 약 64%를 보이는 키로 보정한다. */
data class TrackAvatarViewport(val slotSize: Float, val top: Float) {
    companion object {
        fun of(visibleHeight: Float): TrackAvatarViewport {
            val slot = visibleHeight / (1.17f * 0.64f)
            return TrackAvatarViewport(slot, visibleHeight - slot * 0.94f)
        }
    }
}
