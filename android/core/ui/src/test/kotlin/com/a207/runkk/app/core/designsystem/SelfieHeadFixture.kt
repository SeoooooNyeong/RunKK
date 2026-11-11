package com.a207.runkk.app.core.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import java.io.ByteArrayOutputStream

/**
 * 셀카 머리 층 도형 픽스처(서버 GET /avatar/heads 정면 세 층처럼 1024 캔버스 가운데에 작은 머리와 짧은 목): 얼굴(귀·목 포함), 눈 두 개,
 * 헤어. 스크린샷 시험이 프로필 원 구도를 눈으로 확인하는 데 쓴다.
 */
internal object SelfieHeadFixture {
    private const val SIZE = 1024
    private val SKIN = Color.rgb(246, 214, 192)
    private val EYE = Color.rgb(70, 45, 35)
    private val HAIR = Color.rgb(78, 52, 40)

    val layers: List<FaceLayer> by lazy {
        listOf(
            FaceLayer(
                FacePart.EARS,
                png { canvas, paint ->
                    paint.color = SKIN
                    canvas.drawRect(472f, 660f, 552f, 740f, paint)
                    canvas.drawOval(RectF(372f, 360f, 652f, 690f), paint)
                    canvas.drawOval(RectF(350f, 500f, 400f, 580f), paint)
                    canvas.drawOval(RectF(624f, 500f, 674f, 580f), paint)
                }
            ),
            FaceLayer(
                FacePart.EYES,
                png { canvas, paint ->
                    paint.color = EYE
                    canvas.drawOval(RectF(430f, 520f, 480f, 580f), paint)
                    canvas.drawOval(RectF(544f, 520f, 594f, 580f), paint)
                }
            ),
            FaceLayer(
                FacePart.HAIR,
                png { canvas, paint ->
                    paint.color = HAIR
                    canvas.drawOval(RectF(352f, 300f, 672f, 520f), paint)
                }
            )
        )
    }

    private fun png(draw: (Canvas, Paint) -> Unit): ByteArray {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        try {
            draw(Canvas(bitmap), Paint(Paint.ANTI_ALIAS_FLAG))
            return ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
