package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * 남자 서기 riv에 내장된 기본 옷 몸(1122×1402) 슬롯. 상의·하의·신발 표면 0일 때만 보이는데, 의상 이전 뒤에는 모든 묶음이 표면 1·2를
 * 쓰므로(D-747) 앱이 이 슬롯을 다시 그리지 않는다. 이름과 크기는 riv 계약이라 남긴다. 앱은 이 자리에 같은 크기의 투명 그림
 * ([transparentPng])을 넣는다: 내장 원본(흰 기본 티·밝은 피부)이 어떤 프레임·상태에서든 아틀라스 표면의 빈 곳(민소매 옆 틈 등)으로
 * 비치지 않게 한다(실기기 판정: 남자 러닝량 100 싱글렛 옆 틈의 흰 조각, base_top은 티가 같은 자리를 덮어 깨끗했다).
 */
internal object MaleSelectedBody {
    const val ASSET = "male_selected_body"
    const val WIDTH = 1122
    const val HEIGHT = 1402

    /** [WIDTH]×[HEIGHT] 투명 PNG. */
    val transparentPng: ByteArray by lazy {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encoding failed" }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
