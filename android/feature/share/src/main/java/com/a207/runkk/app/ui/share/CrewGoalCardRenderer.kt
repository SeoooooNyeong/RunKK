package com.a207.runkk.app.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.a207.runkk.app.domain.crew.CelebrationCard
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

/**
 * 공동 목표 축하 카드 이미지(6.1.14 CrewGoalCardRenderer.render(card): Bitmap, FR-141): 크루명, 주간 합산 거리, 목표 거리, 참여 인원.
 * 위치 경로는 그리지 않는다(TC-7496). 러닝 카드(FR-069)와 같은 크기·색·라벨 알약을 쓰고 테두리만 라임으로 강조한다.
 * ui.crew가 CardSharer로 공유한다(D-140 ui.crew → ui.share).
 */
class CrewGoalCardRenderer @Inject constructor(@ApplicationContext private val context: Context) {
    fun render(card: CelebrationCard): Bitmap {
        val width = RunCardRenderer.WIDTH
        val height = RunCardRenderer.HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(CardColors.BACKGROUND)
        val rect = RectF(MARGIN, MARGIN, width - MARGIN, height - MARGIN)
        canvas.drawRoundRect(rect, CORNER, CORNER, paint(CardColors.SURFACE))
        canvas.drawRoundRect(
            rect,
            CORNER,
            CORNER,
            paint(CardColors.LIME).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
        )
        canvas.labelPill(context.getString(R.string.share_card_crew_goal_label))
        canvas.drawText(card.crewName, PAD, PAD + 200f, paint(CardColors.TEXT, 64f))
        canvas.drawText(km(card.total.meters), PAD - 8f, 720f, paint(CardColors.TEXT, 280f, NUMBER))
        canvas.drawText("KILOMETERS", PAD, 800f, paint(CardColors.TEXT_SECONDARY, 56f, NUMBER))
        val stats = listOf(
            context.getString(R.string.share_card_crew_goal_goal) to "${km(card.goal.meters)} km",
            context.getString(R.string.share_card_crew_goal_participants) to
                context.getString(R.string.share_card_crew_goal_people, card.participantCount)
        )
        stats.forEachIndexed { i, (name, value) ->
            val x = PAD + i * STAT_GAP
            canvas.drawText(name, x, 960f, paint(CardColors.TEXT_SECONDARY, 40f))
            canvas.drawText(value, x, 1_050f, paint(CardColors.TEXT, 72f, NUMBER))
        }
        canvas.brand(height)
        return bitmap
    }

    private fun km(meters: Long): String = String.format(Locale.US, "%.1f", meters / METERS_PER_KM)

    private companion object {
        const val MARGIN = 40f
        const val CORNER = 48f
        const val STAT_GAP = 460f
        const val METERS_PER_KM = 1_000.0
    }
}
