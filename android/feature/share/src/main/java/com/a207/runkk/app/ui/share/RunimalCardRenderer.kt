package com.a207.runkk.app.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunimalAxisName
import com.a207.runkk.app.domain.runimal.RunimalCard
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 러니멀 유형 카드 이미지(앞면만, FR-168, design-review.md M28). 유형 동물 그림·제목·설명·축별 비율.
 * 그림·축 이름은 core.designsystem의 대응표(RunimalArt, RunimalAxisName)로 고른다. 모르는 유형은 기본 그림이다.
 */
class RunimalCardRenderer @Inject constructor(@ApplicationContext private val context: Context) {
    fun render(card: RunimalCard): Bitmap {
        val width = RunCardRenderer.WIDTH
        val height = RunCardRenderer.HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.cardBase(width, height)
        canvas.labelPill(context.getString(R.string.share_card_runimal_label))
        ContextCompat.getDrawable(context, RunimalArt.of(card.type).drawableRes)?.let { drawable ->
            canvas.drawCircle(PAD + ART_SIZE / 2, ART_TOP + ART_SIZE / 2, ART_SIZE / 2, paint(CardColors.BACKGROUND))
            val inset = ART_SIZE * ART_INSET
            drawable.setBounds(
                (PAD + inset).toInt(),
                (ART_TOP + inset).toInt(),
                (PAD + ART_SIZE - inset).toInt(),
                (ART_TOP + ART_SIZE - inset).toInt()
            )
            drawable.draw(canvas)
        }
        canvas.drawText(card.title, PAD, 540f, paint(CardColors.TEXT, 64f))
        wrap(card.description, 26).take(3).forEachIndexed { i, line ->
            canvas.drawText(line, PAD, 640f + i * 60f, paint(CardColors.TEXT_SECONDARY, 42f))
        }
        card.axisRatios.entries.take(4).forEachIndexed { i, (axis, ratio) ->
            val y = 880f + i * 100f
            val label = RunimalAxisName.of(axis)?.let { context.getString(it.nameRes) } ?: axis
            canvas.drawText(label, PAD, y, paint(CardColors.TEXT_SECONDARY, 40f))
            val left = PAD + 220f
            val right = width - PAD
            canvas.drawRoundRect(RectF(left, y - 30f, right, y), 15f, 15f, paint(CardColors.BORDER))
            val fill = left + (right - left) * ratio.coerceIn(0.0, 1.0).toFloat()
            canvas.drawRoundRect(
                RectF(left, y - 30f, fill, y),
                15f,
                15f,
                paint(
                    if (i % 2 ==
                        0
                    ) {
                        CardColors.LIME
                    } else {
                        CardColors.LAVENDER
                    }
                )
            )
        }
        canvas.brand(height)
        return bitmap
    }

    private fun wrap(text: String, perLine: Int): List<String> = text.chunked(perLine)

    private companion object {
        const val ART_TOP = 196f
        const val ART_SIZE = 270f
        const val ART_INSET = 0.08f
    }
}
