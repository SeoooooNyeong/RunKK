package com.a207.runkk.app.ui.record

import android.graphics.Bitmap
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.record.RunCard
import com.a207.runkk.app.ui.share.CardSide

data class RecordCardUiState(
    val isLoading: Boolean = true,
    val card: RunCard? = null,
    val front: Bitmap? = null,
    val back: Bitmap? = null,
    val side: CardSide = CardSide.FRONT,
    /** 카드 공유 단계(G3)인가. 거짓이면 카드 팝업(G1·G2)이다. */
    val isShareStep: Boolean = false,
    val isSharing: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null,
    /** 앞면 무대 배경으로 쓸 내가 고른 배경 키(FR-202). 읽지 못했거나 고르지 않았으면 null(기본 무대)이다. */
    val backgroundKey: String? = null
) {
    /** 뒷면(코스 지도)을 공유할 수 있는가(FR-011·FR-069). 최종 판단은 CardSharer가 설정 사본으로 한다. */
    val canShareBack: Boolean get() = card?.isCourseMapShared == true
}
