package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.avatar.GeneratedHeadLayers
import com.a207.runkk.app.core.model.FaceLayer

/**
 * 아바타 화면이 그리는 얼굴 하나.
 *
 * @property faceLayers Rive를 그리지 못할 때 앞 단계 그림이 쓰는 대체 그림(셀카 부위 또는 셀카 머리 정면 세 층)
 * @property headLayers 렌더러 셀카 머리 레이어. 셀카 머리가 아니거나 만들지 못했으면 null(기본 머리)
 */
class FaceLook(val faceLayers: List<FaceLayer>, val headLayers: GeneratedHeadLayers?) {
    internal companion object {
        val NONE = FaceLook(emptyList(), null)
    }
}
