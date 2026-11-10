package com.a207.runkk.app.ui.crew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.model.Gender
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 크루 기본 이미지(피그마 `RunKK / Avatar / 크루 기본 이미지`): surfaceHigh 바탕 + groups 아이콘(보조 글자색). 크루 로고 사진은 아직
 * 받지 않는다(로고 올리기는 W4 API). [size] 74 → 모서리 24·아이콘 40, 66 → 모서리 24·아이콘 36, 50 → 모서리 15·아이콘 28.
 */
@Composable
internal fun CrewDefaultImage(size: Dp, modifier: Modifier = Modifier) {
    val corner = if (size >= 66.dp) 24.dp else (size.value * 0.3f).dp
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(RunkkColors.SurfaceHigh)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Icon(
            RunkkIcons.Group,
            contentDescription = null,
            tint = RunkkColors.TextSecondary,
            modifier = Modifier.size(size * GROUPS_ICON_RATIO)
        )
    }
}

/** 크루 기본 이미지에서 아이콘이 차지하는 비율(74 → 40). */
private const val GROUPS_ICON_RATIO = 0.54f

/** 크루원 아바타 자리(피그마 F-22 목록 36, 팝업 80): surfaceHigh 원. 팝업은 divider 점선 대신 실선 테두리로 근사한다. */
@Composable
internal fun MemberAvatar(size: Dp, modifier: Modifier = Modifier, isBordered: Boolean = false) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(RunkkColors.SurfaceHigh)
            .then(if (isBordered) Modifier.border(1.dp, RunkkColors.Border, CircleShape) else Modifier)
            .clearAndSetSemantics {}
    )
}

/** 밑줄 글자 링크(피그마 "탈퇴하기"·"수정"·"크루 삭제하기": 13 Regular 보조색 밑줄, 안쪽 좌우 8·위아래 6, 누름 칸 48). */
@Composable
internal fun CrewUnderlineLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = RunkkTextStyles.text(13, lineHeight = 18).copy(textDecoration = TextDecoration.Underline),
            color = RunkkColors.TextSecondary
        )
    }
}

/** 이동 줄(피그마 F-18 "지난 크루런 ›"·"지난 목표 ›"): 높이 52, 아래 divider, 15 Regular, 오른쪽 "›" 18 보조색. */
@Composable
internal fun CrewNavRow(title: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = RunkkTextStyles.text(15),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text("›", style = RunkkTextStyles.text(18), color = RunkkColors.TextSecondary)
        }
        HorizontalDivider(color = RunkkColors.Border, thickness = 1.dp)
    }
}

/** 테두리 카드(피그마 F-18 통계 칸·F-22 내 정보: 흰 바탕, divider 테두리, 모서리 24, 안쪽 14). */
@Composable
internal fun CrewOutlinedCard(
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    background: Color = RunkkColors.Surface,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(14.dp)
    ) { content() }
}

/** 크루장 배지(피그마 F-22: 흰 바탕, divider 테두리, 높이 20, 좌우 8, 모서리 16, 11 Regular). */
@Composable
internal fun LeaderBadge(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .heightIn(min = 20.dp)
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.crew_role_leader),
            style = RunkkTextStyles.text(11),
            color = RunkkColors.TextPrimary
        )
    }
}

/**
 * ⋯ 메뉴(피그마 F-17·F-24): 화면 전체 딤(rgba(23,27,18,0.45)) 위 오른쪽 위 카드(폭 176, 위 62·오른쪽 20, 흰 바탕, 테두리 #C7CCBF,
 * 모서리 16, 그림자, 위아래 안쪽 6). 줄은 "크루 정보", "크루원" 둘이고 사이에 divider. 딤·뒤로가기는 닫는다. 크루장 분기는 없다.
 */
@Composable
internal fun BoxScope.CrewHomeMenu(onInfo: () -> Unit, onMembers: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val menuLabel = stringResource(R.string.crew_home_menu)
    Box(
        Modifier
            .matchParentSize()
            .background(RunkkColors.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
            .clearAndSetSemantics {}
    )
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 62.dp, end = 20.dp)
            .width(176.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Outline, shape)
            .padding(vertical = 6.dp)
            .semantics { paneTitle = menuLabel }
    ) {
        MenuLine(stringResource(R.string.crew_home_menu_info), onInfo)
        HorizontalDivider(color = RunkkColors.Border, thickness = 1.dp)
        MenuLine(stringResource(R.string.crew_home_tab_members), onMembers)
    }
}

@Composable
private fun MenuLine(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = RunkkTextStyles.text(15, lineHeight = 18),
        color = RunkkColors.TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    )
}

/** 정보 한 줄(피그마 F-22b: 왼쪽 이름 13 Regular 보조색, 오른쪽 값 14 Bold). */
@Composable
internal fun InfoPair(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = RunkkTextStyles.text(13), color = RunkkColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = RunkkTextStyles.text(14, FontWeight.Bold), color = RunkkColors.TextPrimary)
    }
}

/** 섹션 제목(피그마 15 Bold) + 개수 13 Regular(예 "크루원 7", "가입 신청 2건"). */
@Composable
internal fun SectionTitle(title: String, count: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = RunkkTextStyles.text(15, FontWeight.Bold, lineHeight = 18),
                color = RunkkColors.TextPrimary
            )
            Text(count, style = RunkkTextStyles.text(13, lineHeight = 18), color = RunkkColors.TextPrimary)
        }
        trailing?.let { Text(it, style = RunkkTextStyles.text(12), color = RunkkColors.TextPrimary) }
    }
}

/** 성별 표시(공개하지 않았으면 "비공개", F-22b). */
@Composable
internal fun genderLabel(gender: Gender?): String = when (gender) {
    Gender.FEMALE -> stringResource(R.string.crew_member_female)
    Gender.MALE -> stringResource(R.string.crew_member_male)
    Gender.UNDISCLOSED, null -> stringResource(R.string.crew_member_private)
}

/** "2026. 10. 01"(피그마 [yyyy. mm. dd]). */
internal fun dottedDate(date: LocalDate): String = DOTTED.format(date)

internal fun dottedDate(at: Instant): String = DOTTED.format(at.atZone(SEOUL))

private val DOTTED: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy. MM. dd")
internal val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

/** 연필(피그마 "Icon - 연필", Material edit 24). */
internal val PencilIcon: ImageVector by lazy {
    ImageVector.Builder("Pencil", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(
            "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34" +
                "c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"
        ).toNodes(),
        fill = SolidColor(Color.White)
    ).build()
}

/** 피그마 F-26 로고 덮개의 검정 50%. */
internal val LOGO_EDIT_SCRIM = Color.Black.copy(alpha = 0.5f)

/** 보조 기술용: 값이 비면 "비공개". */
@Composable
internal fun orPrivate(value: String?): String = value ?: stringResource(R.string.crew_member_private)

/** ⋯ 단추(피그마 `RunKK / Buttons / 아이콘` Medium44): 44 칸(누름 48), more_horiz 24 본문색. */
@Composable
internal fun CrewMoreDots(description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(MoreHorizIcon, contentDescription = null, tint = RunkkColors.TextPrimary, modifier = Modifier.size(24.dp))
    }
}

/** more_horiz(Material 24): 점 셋. */
private val MoreHorizIcon: ImageVector by lazy {
    ImageVector.Builder("MoreHoriz", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(
            "M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z" +
                "m-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"
        ).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()
}
