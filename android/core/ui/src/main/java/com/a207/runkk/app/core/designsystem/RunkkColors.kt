package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 런크크 색 토큰 한 벌(FR-203, NFR-041). 값은 피그마 변수 컬렉션 "Runkk · Theme"(Light 472:0 / Dark 472:1)와
 * "RunKK · Accent"를 따른다(S15P31A207-292, 2026-11-09). RunkkTheme(darkTheme)이 벌을 골라 [LocalRunkkColors]로 내려 주고,
 * 화면은 `RunkkColors.TextPrimary`처럼 지금 벌의 토큰을 읽는다.
 * 본문 글자/배경 짝은 두 벌 모두 명도 대비 4.5:1 이상이다(ColorContrastTest가 짝 목록으로 지킨다).
 * Compose 밖(공유 카드 비트맵 등)에서는 [dark]·[light]의 소문자 속성을 직접 읽는다.
 *
 * 피그마 변수 → 토큰 대응(라이트 / 다크):
 * - color/background → [background] #F7F8F2 / #171B12, color/surface → [surface] #FFFFFF / #292F25
 * - color/surfaceHigh → [surfaceHigh] #E9EDE0 / #343D2D (Runimal 바탕 [runimalTile]도 같은 값)
 * - color/divider → [border] #E9EDE0 / #444B3E
 * - color/textPrimary → [textPrimary] #171B12 / #FFFFFF, color/textSecondary → [textSecondary] #67705B / #A8AEA0
 * - color/primary → [limeFill]·[selectedFill]·[tabSelected] #CAEB2E, color/onPrimary·tabSelected/text·runEnd/icon·
 *   dateSelected/text → [onLimeFill]·[onSelectedFill]·[onTabSelected] #171B12
 * - color/secondary → [violet] #5C4BDB, color/onSecondary → [onSecondary] #FFFFFF
 * - color/accent → [accent]·다크 [lavender] #9B8DEE, color/onAccent → [onAccent] #171B12
 * - color/accentContainer → [accentContainer], color/secondaryContainer → [secondaryContainer]
 * - color/brandDark → [ink] #171B12, color/imageScrim → [imageScrim]
 * - color/tagPurple/bg·text → [tagPurple]·[onTagPurple], color/segmentSelected/bg → [segmentSelected]
 * - color/chatMine/bg·stroke → [chatMine]·[chatMineStroke], color/radio/stroke·selectedOuter·dot → [radioStroke]·
 *   [radioSelectedOuter]·[radioDot]
 * - color/todayMark/bg → [todayMark], color/inputHigh/bg → [inputHigh], color/menuDivider → [menuDivider],
 *   color/toggleOff/bg → [toggleOff], color/lockShortcut/icon → [lockShortcutIcon]
 * - color/respChip/bg·text → [chipSelected]·[onChipSelected], color/dropdown/stroke → [outline]
 * - color/mapFade/0·50·100 → [background]에 알파 0·0.5·1(토큰 없이 `Background.copy(alpha = …)`)
 * - RunKK · Accent: color/barCrew → [barCrew], color/accentMuted → [accentMuted]
 * - 변수가 아닌 피그마 원시값: 대화상자·시트 딤 rgba(23,27,18,0.45) → [scrim], 라임 틴트 태그 #F2F9D0·#3D4A00 → [tagLime]·
 *   [onTagLime](두 벌 같음), 라임 틴트 바탕은 라이트 [limeContainer]에도 쓴다.
 * 피그마에 없는 토큰([backgroundLocked]·[backgroundDecor]·[surfacePressed]·[borderStrong]·[inputBorder]·[borderHover]·[lime]·
 * [limePressed]·[limeDim]·[onLime]·다크 [limeContainer]·[runimalTileBorder])은 피그마 팔레트 값 가운데 대비 규칙(글자 4.5:1,
 * 컨트롤 경계 3:1)을 지키는 값으로 두었다.
 */
@Immutable
class RunkkColors(
    /** 화면 배경(color/background). */
    val background: Color,
    /** 화면 잠금 배경(다크는 OLED 절전 검정). */
    val backgroundLocked: Color,
    /** 배경 위 장식 글자(로그인 "RUN"). 읽는 글자가 아니다. */
    val backgroundDecor: Color,
    /** 카드·버튼 표면(color/surface). */
    val surface: Color,
    /** 눌린 표면(hover 근사). */
    val surfacePressed: Color,
    /** 한 단 높은 표면(color/surfaceHigh): 세그먼트 바탕, 회색 버튼, 남의 말풍선, 크루 기본 이미지 바탕. */
    val surfaceHigh: Color,
    /** 기본 테두리·구분선(color/divider). */
    val border: Color,
    /** 강한 테두리(아이콘 버튼·라디오 등 컨트롤 경계). 라이트 벌은 배경 대비 3:1 이상(WCAG 1.4.11). */
    val borderStrong: Color,
    /** 입력칸·체크박스 경계(WCAG 1.4.11 비텍스트 대비 3:1 이상). */
    val inputBorder: Color,
    /** 눌린 테두리(hover 근사), 비강조 막대. */
    val borderHover: Color,
    /** 드롭다운·입력 외곽선(color/dropdown/stroke). */
    val outline: Color,
    /** 본문 글자(color/textPrimary). */
    val textPrimary: Color,
    /** 보조 글자(color/textSecondary). */
    val textSecondary: Color,
    /** 포인트 색(강조 글자·선·테두리). 다크는 라임, 라이트는 짙은 올리브다. 채움은 [limeFill]을 쓴다. */
    val lime: Color,
    /** 포인트 눌림(hover 근사). */
    val limePressed: Color,
    /** 단계 표시용 어두운 포인트(심박 구간 막대 등, 글자에 쓰지 않는다). */
    val limeDim: Color,
    /** 포인트 위 글자. */
    val onLime: Color,
    /** 선택 칩·강조 배너 배경. */
    val limeContainer: Color,
    /** 라임 채움(color/primary): 주 버튼·고른 칸·켜진 스위치. 두 벌 모두 라임이다. */
    val limeFill: Color,
    /** 라임 채움 눌림. */
    val limeFillPressed: Color,
    /** 라임 채움 위 글자(color/onPrimary). */
    val onLimeFill: Color,
    /** 하나만 고르는 선택지([RunkkSegmentRow])의 고른 칸 배경. 두 벌 모두 라임 채움이다. */
    val selectedFill: Color,
    /** 고른 칸 글자. */
    val onSelectedFill: Color,
    /** 고른 칸 테두리. */
    val selectedBorder: Color,
    /** 탭 전환([RunkkSegmentedTabs])의 고른 탭 배경. 두 벌 모두 라임 채움이다. */
    val tabSelected: Color,
    /** 고른 탭 글자(color/tabSelected/text). */
    val onTabSelected: Color,
    /** Runimal 그림 바탕(연한 둥근 네모, G-4·G-6·G-11). surfaceHigh와 같다(D-496). */
    val runimalTile: Color,
    /** Runimal 그림 바탕 테두리와 16유형 "내 유형" 칸 테두리(G-5). */
    val runimalTileBorder: Color,
    /** 상대·실시간·종료·그만두기 강조. 라이트는 보라(color/secondary), 다크는 color/accent다. */
    val lavender: Color,
    /** 브랜드 보라(color/secondary): 대화상자 확인 버튼, 보라 버튼. */
    val violet: Color,
    /** 보라 위 글자(color/onSecondary). */
    val onSecondary: Color,
    /** 보라 옅은 바탕(color/secondaryContainer). */
    val secondaryContainer: Color,
    /** 연보라 강조(color/accent). */
    val accent: Color,
    /** 연보라 위 글자(color/onAccent). */
    val onAccent: Color,
    /** 연보라 옅은 바탕(color/accentContainer). */
    val accentContainer: Color,
    /** 차분한 연보라(RunKK · Accent color/accentMuted). */
    val accentMuted: Color,
    /** 크루 막대 채움(RunKK · Accent color/barCrew). */
    val barCrew: Color,
    /** 보라 틴트 태그 바탕(color/tagPurple/bg). */
    val tagPurple: Color,
    /** 보라 틴트 태그 글자(color/tagPurple/text). */
    val onTagPurple: Color,
    /** 라임 틴트 태그 바탕(피그마 원시값 #F2F9D0, 두 벌 같음). */
    val tagLime: Color,
    /** 라임 틴트 태그 글자(피그마 원시값 #3D4A00, 두 벌 같음). */
    val onTagLime: Color,
    /** 세그먼트([RunkkSegmented]) 고른 칸(color/segmentSelected/bg). 글자는 [textPrimary]다. */
    val segmentSelected: Color,
    /** 고른 칩([RunkkChoiceChip]) 채움(color/respChip/bg). */
    val chipSelected: Color,
    /** 고른 칩 글자(color/respChip/text). */
    val onChipSelected: Color,
    /** 내 말풍선 바탕(color/chatMine/bg). */
    val chatMine: Color,
    /** 내 말풍선 테두리(color/chatMine/stroke). */
    val chatMineStroke: Color,
    /** 라디오 테두리(color/radio/stroke). */
    val radioStroke: Color,
    /** 고른 라디오 바깥 고리(color/radio/selectedOuter). */
    val radioSelectedOuter: Color,
    /** 고른 라디오 점(color/radio/dot). */
    val radioDot: Color,
    /** 달력 오늘 표시(color/todayMark/bg). */
    val todayMark: Color,
    /** 높은 입력칸 바탕(color/inputHigh/bg). */
    val inputHigh: Color,
    /** 메뉴 구분선(color/menuDivider). */
    val menuDivider: Color,
    /** 꺼진 스위치 트랙(color/toggleOff/bg). */
    val toggleOff: Color,
    /** 잠금 화면 바로가기 아이콘(color/lockShortcut/icon). */
    val lockShortcutIcon: Color,
    /** 대화상자·바텀시트 딤(피그마 rgba(23,27,18,0.45), 두 벌 같음). */
    val scrim: Color,
    /** 사진 위 덮개(color/imageScrim, 라이트는 투명). */
    val imageScrim: Color,
    /** 브랜드 잉크(color/brandDark). */
    val ink: Color,
    /** 어두운 벌인가(시스템 막대 아이콘 색 등). */
    val isDark: Boolean
) {
    companion object {
        /** 피그마 Runkk · Theme Dark. */
        val dark =
            RunkkColors(
                background = Color(0xFF171B12),
                backgroundLocked = Color(0xFF000000),
                backgroundDecor = Color(0xFF292F25),
                surface = Color(0xFF292F25),
                surfacePressed = Color(0xFF343D2D),
                surfaceHigh = Color(0xFF343D2D),
                border = Color(0xFF444B3E),
                borderStrong = Color(0xFF737A6B),
                inputBorder = Color(0xFF737A6B),
                borderHover = Color(0xFF616959),
                outline = Color(0xFF737A6B),
                textPrimary = Color(0xFFFFFFFF),
                textSecondary = Color(0xFFA8AEA0),
                lime = Color(0xFFCAEB2E),
                limePressed = Color(0xFFD6F04F),
                limeDim = Color(0xFFA3BD22),
                onLime = Color(0xFF171B12),
                limeContainer = Color(0xFF262B10),
                limeFill = Color(0xFFCAEB2E),
                limeFillPressed = Color(0xFFD6F04F),
                onLimeFill = Color(0xFF171B12),
                selectedFill = Color(0xFFCAEB2E),
                onSelectedFill = Color(0xFF171B12),
                selectedBorder = Color(0xFFCAEB2E),
                tabSelected = Color(0xFFCAEB2E),
                onTabSelected = Color(0xFF171B12),
                runimalTile = Color(0xFF343D2D),
                runimalTileBorder = Color(0xFF444B3E),
                lavender = Color(0xFF9B8DEE),
                violet = Color(0xFF5C4BDB),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFF302856),
                accent = Color(0xFF9B8DEE),
                onAccent = Color(0xFF171B12),
                accentContainer = Color(0xFF3A3452),
                accentMuted = Color(0xFF9E94D4),
                barCrew = Color(0xFF949C87),
                tagPurple = Color(0xFFCFC4FF),
                onTagPurple = Color(0xFF2B1F6B),
                tagLime = Color(0xFFF2F9D0),
                onTagLime = Color(0xFF3D4A00),
                segmentSelected = Color(0xFF616959),
                chipSelected = Color(0xFFDBE0D4),
                onChipSelected = Color(0xFF171B12),
                chatMine = Color(0x00545C4F),
                chatMineStroke = Color(0xFF737A6B),
                radioStroke = Color(0xFF737A6B),
                radioSelectedOuter = Color(0xFF5C4ADB),
                radioDot = Color(0xFFFFFFFF),
                todayMark = Color(0xFF404A38),
                inputHigh = Color(0xFF40453B),
                menuDivider = Color(0xFF737A6B),
                toggleOff = Color(0xFF525C4A),
                lockShortcutIcon = Color(0xFF171B12),
                scrim = SCRIM,
                imageScrim = Color(0xFF171B12).copy(alpha = 0.58f),
                ink = Color(0xFF171B12),
                isDark = true
            )

        /**
         * 피그마 Runkk · Theme Light. 라임(#CAEB2E)은 흰 바탕 위 글자 대비가 1.4:1이라 글자·선에 쓰는 포인트 색([lime])은 짙은 올리브로 둔다.
         * 채움(주 버튼·고른 칸·켜진 스위치)은 라임에 검은 글자다([limeFill]).
         */
        val light =
            RunkkColors(
                background = Color(0xFFF7F8F2),
                backgroundLocked = Color(0xFFF7F8F2),
                backgroundDecor = Color(0xFFE9EDE0),
                surface = Color(0xFFFFFFFF),
                surfacePressed = Color(0xFFF0F0F2),
                surfaceHigh = Color(0xFFE9EDE0),
                border = Color(0xFFE9EDE0),
                borderStrong = Color(0xFF737A6B),
                inputBorder = Color(0xFF67705B),
                borderHover = Color(0xFFC7CCBF),
                outline = Color(0xFFC7CCBF),
                textPrimary = Color(0xFF171B12),
                textSecondary = Color(0xFF67705B),
                lime = Color(0xFF3F6212),
                limePressed = Color(0xFF4D7C0F),
                limeDim = Color(0xFF5F8A1C),
                onLime = Color(0xFFFFFFFF),
                limeContainer = Color(0xFFF2F9D0),
                limeFill = Color(0xFFCAEB2E),
                limeFillPressed = Color(0xFFB9D624),
                onLimeFill = Color(0xFF171B12),
                selectedFill = Color(0xFFCAEB2E),
                onSelectedFill = Color(0xFF171B12),
                selectedBorder = Color(0xFFCAEB2E),
                tabSelected = Color(0xFFCAEB2E),
                onTabSelected = Color(0xFF171B12),
                runimalTile = Color(0xFFE9EDE0),
                runimalTileBorder = Color(0xFFD3D9C6),
                lavender = Color(0xFF5C4BDB),
                violet = Color(0xFF5C4BDB),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFE3DFF9),
                accent = Color(0xFF9B8DEE),
                onAccent = Color(0xFF171B12),
                accentContainer = Color(0xFFEBE7FC),
                accentMuted = Color(0xFF7569BF),
                barCrew = Color(0xFFBFC6B3),
                tagPurple = Color(0xFFEBE7FC),
                onTagPurple = Color(0xFF5C4BDB),
                tagLime = Color(0xFFF2F9D0),
                onTagLime = Color(0xFF3D4A00),
                segmentSelected = Color(0xFFFFFFFF),
                chipSelected = Color(0xFF67705B),
                onChipSelected = Color(0xFFFFFFFF),
                chatMine = Color(0xFFFFFFFF),
                chatMineStroke = Color(0x00454A3D),
                radioStroke = Color(0xFFE9EDE0),
                radioSelectedOuter = Color(0xFFE9EDE0),
                radioDot = Color(0xFF171B12),
                todayMark = Color(0xFFE9EDE0),
                inputHigh = Color(0xFFE9EDE0),
                menuDivider = Color(0xFFE9EDE0),
                toggleOff = Color(0xFFE9EDE0),
                lockShortcutIcon = Color(0xFFFFFFFF),
                scrim = SCRIM,
                imageScrim = Color(0x00171B12),
                ink = Color(0xFF171B12),
                isDark = false
            )

        // 지금 테마 벌의 토큰(화면 코드가 읽는 이름). RunkkTheme 밖에서 읽으면 다크 벌이다.

        val Background: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.background

        val BackgroundLocked: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.backgroundLocked

        val BackgroundDecor: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.backgroundDecor

        val Surface: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.surface

        val SurfacePressed: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.surfacePressed

        val Border: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.border

        val BorderStrong: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.borderStrong

        val InputBorder: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.inputBorder

        val BorderHover: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.borderHover

        val TextPrimary: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.textPrimary

        val TextSecondary: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.textSecondary

        val Lime: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.lime

        val LimePressed: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.limePressed

        val LimeDim: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.limeDim

        val OnLime: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onLime

        val LimeContainer: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.limeContainer

        val LimeFill: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.limeFill

        val LimeFillPressed: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.limeFillPressed

        val OnLimeFill: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onLimeFill

        val SelectedFill: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.selectedFill

        val OnSelectedFill: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onSelectedFill

        val SelectedBorder: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.selectedBorder

        val TabSelected: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.tabSelected

        val OnTabSelected: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onTabSelected

        val RunimalTile: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.runimalTile

        val RunimalTileBorder: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.runimalTileBorder

        val Lavender: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.lavender

        val Violet: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.violet

        val SurfaceHigh: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.surfaceHigh

        val Outline: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.outline

        val OnSecondary: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onSecondary

        val SecondaryContainer: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.secondaryContainer

        val Accent: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.accent

        val OnAccent: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onAccent

        val AccentContainer: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.accentContainer

        val AccentMuted: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.accentMuted

        val BarCrew: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.barCrew

        val TagPurple: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.tagPurple

        val OnTagPurple: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onTagPurple

        val TagLime: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.tagLime

        val OnTagLime: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onTagLime

        val SegmentSelected: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.segmentSelected

        val ChipSelected: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.chipSelected

        val OnChipSelected: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onChipSelected

        val ChatMine: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.chatMine

        val ChatMineStroke: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.chatMineStroke

        val RadioStroke: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.radioStroke

        val RadioSelectedOuter: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.radioSelectedOuter

        val RadioDot: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.radioDot

        val TodayMark: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.todayMark

        val InputHigh: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.inputHigh

        val MenuDivider: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.menuDivider

        val ToggleOff: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.toggleOff

        val LockShortcutIcon: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.lockShortcutIcon

        val Scrim: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.scrim

        val ImageScrim: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.imageScrim

        val Ink: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.ink
    }
}

/** 대화상자·시트 딤(피그마 rgba(23,27,18,0.45)). */
private val SCRIM = Color(0xFF171B12).copy(alpha = 0.45f)

/** 지금 테마의 색 벌. RunkkTheme이 넣는다. 넣지 않으면 다크 벌이다. */
val LocalRunkkColors = staticCompositionLocalOf { RunkkColors.dark }
