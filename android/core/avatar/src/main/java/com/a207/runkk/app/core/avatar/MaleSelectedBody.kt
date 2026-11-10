package com.a207.runkk.app.core.avatar

/**
 * 남자 서기 riv에 내장된 기본 옷 몸(1122×1402) 슬롯. 상의·하의·신발 표면 0일 때만 보이는데, 의상 이전 뒤에는 모든 묶음이 표면 1·2를
 * 쓰므로(D-성-115) 앱이 이 슬롯을 다시 그리지 않는다. 이름과 크기는 riv 계약이라 남긴다.
 */
internal object MaleSelectedBody {
    const val ASSET = "male_selected_body"
    const val WIDTH = 1122
    const val HEIGHT = 1402
}
