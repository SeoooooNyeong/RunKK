package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.CelebrationCard

/**
 * 공동 목표 축하 카드 공유(FR-141, TC-7496). 러닝 카드와 같은 공유 방식(ui.share CardSharer, FR-069)을 쓴다(D-140 ui.crew → ui.share).
 * 돌려주는 값은 화면에 알릴 문구(공유 창이 열렸으면 null)다. 시험에서 가짜로 바꿔 끼운다.
 */
fun interface CelebrationSharer {
    suspend fun share(card: CelebrationCard): UiText?
}
