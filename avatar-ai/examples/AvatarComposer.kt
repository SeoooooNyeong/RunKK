package com.a207.runkk.avatar.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Base64
import kotlin.math.max
import kotlin.math.min

// Retrofit/serialization DTO에서 읽은 값. anchor와 neck은 각각 이미지 전체의 0..1 좌표다.
data class HeadMobile(
    val canvas: List<Int>,
    val anchor: List<Float>,
    val renderFaceImage: String,
    val skinColor: List<Int>?,
    val displayHeight: Float,
)

data class BodyMobile(
    val canvas: List<Int>,
    val neck: List<Float>,
    val neckWidth: Float?, // 정규화 비율이 아닌 원본 몸 PNG의 픽셀 너비
    val neckHeight: Float?,
    val neckTop: Float?,
)

object AvatarComposer {
    fun decodePng(dataUrl: String): Bitmap {
        require(dataUrl.startsWith("data:image/png;base64,"))
        val bytes = Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)
        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    // Bitmap 계산은 Dispatchers.Default에서 호출한다. 응답 이미지를 개별 trim/resize하지 않는다.
    fun compose(
        mobile: HeadMobile,
        eyesImage: String,
        hairImage: String,
        bodyImage: String,
        bodyMobile: BodyMobile,
        neckGap: Float = 10f,
    ): Bitmap {
        val face = decodePng(mobile.renderFaceImage) // faceImage에 목 제거를 다시 적용하지 않는다.
        val eyes = decodePng(eyesImage)
        val hair = decodePng(hairImage)
        val originalBody = decodePng(bodyImage)
        require(listOf(face, eyes, hair).all { it.width == mobile.canvas[0] && it.height == mobile.canvas[1] })
        require(originalBody.width == bodyMobile.canvas[0] && originalBody.height == bodyMobile.canvas[1])
        val body = matchNeckSkin(originalBody, bodyMobile, mobile.skinColor)
        val output = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val bodyScale = 570f / body.height
        val neck = bodyMobile.neck
        val bodyLeft = 512f - neck[0] * body.width * bodyScale
        val bodyTop = 450f - neck[1] * body.height * bodyScale
        val available = max(0f, (bodyMobile.neckHeight ?: 0f) * bodyScale - 2f)
        val natural = max(10f, (bodyMobile.neckWidth ?: 0f) * bodyScale * .4f)
        val visibleNeck = min(available, min(available, natural) + neckGap.coerceIn(0f, 26f))
        // 턱을 몸 목의 끝에서 visibleNeck만큼 위에 둔다. 목 폭이 없으면 끝점에 붙인다.
        val headScale = mobile.displayHeight / max(1f, face.height.toFloat())
        val headLeft = 512f - mobile.anchor[0] * face.width * headScale
        val headTop = 450f - visibleNeck - mobile.anchor[1] * face.height * headScale
        fun draw(image: Bitmap, x: Float, y: Float, scale: Float) {
            canvas.drawBitmap(image, null, RectF(x, y, x + image.width * scale, y + image.height * scale), paint)
        }
        // 몸 → 목 제거 얼굴 → 눈·속눈썹 → 헤어·눈썹. 세 얼굴 레이어는 동일한 변환을 쓴다.
        draw(body, bodyLeft, bodyTop, bodyScale)
        for (layer in listOf(face, eyes, hair)) draw(layer, headLeft, headTop, headScale)
        return output
    }

    private fun matchNeckSkin(source: Bitmap, mobile: BodyMobile, skin: List<Int>?): Bitmap {
        val width = mobile.neckWidth ?: return source
        val top = mobile.neckTop ?: return source
        val height = mobile.neckHeight ?: return source
        if (skin == null || skin.size != 3) return source
        val bitmap = requireNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
        val center = mobile.neck[0] * bitmap.width
        val left = (center - width * .7f).toInt().coerceIn(0, bitmap.width - 1)
        val right = (center + width * .7f).toInt().coerceIn(left + 1, bitmap.width)
        val first = top.toInt().coerceIn(0, bitmap.height - 1)
        val bottom = (top + height).toInt().coerceIn(first + 1, bitmap.height)
        for (y in first until bottom) for (x in left until right) {
            val color = bitmap.getPixel(x, y)
            val alpha = (color ushr 24) and 255
            val r = (color ushr 16) and 255
            val g = (color ushr 8) and 255
            val b = color and 255
            if (alpha > 90 && r > g + 3 && g > b - 2 && r > b + 8 && r > 55) {
                bitmap.setPixel(x, y, (alpha shl 24) or (skin[0].coerceIn(0, 255) shl 16) or (skin[1].coerceIn(0, 255) shl 8) or skin[2].coerceIn(0, 255))
            }
        }
        return bitmap
    }
}
