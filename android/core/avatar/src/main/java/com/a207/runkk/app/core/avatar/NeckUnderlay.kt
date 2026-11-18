package com.a207.runkk.app.core.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff

/**
 * 머리 아래에 깔 몸 쪽 목 층: 자리([neck], 몸 목 폭)와 머리 피부색([skin], 불투명 ARGB. 셀카는 메타 mobile.skinColor 또는 턱 위 피부
 * 중앙값, 기본 머리는 기본 머리 얼굴의 턱 위 피부 중앙값). 머리마다 다른 목 길이·폭에 기대지 않고 몸 기준 한 규칙으로 깃 안을 채운다.
 * 그을림은 얼굴과 같은 필터로 따른다(정면은 이 층을 얼굴 레이어에 구워 얼굴과 함께 톤을 받고, 측면은 합성할 때 같은 톤으로 그린다).
 */
internal class NeckUnderlay(val neck: BodyNeck, val skin: Int) {
    /** [canvas]에 목 층을 그린다. [amount]가 0보다 크면 얼굴과 같은 러닝량 톤을 준다. */
    fun draw(canvas: Canvas, amount: Int = MIN_RUNNING_AMOUNT) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = skin
            style = Paint.Style.FILL
            if (amount != MIN_RUNNING_AMOUNT) colorFilter = ColorMatrixColorFilter(SkinToneFilter.matrix(amount))
        }
        canvas.drawPath(neck.path(), paint)
    }

    /**
     * 측면 머리 피부에서 턱 아래([BodyNeck.trimFromY])의 목 층 밖 화소를 지운다([face]는 바뀐다). 목 폭을 몸 목 폭에 맞춘다.
     * 정면은 목 구멍 밖을 몸이 덮으므로 지우지 않는다.
     */
    fun trim(face: Bitmap) {
        val from = neck.trimFromY ?: return
        val canvas = Canvas(face)
        canvas.clipRect(0f, from, face.width.toFloat(), face.height.toFloat())
        canvas.clipOutPath(neck.path())
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
    }

    /** [face] 아래에 목 층을 깐 새 그림(정면 얼굴 레이어). 부른 쪽이 recycle한다. */
    fun under(face: Bitmap): Bitmap = Bitmap.createBitmap(face.width, face.height, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        draw(canvas)
        canvas.drawBitmap(face, 0f, 0f, null)
    }
}
