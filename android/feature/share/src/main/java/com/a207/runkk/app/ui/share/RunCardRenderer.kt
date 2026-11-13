package com.a207.runkk.app.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.a207.runkk.app.core.designsystem.avatarBackdropRes
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.domain.record.RunCard
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * 러닝 카드 이미지(6.1.14 RunCardRenderer.render(card, side, backgroundKey, backgroundStage): Bitmap). 앞면은 고른 랜드마크 배경(AvatarBackdrop,
 * FR-202) 위에 기록, 뒷면은 경로선(RunCard.route), 라벨은 카드 종류. 배경 키·단계는 ui.record가 읽어 문자열로 넘기고 null이면 기본 배경이다
 * (D-140, D-185). 단계가 있으면 운영이 더한 키(LM_n)도 단계 그림으로 그린다. 지도 타일은 지도 SDK 연동(C-005) 뒤 뒷면 바탕에 깐다. 지금은 어두운 격자 위에 경로선만 그린다.
 * 카드 이미지는 공유용이라 앱 테마와 무관하게 다크 색으로 그린다.
 */
class RunCardRenderer @Inject constructor(@ApplicationContext private val context: Context) {
    fun render(card: RunCard, side: CardSide, backgroundKey: String?, backgroundStage: String?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.cardBase(WIDTH, HEIGHT)
        if (side == CardSide.FRONT) backdrop(canvas, backgroundKey, backgroundStage)
        canvas.labelPill(cardKindLabel(card.cardKind))
        when (side) {
            CardSide.FRONT -> front(canvas, card)
            CardSide.BACK -> back(canvas, card)
        }
        canvas.brand(HEIGHT)
        return bitmap
    }

    /** 앞면 위쪽에 랜드마크 배경을 깔고 아래로 갈수록 카드 표면색으로 덮어 기록 글자를 읽기 쉽게 한다. */
    private fun backdrop(canvas: Canvas, backgroundKey: String?, backgroundStage: String?) {
        val drawable = context.getDrawable(avatarBackdropRes(backgroundKey, backgroundStage)) ?: return
        val bottom = (HEIGHT * BACKDROP_HEIGHT).toInt()
        drawable.setBounds(BACKDROP_INSET, BACKDROP_INSET, WIDTH - BACKDROP_INSET, bottom)
        drawable.alpha = BACKDROP_ALPHA
        drawable.draw(canvas)
        val fade = Paint().apply {
            shader = LinearGradient(
                0f,
                bottom * FADE_START,
                0f,
                bottom.toFloat(),
                Color.TRANSPARENT,
                CardColors.SURFACE,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(
            BACKDROP_INSET.toFloat(),
            BACKDROP_INSET.toFloat(),
            (WIDTH - BACKDROP_INSET).toFloat(),
            bottom.toFloat(),
            fade
        )
    }

    private fun front(canvas: Canvas, card: RunCard) {
        val date = DATE.format(card.startedAt.atZone(ZoneId.systemDefault()))
        canvas.drawText(date, PAD, PAD + 140f, paint(CardColors.TEXT_SECONDARY, 40f))
        canvas.drawText(km(card.distance), PAD - 8f, 700f, paint(CardColors.TEXT, 300f, NUMBER))
        canvas.drawText("KILOMETERS", PAD, 790f, paint(CardColors.TEXT_SECONDARY, 56f, NUMBER))
        val stats = listOf(
            context.getString(R.string.share_card_stat_time) to duration(card.elapsed),
            context.getString(R.string.share_card_stat_pace) to pace(card.averagePace)
        )
        stats.forEachIndexed { i, (label, value) ->
            val x = PAD + i * 460f
            canvas.drawText(label, x, 960f, paint(CardColors.TEXT_SECONDARY, 40f))
            canvas.drawText(value, x, 1_070f, paint(CardColors.LIME, 110f, NUMBER))
        }
    }

    private fun back(canvas: Canvas, card: RunCard) {
        val area = RectF(PAD, PAD + 120f, WIDTH - PAD, HEIGHT - PAD - 120f)
        val grid = paint(CardColors.BORDER).apply { strokeWidth = 2f }
        var y = area.top
        while (y <= area.bottom) {
            canvas.drawLine(area.left, y, area.right, y, grid)
            y += GRID
        }
        var x = area.left
        while (x <= area.right) {
            canvas.drawLine(x, area.top, x, area.bottom, grid)
            x += GRID
        }
        drawRoute(canvas, card.route, area, context.getString(R.string.share_card_no_course))
        canvas.drawText(
            "${km(card.distance)} KM   ${duration(card.elapsed)}",
            PAD,
            HEIGHT - PAD - 60f,
            paint(CardColors.TEXT, 48f, NUMBER)
        )
    }

    private fun drawRoute(canvas: Canvas, route: List<GeoPoint>, area: RectF, noCourse: String) {
        if (route.size < 2) {
            canvas.drawText(noCourse, area.left + 40f, area.centerY(), paint(CardColors.TEXT_SECONDARY, 44f))
            return
        }
        val minLat = route.minOf { it.latitude }
        val maxLat = route.maxOf { it.latitude }
        val minLon = route.minOf { it.longitude }
        val maxLon = route.maxOf { it.longitude }
        val span = maxOf(maxLat - minLat, maxLon - minLon).takeIf { it > 0 } ?: 1.0
        val inset = 60f
        val scale = minOf(area.width() - 2 * inset, area.height() - 2 * inset) / span
        val ox = area.left + (area.width() - (maxLon - minLon) * scale).toFloat() / 2
        val oy = area.top + (area.height() - (maxLat - minLat) * scale).toFloat() / 2
        fun px(p: GeoPoint) = (ox + (p.longitude - minLon) * scale).toFloat()
        fun py(p: GeoPoint) = (oy + (maxLat - p.latitude) * scale).toFloat()
        val path = Path().apply {
            moveTo(px(route.first()), py(route.first()))
            route.drop(1).forEach { lineTo(px(it), py(it)) }
        }
        canvas.drawPath(
            path,
            paint(CardColors.LIME).apply {
                style = Paint.Style.STROKE
                strokeWidth = 14f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        )
        canvas.drawCircle(px(route.first()), py(route.first()), 18f, paint(CardColors.TEXT))
        canvas.drawCircle(px(route.last()), py(route.last()), 18f, paint(CardColors.LIME))
    }

    companion object {
        /** 카드 크기(4:5, 인스타그램 피드·스토리 공통). */
        const val WIDTH = 1_080
        const val HEIGHT = 1_350
        private const val GRID = 90f
        private const val BACKDROP_HEIGHT = 0.62f
        private const val BACKDROP_INSET = 30
        private const val BACKDROP_ALPHA = 150
        private const val FADE_START = 0.35f
        private val DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREAN)
    }
}
