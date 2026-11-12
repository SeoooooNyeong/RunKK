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
    /** 꺾쇠 뒤로(<). 상단 바 뒤로 버튼은 [ArrowBack]을 쓴다. */
    val Back: ImageVector = line("Back", "M15 18L9 12L15 6")

    /** 화살대 있는 뒤로(←, 피그마 RunKK / Buttons / 뒤로가기의 Material arrow_back, 채움). */
    val ArrowBack: ImageVector =
        filled("ArrowBack", 24f, "M7.825 13L13.425 18.6L12 20L4 12L12 4L13.425 5.4L7.825 11L20 11L20 13L7.825 13Z")

    // 메타 줄(C안) 아이콘 8종: 피그마 "제안" 62299:1642의 14 격자 Material 아이콘(채움)을 그대로 옮겼다. 보조 글자색으로 칠한다.

    /** 달력(calendar_today): 날짜·개설일. */
    val CalendarToday: ImageVector = filled(
        "CalendarToday",
        META_GRID,
        "M11.667 1.75L11.083 1.75L11.083 0.583L9.917 0.583L9.917 1.75L4.083 1.75L4.083 0.583L2.917 0.583L2.917 1.75" +
            "L2.333 1.75C1.692 1.75 1.167 2.275 1.167 2.917L1.167 12.25" +
            "C1.167 12.892 1.692 13.417 2.333 13.417L11.667 13.417" +
            "C12.308 13.417 12.833 12.892 12.833 12.25L12.833 2.917C12.833 2.275 12.308 1.75 11.667 1.75Z" +
            "M11.667 12.25L2.333 12.25L2.333 4.667L11.667 4.667L11.667 12.25Z"
    )

    /** 사람들(group): 인원. */
    val Group: ImageVector = filled(
        "Group",
        META_GRID,
        "M9.333 6.417C10.302 6.417 11.077 5.635 11.077 4.667C11.077 3.698 10.302 2.917 9.333 2.917" +
            "C8.365 2.917 7.583 3.698 7.583 4.667C7.583 5.635 8.365 6.417 9.333 6.417Z" +
            "M4.667 6.417C5.635 6.417 6.411 5.635 6.411 4.667C6.411 3.698 5.635 2.917 4.667 2.917" +
            "C3.698 2.917 2.917 3.698 2.917 4.667C2.917 5.635 3.698 6.417 4.667 6.417Z" +
            "M4.667 7.583C3.307 7.583 0.583 8.266 0.583 9.625L0.583 11.083L8.75 11.083L8.75 9.625" +
            "C8.75 8.266 6.026 7.583 4.667 7.583Z" +
            "M9.333 7.583C9.164 7.583 8.972 7.595 8.767 7.613C9.444 8.103 9.917 8.762 9.917 9.625L9.917 11.083" +
            "L13.417 11.083L13.417 9.625C13.417 8.266 10.692 7.583 9.333 7.583Z"
    )

    /** 시계(schedule): 시간대·시각. */
    val Schedule: ImageVector = filled(
        "Schedule",
        META_GRID,
        "M6.994 1.167C3.774 1.167 1.167 3.78 1.167 7C1.167 10.22 3.774 12.833 6.994 12.833" +
            "C10.22 12.833 12.833 10.22 12.833 7C12.833 3.78 10.22 1.167 6.994 1.167Z" +
            "M7 11.667C4.422 11.667 2.333 9.578 2.333 7C2.333 4.422 4.422 2.333 7 2.333" +
            "C9.578 2.333 11.667 4.422 11.667 7C11.667 9.578 9.578 11.667 7 11.667Z" +
            "M7.292 4.083L6.417 4.083L6.417 7.583L9.479 9.421L9.917 8.703L7.292 7.146L7.292 4.083Z"
    )

    /** 달리기(directions_run): 거리·러닝 횟수. */
    val DirectionsRun: ImageVector = filled(
        "DirectionsRun",
        META_GRID,
        "M7.869 3.197C8.511 3.197 9.036 2.672 9.036 2.03C9.036 1.388 8.511 0.863 7.869 0.863" +
            "C7.227 0.863 6.702 1.388 6.702 2.03C6.702 2.672 7.227 3.197 7.869 3.197Z" +
            "M5.769 11.305L6.352 8.738L7.577 9.905L7.577 13.405L8.744 13.405L8.744 9.03L7.519 7.863L7.869 6.113" +
            "C8.627 6.988 9.794 7.572 11.077 7.572L11.077 6.405C9.969 6.405 9.036 5.822 8.569 5.005L7.986 4.072" +
            "C7.752 3.722 7.402 3.488 6.994 3.488C6.819 3.488 6.702 3.547 6.527 3.547L3.494 4.83L3.494 7.572" +
            "L4.661 7.572L4.661 5.588L5.711 5.18L4.777 9.905L1.919 9.322L1.686 10.488L5.769 11.305Z"
    )

    /** 체크(check_circle): 완료·달성. */
    val CheckCircle: ImageVector = filled(
        "CheckCircle",
        META_GRID,
        "M7 1.167C3.78 1.167 1.167 3.78 1.167 7C1.167 10.22 3.78 12.833 7 12.833C10.22 12.833 12.833 10.22 12.833 7" +
            "C12.833 3.78 10.22 1.167 7 1.167Z" +
            "M5.833 9.917L2.917 7L3.739 6.178L5.833 8.266L10.261 3.838L11.083 4.667L5.833 9.917Z"
    )

    /** 트로피(emoji_events): 티어·순위. */
    val EmojiEvents: ImageVector = filled(
        "EmojiEvents",
        META_GRID,
        "M11.083 2.917L9.917 2.917L9.917 1.75L4.083 1.75L4.083 2.917L2.917 2.917C2.275 2.917 1.75 3.442 1.75 4.083" +
            "L1.75 4.667C1.75 6.154 2.87 7.367 4.311 7.548C4.678 8.423 5.466 9.082 6.417 9.275L6.417 11.083" +
            "L4.083 11.083L4.083 12.25L9.917 12.25L9.917 11.083L7.583 11.083L7.583 9.275" +
            "C8.534 9.082 9.322 8.423 9.689 7.548" +
            "C11.13 7.367 12.25 6.154 12.25 4.667L12.25 4.083C12.25 3.442 11.725 2.917 11.083 2.917Z" +
            "M2.917 4.667L2.917 4.083L4.083 4.083L4.083 6.312C3.407 6.067 2.917 5.425 2.917 4.667Z" +
            "M11.083 4.667C11.083 5.425 10.593 6.067 9.917 6.312L9.917 4.083L11.083 4.083L11.083 4.667Z"
    )

    /** 상승(trending_up): 레벨·변화. */
    val TrendingUp: ImageVector = filled(
        "TrendingUp",
        META_GRID,
        "M9.333 3.5L10.669 4.836L7.823 7.683L5.489 5.349L1.167 9.678L1.989 10.5L5.489 7L7.823 9.333L11.497 5.664" +
            "L12.833 7L12.833 3.5L9.333 3.5Z"
    )

    /** 사람(person): 나이·성별 등 사람 정보. */
    val Person: ImageVector = filled(
        "Person",
        META_GRID,
        "M7 7C8.289 7 9.333 5.956 9.333 4.667C9.333 3.377 8.289 2.333 7 2.333C5.711 2.333 4.667 3.377 4.667 4.667" +
            "C4.667 5.956 5.711 7 7 7Z" +
            "M7 8.167C5.442 8.167 2.333 8.948 2.333 10.5L2.333 11.667L11.667 11.667L11.667 10.5" +
            "C11.667 8.948 8.557 8.167 7 8.167Z"
    )

    val ChevronRight: ImageVector = line("ChevronRight", "M9 18L15 12L9 6")

    /** 아래·위 꺾쇠(홈 「내 기록 요약 ⌄」·「접기 ⌃」, 피그마 keyboard_arrow_down·up). */
    val ChevronDown: ImageVector = line("ChevronDown", "M6 9L12 15L18 9")
    val ChevronUp: ImageVector = line("ChevronUp", "M18 15L12 9L6 15")
    val ArrowRight: ImageVector = line("ArrowRight", "M5 12H19", "M12 5L19 12L12 19")
    val Bell: ImageVector = line(
        "Bell",
        "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9",
        "M10.3 21a1.94 1.94 0 0 0 3.4 0"
    )

    /** 선물(알림함 이벤트 항목, 피그마 icon/gift). */
    val Gift: ImageVector = line(
        "Gift",
        "M4 8h16a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z",
        "M12 8v13",
        "M19 12v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7",
        "M7.5 8a2.5 2.5 0 0 1 0-5A4.8 8 0 0 1 12 8a4.8 8 0 0 1 4.5-5 2.5 2.5 0 0 1 0 5"
    )

    /** 바깥에서 열기(알림함 공지 행, 피그마 icon/open-in-new 18 격자를 24로 옮김). */
    val OpenInNew: ImageVector = line(
        "OpenInNew",
        "M20 10V4H14",
        "M20 4L11 13",
        "M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"
    )
    val Bolt: ImageVector = line("Bolt", "M13 2L3 14H12L11 22L21 10H12L13 2Z", isFilled = true)
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

    /** 시계(랭킹 「n일 n시간 뒤 마감」, 피그마 schedule). */
    val Clock: ImageVector = line("Clock", circle(12f, 12f, 9f), "M12 7V12L15 14")
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

    /** 걷는 사람(하단 기록런 탭, J-0). */
    val Walk: ImageVector = line(
        "Walk",
        circle(13f, 4f, 2f),
        "M7 11l3-3 4 1 3 3",
        "M10 8l-1 6 3 2v6",
        "M9 14l-3 7"
    )

    /** 두 점을 잇는 길(평균 거리, 런바디 요약 G-14). */
    val Route: ImageVector = line(
        "Route",
        circle(6f, 19f, 3f),
        "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15",
        circle(18f, 5f, 3f)
    )

    /** 채운 말풍선(러닝 코멘트, 런바디 요약 G-14). */
    val ChatBubble: ImageVector = line(
        "ChatBubble",
        "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z",
        isFilled = true
    )

    /** 보내기(피그마 G-25 AI 코치 질문 보내기의 Material send, 채움). */
    val Send: ImageVector =
        filled("Send", 24f, "M3 20V4L22 12L3 20ZM5 17L16.85 12L5 7V10.5L11 12L5 13.5V17ZM5 17V12V7V10.5V13.5V17Z")

    /** 해(홈 러닝 설정 버튼, A-4). */
    val Sun: ImageVector = line(
        "Sun",
        circle(12f, 12f, 4f),
        "M12 2v2",
        "M12 20v2",
        "m4.93 4.93 1.41 1.41",
        "m17.66 17.66 1.41 1.41",
        "M2 12h2",
        "M20 12h2",
        "m6.34 17.66-1.41 1.41",
        "m19.07 4.93-1.41 1.41"
    )
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

    /** 일시정지(두 막대, FR-194). */
    val Pause: ImageVector = line("Pause", "M7 5h3v14H7z", "M14 5h3v14h-3z", isFilled = true)

    /** 재개(삼각형, FR-194). */
    val Play: ImageVector = line("Play", "M7 4.5L19 12L7 19.5z", isFilled = true)
    val Calendar: ImageVector = line(
        "Calendar",
        "M5 4H19A2 2 0 0 1 21 6V20A2 2 0 0 1 19 22H5A2 2 0 0 1 3 20V6A2 2 0 0 1 5 4Z",
        "M16 2v4",
        "M8 2v4",
        "M3 10h18"
    )
    val Alert: ImageVector = line("Alert", circle(12f, 12f, 10f), "M12 8v4", "M12 16h.01")

    /** 소리 켜짐(스피커와 소리 물결, 설정의 소리 크기). */
    val Volume: ImageVector = line(
        "Volume",
        "M11 5L6 9H2v6h4l5 4V5z",
        "M15.54 8.46a5 5 0 0 1 0 7.07",
        "M19.07 4.93a10 10 0 0 1 0 14.14"
    )

    /** 소리 꺼짐(스피커와 X). */
    val VolumeOff: ImageVector = line("VolumeOff", "M11 5L6 9H2v6h4l5 4V5z", "M23 9l-6 6", "M17 9l6 6")
    val CloudOff: ImageVector = line(
        "CloudOff",
        "M2 2L22 22",
        "M5.78 5.78A7 7 0 0 0 9 19h8.5a4.5 4.5 0 0 0 1.31-.2",
        "M21.53 16.5A4.5 4.5 0 0 0 17.5 10h-1.79A7 7 0 0 0 9.2 5.2"
    )

    /** 채움 아이콘([viewport] 격자, 크기도 격자와 같다). 색은 tint가 정한다. */
    private fun filled(name: String, viewport: Float, path: String): ImageVector = ImageVector.Builder(
        name = "Runkk.$name",
        defaultWidth = viewport.dp,
        defaultHeight = viewport.dp,
        viewportWidth = viewport,
        viewportHeight = viewport
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()

    /** SVG 원(cx, cy, r)을 path 문자열로. */
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun line(name: String, vararg paths: String, isFilled: Boolean = false): ImageVector {
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
                fill = if (isFilled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }
}

/** 메타 아이콘 격자(피그마 14px). */
private const val META_GRID = 14f
