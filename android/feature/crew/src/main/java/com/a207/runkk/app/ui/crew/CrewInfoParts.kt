package com.a207.runkk.app.ui.crew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkMenuRow
import com.a207.runkk.app.core.designsystem.RunkkTagPill
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.model.Gender
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 밑줄 글자 링크(피그마 "탈퇴하기"·"수정"·"크루 삭제하기": 13/18 Regular 보조색 밑줄, 누름 칸 48). */
@Composable
internal fun CrewUnderlineLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RunkkTextLink(
        text,
        onClick = onClick,
        modifier = modifier,
        isUnderlined = true,
        style = RunkkTextStyles.text(13, lineHeight = 18),
        color = RunkkColors.TextSecondary
    )
}

/** 이동 줄(피그마 F-18 "지난 크루런 ›"·"지난 목표 ›"): 높이 52, 좌우 0, 15 Regular, 꺾쇠 18 보조색, 아래 divider. */
@Composable
internal fun CrewNavRow(title: String, onClick: () -> Unit) {
    RunkkMenuRow(
        title,
        onClick = onClick,
        minHeight = 52.dp,
        horizontalPadding = 0.dp,
        titleStyle = RunkkTextStyles.text(15),
        chevronSize = 18.dp,
        hasDivider = true
    )
}

/** 크루장 배지(피그마 F-22: divider 테두리 꼬리표, 11 Regular). */
@Composable
internal fun LeaderBadge(modifier: Modifier = Modifier) {
    RunkkTagPill(stringResource(R.string.crew_role_leader), modifier = modifier)
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
        Text(label, style = RunkkTextStyles.Label, color = RunkkColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
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

/** ⋯ 단추(피그마 `RunKK / Buttons / 아이콘` Medium44): 바탕·테두리 없는 원 단추(누름 48), more_horiz 24 본문색. */
@Composable
internal fun CrewMoreDots(description: String, onClick: () -> Unit) {
    RunkkIconButton(
        icon = RunkkIcons.MoreHoriz,
        contentDescription = description,
        onClick = onClick,
        iconSize = 24.dp,
        borderColor = Color.Transparent,
        containerColor = Color.Transparent
    )
}
