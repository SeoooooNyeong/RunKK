package com.a207.runkk.app.ui.chat

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.domain.chat.ReactionKind

/**
 * 채팅 줄선 아이콘(피그마 F-09 반응 알약·메뉴·F-09d 시트, 보내기 위쪽 화살표). 공용 RunkkIcons에 없는 엄지·위 화살표만 여기 둔다(24 격자,
 * 두께 2, 색은 tint).
 */
internal object ChatIcons {
    val ThumbsUp: ImageVector = line(
        "ThumbsUp",
        "M7 10v12",
        "M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4" +
            "a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"
    )

    val ArrowUp: ImageVector = line("ArrowUp", "M12 19V5", "M5 12l7-7 7 7")

    private fun line(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = "RunkkChat.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        paths.forEach { data ->
            builder.addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }
}

/** 반응 종류의 줄선 아이콘(좋아요 엄지, 하트, 체크). */
internal fun ReactionKind.icon(): ImageVector = when (this) {
    ReactionKind.LIKE -> ChatIcons.ThumbsUp
    ReactionKind.HEART -> RunkkIcons.Heart
    ReactionKind.CHECK -> RunkkIcons.Check
}

/** 반응 종류 이름(좋아요, 하트, 체크). */
@StringRes
internal fun ReactionKind.labelRes(): Int = when (this) {
    ReactionKind.LIKE -> R.string.chat_reaction_like
    ReactionKind.HEART -> R.string.chat_reaction_heart
    ReactionKind.CHECK -> R.string.chat_reaction_check
}
