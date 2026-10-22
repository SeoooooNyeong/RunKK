package com.a207.runkk.app.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.RunCard
import com.a207.runkk.app.domain.runimal.RunimalCard
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * 카드 색(하이파이 다크 네온). ui.share는 5.1.2 표상 core.designsystem을 import하지 않으므로 같은 값을 여기 둔다.
 * 값이 바뀌면 core.designsystem.RunkkColors와 함께 바꾼다.
 */
private object CardColors {
    const val BACKGROUND = 0xFF09090B.toInt()
    const val SURFACE = 0xFF18181B.toInt()
    const val BORDER = 0xFF27272A.toInt()
    const val TEXT = 0xFFFAFAFA.toInt()
    const val TEXT_SECONDARY = 0xFFA1A1AA.toInt()
    const val LIME = 0xFFCBEA2B.toInt()
    const val ON_LIME = 0xFF09090B.toInt()
    const val LAVENDER = 0xFF9C92F0.toInt()
}

/** 숫자는 굵은 기울임(Barlow Condensed 근사), 글자는 기본 굵은 글꼴. */
private val NUMBER: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
private val BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

private fun paint(color: Int, size: Float = 0f, typeface: Typeface = BOLD): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    textSize = size
    this.typeface = typeface
}

/** 카드 바탕(배경 + 표면 둥근 사각형 + 테두리). */
private fun Canvas.cardBase(width: Int, height: Int) {
    drawColor(CardColors.BACKGROUND)
    val rect = RectF(MARGIN, MARGIN, width - MARGIN, height - MARGIN)
    drawRoundRect(rect, CORNER, CORNER, paint(CardColors.SURFACE))
    drawRoundRect(
        rect,
        CORNER,
        CORNER,
        paint(CardColors.BORDER).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
    )
}

/** 왼쪽 위 라벨 알약(카드 종류). */
private fun Canvas.labelPill(text: String) {
    val p = paint(CardColors.ON_LIME, 40f)
    val w = p.measureText(text) + 64f
    drawRoundRect(RectF(PAD, PAD, PAD + w, PAD + 72f), 36f, 36f, paint(CardColors.LIME))
    drawText(text, PAD + 32f, PAD + 50f, p)
}

private fun Canvas.brand(height: Int) {
    drawText("RUNKK", PAD, height - PAD, paint(CardColors.LIME, 48f, NUMBER))
}

private fun km(distance: Distance): String = String.format(Locale.US, "%.2f", distance.meters / 1_000.0)

private fun pace(pace: Pace?): String =
    pace?.let { String.format(Locale.US, "%d:%02d", it.secondsPerKm / 60, it.secondsPerKm % 60) } ?: "-:--"

private fun duration(d: Duration): String {
    val s = d.seconds.coerceAtLeast(0)
    return if (s >= 3_600) {
        String.format(Locale.US, "%d:%02d:%02d", s / 3_600, (s % 3_600) / 60, s % 60)
    } else {
        String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }
}

/** 카드 종류 라벨(FR-068: 자유런·배틀런·나와의 싸움·셀럽런·크루런). */
internal fun cardKindLabel(kind: CardKind): String = when (kind) {
    CardKind.FREE_RUN -> "FREE RUN"
    CardKind.BATTLE_RUN -> "BATTLE RUN"
    CardKind.SELF_RUN -> "SELF RUN"
    CardKind.CELEB_RUN -> "CELEB RUN"
    CardKind.CREW_RUN -> "CREW RUN"
}

/**
 * 러닝 카드 이미지(6.1.14 RunCardRenderer.render(card, side): Bitmap). 앞면은 기록, 뒷면은 경로선(RunCard.route),
 * 라벨은 카드 종류. 지도 타일은 지도 SDK 연동(C-005) 뒤 뒷면 바탕에 깐다. 지금은 어두운 격자 위에 경로선만 그린다.
 */
class RunCardRenderer @Inject constructor(@ApplicationContext private val context: Context) {
    fun render(card: RunCard, side: CardSide): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.cardBase(WIDTH, HEIGHT)
        canvas.labelPill(cardKindLabel(card.cardKind))
        when (side) {
            CardSide.FRONT -> front(canvas, card)
            CardSide.BACK -> back(canvas, card)
        }
        canvas.brand(HEIGHT)
        return bitmap
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
            "${km(card.distance)} KM · ${duration(card.elapsed)}",
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
        private val DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREAN)
    }
}

/**
 * 러니멀 유형 카드 이미지(앞면만, FR-168, design-review.md M28). 유형 동물 그림·제목·설명·축별 비율.
 * 그림은 ui.runimal의 대응표가 고른 drawable을 받는다(ui.share는 유형 값을 해석하지 않는다). null이면 그림을 비워 둔다.
 */
class RunimalCardRenderer @Inject constructor(@ApplicationContext private val context: Context) {
    fun render(card: RunimalCard, @DrawableRes art: Int? = null): Bitmap {
        val width = RunCardRenderer.WIDTH
        val height = RunCardRenderer.HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.cardBase(width, height)
        canvas.labelPill(context.getString(R.string.share_card_runimal_label))
        art?.let { ContextCompat.getDrawable(context, it) }?.let { drawable ->
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
            canvas.drawText(axis, PAD, y, paint(CardColors.TEXT_SECONDARY, 40f))
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

private const val MARGIN = 24f
private const val PAD = 96f
private const val CORNER = 64f
