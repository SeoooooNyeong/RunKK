package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 시안에 쓰인 선 아이콘(24 격자, 선 두께 2, 둥근 끝). SVG 원·다각형은 같은 모양의 path로 옮겼다.
 * Icon(imageVector = RunkkIcons.Back, contentDescription = …, tint = …)로 쓴다. 선 색은 tint가 정한다.
 */
object RunkkIcons {
    val Back: ImageVector = line("Back", "M15 18L9 12L15 6")
    val ChevronRight: ImageVector = line("ChevronRight", "M9 18L15 12L9 6")
    val ArrowRight: ImageVector = line("ArrowRight", "M5 12H19", "M12 5L19 12L12 19")
    val Bell: ImageVector = line(
        "Bell",
        "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9",
        "M10.3 21a1.94 1.94 0 0 0 3.4 0"
    )
    val Bolt: ImageVector = line("Bolt", "M13 2L3 14H12L11 22L21 10H12L13 2Z", filled = true)
    val Lock: ImageVector = line(
        "Lock",
        "M5 11H19A2 2 0 0 1 21 13V20A2 2 0 0 1 19 22H5A2 2 0 0 1 3 20V13A2 2 0 0 1 5 11Z",
        "M7 11V7a5 5 0 0 1 10 0v4"
    )
    val Unlock: ImageVector = line(
        "Unlock",
        "M5 11H19A2 2 0 0 1 21 13V20A2 2 0 0 1 19 22H5A2 2 0 0 1 3 20V13A2 2 0 0 1 5 11Z",
        "M7 11V7a5 5 0 0 1 9.9-1"
    )
    val Camera: ImageVector = line(
        "Camera",
        "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
        circle(12f, 13f, 3f)
    )
    val Share: ImageVector = line(
        "Share",
        circle(18f, 5f, 3f),
        circle(6f, 12f, 3f),
        circle(18f, 19f, 3f),
        "M8.59 13.51L15.42 17.49",
        "M15.41 6.51L8.59 10.49"
    )
    val Trophy: ImageVector = line(
        "Trophy",
        "M6 9H4.5a2.5 2.5 0 0 1 0-5H6",
        "M18 9h1.5a2.5 2.5 0 0 0 0-5H18",
        "M4 22h16",
        "M18 2H6v7a6 6 0 0 0 12 0V2Z"
    )
    val Heart: ImageVector = line(
        "Heart",
        "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2" +
            "A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"
    )
    val History: ImageVector = line("History", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5")
    val Star: ImageVector = line(
        "Star",
        "M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"
    )
    val Home: ImageVector = line("Home", "m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M9 22V12H15V22")
    val Users: ImageVector = line(
        "Users",
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        circle(9f, 7f, 4f),
        "M22 21v-2a4 4 0 0 0-3-3.87",
        "M16 3.13a4 4 0 0 1 0 7.75"
    )
    val User: ImageVector = line("User", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", circle(12f, 7f, 4f))
    val Settings: ImageVector = line(
        "Settings",
        circle(12f, 12f, 3f),
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33" +
            " 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33" +
            "l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4" +
            "h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06" +
            "a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68" +
            "a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06" +
            "a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09" +
            "a1.65 1.65 0 0 0-1.51 1Z"
    )
    val Check: ImageVector = line("Check", "M20 6L9 17L4 12")
    val Search: ImageVector = line("Search", circle(11f, 11f, 8f), "m21 21-4.3-4.3")
    val Plus: ImageVector = line("Plus", "M5 12h14", "M12 5v14")

    /** 교차한 두 검(시안 HiMatchWaiting 대결 대기). */
    val Swords: ImageVector = line(
        "Swords",
        "M14.5 17.5L3 6V3h3l11.5 11.5",
        "M13 19l6-6",
        "M16 16l4 4",
        "M19 21l2-2",
        "M14.5 6.5L18 3h3v3l-3.5 3.5",
        "M5 14l4 4",
        "M7 17l-3 3",
        "M3 19l2 2"
    )
    val Close: ImageVector = line("Close", "M18 6L6 18", "M6 6L18 18")
    val Calendar: ImageVector = line(
        "Calendar",
        "M5 4H19A2 2 0 0 1 21 6V20A2 2 0 0 1 19 22H5A2 2 0 0 1 3 20V6A2 2 0 0 1 5 4Z",
        "M16 2v4",
        "M8 2v4",
        "M3 10h18"
    )
    val Alert: ImageVector = line("Alert", circle(12f, 12f, 10f), "M12 8v4", "M12 16h.01")
    val CloudOff: ImageVector = line(
        "CloudOff",
        "M2 2L22 22",
        "M5.78 5.78A7 7 0 0 0 9 19h8.5a4.5 4.5 0 0 0 1.31-.2",
        "M21.53 16.5A4.5 4.5 0 0 0 17.5 10h-1.79A7 7 0 0 0 9.2 5.2"
    )

    /** SVG 원(cx, cy, r)을 path 문자열로. */
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun line(name: String, vararg paths: String, filled: Boolean = false): ImageVector {
        val builder = ImageVector.Builder(
            name = "Runkk.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        paths.forEach { data ->
            builder.addPath(
                pathData = addPathNodes(data),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }
}
