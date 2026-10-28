package com.a207.runkk.app.ui.share

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.Duration
import java.util.Locale

/** 숫자는 굵은 기울임(Barlow Condensed 근사), 글자는 기본 굵은 글꼴. */
internal val NUMBER: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)

private val BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

internal fun paint(color: Int, size: Float = 0f, typeface: Typeface = BOLD): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        this.typeface = typeface
    }

/** 카드 바탕(배경 + 표면 둥근 사각형 + 테두리). */
internal fun Canvas.cardBase(width: Int, height: Int) {
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
internal fun Canvas.labelPill(text: String) {
    val p = paint(CardColors.ON_LIME, 40f)
    val w = p.measureText(text) + 64f
    drawRoundRect(RectF(PAD, PAD, PAD + w, PAD + 72f), 36f, 36f, paint(CardColors.LIME))
    drawText(text, PAD + 32f, PAD + 50f, p)
}

internal fun Canvas.brand(height: Int) {
    drawText("RUNKK", PAD, height - PAD, paint(CardColors.LIME, 48f, NUMBER))
}

internal fun km(distance: Distance): String = String.format(Locale.US, "%.2f", distance.meters / 1_000.0)

internal fun pace(pace: Pace?): String =
    pace?.let { String.format(Locale.US, "%d:%02d", it.secondsPerKm / 60, it.secondsPerKm % 60) } ?: "-:--"

internal fun duration(d: Duration): String {
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

private const val MARGIN = 24f

internal const val PAD = 96f

private const val CORNER = 64f
